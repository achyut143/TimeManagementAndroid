package com.focusflow.app.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.focusflow.app.domain.FolderTree
import com.focusflow.app.domain.QuickTaskParser
import com.focusflow.app.domain.RepeatPlanner
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.isDone
import com.focusflow.app.domain.isRepeating
import com.focusflow.app.domain.isUntimed
import com.focusflow.app.domain.repeatRule
import com.focusflow.app.notify.ReminderScheduler
import com.focusflow.app.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * All task changes go through here so the iOS rules are applied in one place:
 * - Completing (or missing) a repeat task creates its next occurrence on the
 *   next day its [repeatRule] allows (every N days, chosen weekdays, or a day
 *   of the month), unless `reassign` (red repeat icon) is set.
 * - Marking a task not completed rolls a copy over to tomorrow (except
 *   repeat tasks whose next copy already lands tomorrow).
 * - Opening a day (today or later) creates the repeat tasks that belong on it
 *   ([ensureRepeatsFor]), so they show up before the earlier one is marked.
 *   Occurrences the user deleted, moved or sent to the Backlog aren't brought back.
 * - Turning repeat off removes the later copies that haven't been touched yet.
 * - A series never gets two copies on the same day.
 * - Reminders and the home-screen widget are kept in sync.
 */
class TaskRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val reminders: ReminderScheduler,
) {
    private val tasks = db.taskDao()
    private val subtasks = db.subtaskDao()
    private val goals = db.goalDao()
    private val folders = db.backlogFolderDao()
    private val skipped = db.skippedDao()
    private val planLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // region Tasks

    suspend fun save(task: TaskEntity) {
        var toSave = task
        if (toSave.isRepeating) {
            toSave = toSave.copy(seriesId = toSave.seriesId ?: toSave.id, repeatNumber = toSave.repeatNumber ?: 1)
        }
        val old = tasks.getById(task.id)
        db.withTransaction {
            tasks.upsert(toSave)
            if (old != null) {
                // Moved off its day: don't let that day recreate it.
                if (old.date != null && old.date != toSave.date) skip(old)
                // Repeat turned off: drop the later copies that were already made.
                if (old.isRepeating && !toSave.isRepeating) removeUntouchedLaterCopies(toSave.seriesId, toSave.date ?: old.date)
                // Repeat rule or end date changed: apply it to the rest of the series. Untouched
                // later copies are removed so opening those days recreates them on the new schedule.
                if (old.isRepeating && toSave.isRepeating && repeatSettingsChanged(old, toSave)) {
                    val from = toSave.date ?: old.date
                    val series = toSave.seriesId
                    if (from != null && series != null) {
                        removeUntouchedLaterCopies(series, from)
                        tasks.updateRepeatAfter(
                            series, from, toSave.repeatAgain, toSave.repeatWeekdays, toSave.repeatMonthDay, toSave.repeatUntil,
                        )
                    }
                }
            }
        }
        changed(toSave)
    }

    suspend fun delete(task: TaskEntity) {
        db.withTransaction {
            tasks.delete(task)
            skip(task)
        }
        reminders.cancel(task.id)
        refreshWidget()
    }

    /**
     * Stops the routine (all repeat tasks named [title]): upcoming copies that aren't
     * marked yet are deleted and no new ones are created. Past days stay, so the
     * history and points are kept.
     */
    suspend fun stopRoutine(title: String, today: LocalDate = LocalDate.now()) {
        val removed = db.withTransaction {
            val series = tasks.seriesIdsForTitle(title)
            if (series.isEmpty()) return@withTransaction emptyList()
            val all = tasks.tasksInSeries(series)
            val upcoming = all.filter { it.date != null && !it.date.isBefore(today) && !it.isDone }
            tasks.deleteAll(upcoming)
            val lastKept = (all - upcoming.toSet()).mapNotNull { it.date }.maxOrNull()
            val until = listOfNotNull(lastKept, today.minusDays(1)).max()
            tasks.endSeries(series, until)
            upcoming
        }
        removed.forEach { reminders.cancel(it.id) }
        refreshWidget()
    }

    /** Deletes the routine completely: every copy, past and future, with its subtasks. */
    suspend fun deleteRoutine(title: String) {
        val removed = db.withTransaction {
            val series = tasks.seriesIdsForTitle(title)
            if (series.isEmpty()) return@withTransaction emptyList()
            val all = tasks.tasksInSeries(series)
            tasks.deleteAll(all)
            skipped.deleteForSeries(series)
            all
        }
        removed.forEach { reminders.cancel(it.id) }
        refreshWidget()
    }

    /**
     * The "trigger" run whenever a day is opened: creates every repeat task whose
     * rule lands on [day]. Past days are left as they were.
     */
    suspend fun ensureRepeatsFor(day: LocalDate, today: LocalDate = LocalDate.now()) {
        if (day.isBefore(today)) return
        val created = planLock.withLock {
            db.withTransaction {
                val skippedHere = skipped.seriesSkippedOn(day).toSet()
                tasks.seriesTasksUpTo(day).groupBy { it.seriesId!! }
                    .filterKeys { it !in skippedHere }
                    .mapNotNull { (_, series) -> RepeatPlanner.plan(day, series) }
                    .count { copyTo(it.template, day, reassign = false, repeatNumber = it.repeatNumber) }
            }
        }
        if (created > 0) refreshWidget()
    }

    suspend fun toggleCompleted(task: TaskEntity) {
        val updated = task.copy(completed = !task.completed)
        db.withTransaction {
            tasks.update(updated)
            if (updated.completed && !task.reassign) createNextOccurrence(task)
        }
        changed(updated)
    }

    suspend fun toggleNotCompleted(task: TaskEntity) {
        val updated = task.copy(notCompleted = !task.notCompleted)
        db.withTransaction {
            tasks.update(updated)
            if (updated.notCompleted) {
                if (!task.reassign) createNextOccurrence(task)
                if (!nextRepeatIsTomorrow(task)) rollOverToTomorrow(task)
            }
        }
        changed(updated)
    }

    /** "Mark all not completed" for every pending task on [day]. */
    suspend fun markAllNotCompleted(day: LocalDate) {
        val pending = tasks.tasksOn(day).filter { !it.completed && !it.notCompleted }
        db.withTransaction {
            for (task in pending) {
                tasks.update(task.copy(notCompleted = true))
                if (!task.reassign) createNextOccurrence(task)
                if (!nextRepeatIsTomorrow(task)) rollOverToTomorrow(task)
            }
        }
        pending.forEach { reminders.cancel(it.id) }
        refreshWidget()
    }

    /**
     * Moves a timed task later by [minutes]. As on iOS, a repeat task first gets
     * its next occurrence at the original time and is marked red so it won't
     * create a second one.
     */
    suspend fun snooze(task: TaskEntity, minutes: Int) {
        if (task.isUntimed) return
        var moved = task.copy(
            startTime = TaskTime.fromMinutes(TaskTime.toMinutes(task.startTime) + minutes),
            endTime = TaskTime.fromMinutes(TaskTime.toMinutes(task.endTime) + minutes),
        )
        db.withTransaction {
            if (task.isRepeating && !task.reassign) {
                createNextOccurrence(task)
                moved = moved.copy(reassign = true)
            }
            tasks.update(moved)
        }
        changed(moved)
    }

    /** Moves a timed task to start at [startMinute] (minutes after midnight), keeping its length. */
    suspend fun moveTo(task: TaskEntity, startMinute: Int) {
        if (task.isUntimed) return
        val length = TaskTime.span(task.startTime, task.endTime)
        save(task.copy(startTime = TaskTime.fromMinutes(startMinute), endTime = TaskTime.fromMinutes(startMinute + length)))
    }

    /** Clears date and time so the task drops into the Backlog. */
    suspend fun sendToBacklog(task: TaskEntity) {
        val updated = task.copy(date = null, startTime = "", endTime = "")
        db.withTransaction {
            tasks.update(updated)
            skip(task)
        }
        reminders.cancel(task.id)
        refreshWidget()
    }

    suspend fun scheduleFromBacklog(task: TaskEntity, day: LocalDate) {
        save(task.copy(date = day))
    }

    /** Quick-add bar. Returns how many tasks were added. */
    suspend fun quickAdd(input: String, day: LocalDate): Int {
        val parsed = QuickTaskParser.parseMultiple(input)
        for (p in parsed) {
            val timed = !p.isUntimed && TaskTime.isValid(p.startTime) && TaskTime.isValid(p.endTime)
            save(
                TaskEntity(
                    title = p.title,
                    startTime = if (timed) TaskTime.fromMinutes(TaskTime.toMinutes(p.startTime)) else "",
                    endTime = if (timed) TaskTime.fromMinutes(TaskTime.toMinutes(p.endTime)) else "",
                    weight = p.weight,
                    date = day,
                    repeatAgain = p.repeatDays?.takeIf { it > 0 },
                    repeatNumber = p.repeatDays?.takeIf { it > 0 }?.let { 1 },
                ),
            )
        }
        return parsed.size
    }

    // endregion

    // region Backlog folders (port of iOS BacklogView actions)

    /** Adds an unscheduled task, filed in [folderId] (null = Unfiled). */
    suspend fun addBacklogTask(title: String, folderId: String?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        tasks.insert(TaskEntity(title = trimmed, weight = 1.0, date = null, priority = "P3", backlogFolderId = folderId))
    }

    suspend fun moveTaskToFolder(task: TaskEntity, folderId: String?) =
        tasks.update(task.copy(backlogFolderId = folderId))

    suspend fun createFolder(name: String, parentId: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val next = (folders.all().maxOfOrNull { it.sortOrder } ?: 0) + 1
        folders.upsert(BacklogFolderEntity(name = trimmed, sortOrder = next, parentId = parentId))
    }

    suspend fun renameFolder(folder: BacklogFolderEntity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) folders.upsert(folder.copy(name = trimmed))
    }

    /** Moves a folder (with everything inside) into [parentId], or to the top level. Never into itself. */
    suspend fun moveFolder(folder: BacklogFolderEntity, parentId: String?) {
        if (parentId != null && (parentId == folder.id || parentId in FolderTree.descendants(folder.id, folders.all()))) return
        folders.upsert(folder.copy(parentId = parentId))
    }

    /** Deletes only the folder: its tasks and sub-folders move up one level. Nothing else is deleted. */
    suspend fun deleteFolder(folder: BacklogFolderEntity) {
        db.withTransaction {
            tasks.refileTasks(folder.id, folder.parentId)
            folders.reparentChildren(folder.id, folder.parentId)
            folders.delete(folder)
        }
    }

    // endregion

    // region Subtasks

    suspend fun addSubtask(taskId: String, name: String, parentId: String? = null) {
        val siblings = subtasks.forTask(taskId).filter { it.parentId == parentId }
        val next = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1
        subtasks.insert(SubtaskEntity(taskId = taskId, parentId = parentId, name = name.trim(), sortOrder = next))
    }

    suspend fun toggleSubtask(subtask: SubtaskEntity) =
        subtasks.update(subtask.copy(completed = !subtask.completed))

    suspend fun deleteSubtask(subtask: SubtaskEntity) = subtasks.delete(subtask)

    /** Moves a subtask one place up (-1) or down (+1) among its siblings. */
    suspend fun moveSubtask(subtask: SubtaskEntity, step: Int) {
        val siblings = subtasks.forTask(subtask.taskId).filter { it.parentId == subtask.parentId }.toMutableList()
        val from = siblings.indexOfFirst { it.id == subtask.id }
        if (from < 0) return
        val to = (from + step).coerceIn(0, siblings.lastIndex)
        siblings.add(to, siblings.removeAt(from))
        subtasks.updateAll(siblings.mapIndexed { i, s -> s.copy(sortOrder = i) })
    }

    // endregion

    // region Goals

    suspend fun saveGoal(goal: GoalEntity) = goals.upsert(goal)

    suspend fun deleteGoal(goal: GoalEntity) = goals.delete(goal)

    // endregion

    // region Rules ported from iOS (TaskRepeater / createIncompleteTask)

    private suspend fun createNextOccurrence(task: TaskEntity) {
        val rule = task.repeatRule ?: return
        val current = task.date ?: return
        val next = rule.nextAfter(current)
        if (task.repeatUntil != null && next.isAfter(task.repeatUntil)) return // series has ended
        // The user already removed that one: leave it gone.
        if (task.seriesId != null && skipped.isSkipped(task.seriesId, next) > 0) return
        copyTo(task, next, reassign = false, repeatNumber = (task.repeatNumber ?: 1) + 1)
    }

    private fun repeatSettingsChanged(a: TaskEntity, b: TaskEntity) =
        a.repeatAgain != b.repeatAgain || a.repeatWeekdays != b.repeatWeekdays ||
            a.repeatMonthDay != b.repeatMonthDay || a.repeatUntil != b.repeatUntil

    private suspend fun skip(task: TaskEntity) {
        val series = task.seriesId ?: return
        val day = task.date ?: return
        skipped.insert(SkippedOccurrenceEntity(series, day))
    }

    private suspend fun removeUntouchedLaterCopies(seriesId: String?, after: LocalDate?) {
        if (seriesId == null || after == null) return
        for (copy in tasks.untouchedInSeriesAfter(seriesId, after)) {
            tasks.delete(copy)
            reminders.cancel(copy.id)
        }
    }

    private fun nextRepeatIsTomorrow(task: TaskEntity): Boolean {
        val current = task.date ?: return false
        return task.repeatRule?.nextAfter(current) == current.plusDays(1)
    }

    private suspend fun rollOverToTomorrow(task: TaskEntity) {
        val current = task.date ?: return
        copyTo(task, current.plusDays(1), reassign = true, repeatNumber = task.repeatNumber)
    }

    /** Returns false if that day already has this task (a series is checked by id, so an edited time still counts). */
    private suspend fun copyTo(task: TaskEntity, day: LocalDate, reassign: Boolean, repeatNumber: Int?): Boolean {
        val exists = if (task.seriesId != null) {
            tasks.countInSeries(task.seriesId, day) > 0
        } else {
            tasks.countOccurrence(task.title, day, task.startTime, task.endTime) > 0
        }
        if (exists) return false
        val copy = TaskEntity(
            title = task.title,
            tags = task.tags,
            startTime = task.startTime,
            endTime = task.endTime,
            reassign = reassign,
            weight = task.weight,
            date = day,
            repeatAgain = task.repeatAgain,
            repeatWeekdays = task.repeatWeekdays,
            repeatMonthDay = task.repeatMonthDay,
            repeatNumber = if (task.repeatAgain != null) repeatNumber else null,
            seriesId = task.seriesId,
            repeatUntil = task.repeatUntil,
            priority = task.priority,
            elapsedTime = task.elapsedTime,
            copySubtasks = task.copySubtasks,
            goalId = task.goalId,
            info = task.info,
            // timeSpent, notes and completion are not copied, as on iOS.
        )
        tasks.insert(copy)
        if (task.copySubtasks) copySubtasks(task.id, copy.id)
        reminders.schedule(copy)
        return true
    }

    /** Copies every subtask (nested ones too), unchecked, keeping the order. */
    private suspend fun copySubtasks(fromTaskId: String, toTaskId: String) {
        val source = subtasks.forTask(fromTaskId)
        val newIds = mutableMapOf<String, String>()
        // Parents first, so nested subtasks can point at their new parent.
        val ordered = mutableListOf<SubtaskEntity>()
        fun addChildren(parentId: String?) {
            source.filter { it.parentId == parentId }.forEach { ordered += it; addChildren(it.id) }
        }
        addChildren(null)
        for (s in ordered) {
            val copy = SubtaskEntity(
                taskId = toTaskId,
                parentId = s.parentId?.let { newIds[it] },
                name = s.name,
                notes = s.notes,
                sortOrder = s.sortOrder,
            )
            newIds[s.id] = copy.id
            subtasks.insert(copy)
        }
    }

    // endregion

    private fun changed(task: TaskEntity) {
        reminders.schedule(task)
        refreshWidget()
    }

    private fun refreshWidget() {
        scope.launch { runCatching { TodayWidget().updateAll(context) } }
    }
}
