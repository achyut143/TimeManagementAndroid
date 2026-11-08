package com.focusflow.app.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.withTransaction
import com.focusflow.app.domain.FolderTree
import com.focusflow.app.domain.QuickTaskParser
import com.focusflow.app.domain.TaskTime
import com.focusflow.app.domain.isRepeating
import com.focusflow.app.domain.isUntimed
import com.focusflow.app.notify.ReminderScheduler
import com.focusflow.app.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * All task changes go through here so the iOS rules are applied in one place:
 * - Completing (or missing) a repeat task creates its next occurrence
 *   `repeatAgain` days later, unless `reassign` (red repeat icon) is set.
 * - Marking a task not completed rolls a copy over to tomorrow (except
 *   daily repeat tasks, whose next copy already lands tomorrow).
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // region Tasks

    suspend fun save(task: TaskEntity) {
        tasks.upsert(task)
        changed(task)
    }

    suspend fun delete(task: TaskEntity) {
        tasks.delete(task)
        reminders.cancel(task.id)
        refreshWidget()
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
                if (task.repeatAgain == null || task.repeatAgain > 1) rollOverToTomorrow(task)
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
                if (task.repeatAgain == null || task.repeatAgain > 1) rollOverToTomorrow(task)
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

    /** Clears date and time so the task drops into the Backlog. */
    suspend fun sendToBacklog(task: TaskEntity) {
        val updated = task.copy(date = null, startTime = "", endTime = "")
        tasks.update(updated)
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
        val days = task.repeatAgain ?: return
        val current = task.date ?: return
        copyTo(task, current.plusDays(days.toLong()), reassign = false)
    }

    private suspend fun rollOverToTomorrow(task: TaskEntity) {
        val current = task.date ?: return
        copyTo(task, current.plusDays(1), reassign = true)
    }

    private suspend fun copyTo(task: TaskEntity, day: LocalDate, reassign: Boolean) {
        if (tasks.countOccurrence(task.title, day, task.startTime, task.endTime) > 0) return
        val copy = TaskEntity(
            title = task.title,
            tags = task.tags,
            startTime = task.startTime,
            endTime = task.endTime,
            reassign = reassign,
            weight = task.weight,
            date = day,
            repeatAgain = task.repeatAgain,
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
