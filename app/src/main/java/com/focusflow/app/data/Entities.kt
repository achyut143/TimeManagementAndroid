package com.focusflow.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * Android version of the iOS `Task` SwiftData model.
 *
 * Times are stored as "H:mm" strings, the same as iOS, so the rules ported
 * from Swift (repeat, roll-over, points) behave the same way. A task with
 * no [date] is in the Backlog. A task with empty start/end times is "untimed".
 * [tags] is the iOS `taskDescription` field, which the UI uses as
 * comma-separated tags.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("date"), Index("title"), Index("goalId"), Index("backlogFolderId"), Index("seriesId")],
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class TaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val tags: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val completed: Boolean = false,
    val notCompleted: Boolean = false,
    /** iOS "red repeat icon": the next occurrence is already handled, so completing this one won't create another. */
    val reassign: Boolean = false,
    val weight: Double = 1.0,
    val notes: String? = null,
    val date: LocalDate? = null,
    /** Repeat every N days. Non-null means this task is a habit (also set for weekly / monthly rules). */
    val repeatAgain: Int? = null,
    /** Weekly repeat: ISO weekdays "1,3" (Mon = 1). Takes priority over [repeatAgain]. */
    val repeatWeekdays: String? = null,
    /** Monthly repeat on this day of the month (1–31). */
    val repeatMonthDay: Int? = null,
    /** Which occurrence this is (1 = first). A roll-over copy keeps the same number. */
    val repeatNumber: Int? = null,
    /** Shared by every copy of one repeat task, so the series survives a rename. */
    val seriesId: String? = null,
    /** Last day a repeat may land on (inclusive). Null = repeats forever. */
    val repeatUntil: LocalDate? = null,
    val priority: String = "P3",
    /** Minutes actually spent. */
    val timeSpent: Double? = null,
    /** Minutes allocated, for untimed tasks. */
    val elapsedTime: Double? = null,
    val copySubtasks: Boolean = false,
    val goalId: String? = null,
    val info: String? = null,
    /**
     * The Backlog folder it's filed under. Kept when the task is scheduled, so
     * sending it back to the backlog puts it in the same place (as on iOS).
     * No foreign key: deleting a folder moves its tasks up a level instead.
     */
    val backlogFolderId: String? = null,
)

@Entity(
    tableName = "subtasks",
    indices = [Index("taskId"), Index("parentId")],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SubtaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SubtaskEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    /** Set for nested subtasks. */
    val parentId: String? = null,
    val name: String,
    val notes: String? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)

/** A folder for backlog tasks. Folders can sit inside other folders ([parentId]). */
@Entity(tableName = "backlog_folders")
data class BacklogFolderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Null = top level. */
    val parentId: String? = null,
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val goalDescription: String = "",
)

/**
 * A repeat occurrence the user removed (deleted, moved to another day or sent to
 * the Backlog), so opening that day doesn't create it again.
 */
@Entity(tableName = "skipped_occurrences", primaryKeys = ["seriesId", "date"])
data class SkippedOccurrenceEntity(
    val seriesId: String,
    val date: LocalDate,
)
