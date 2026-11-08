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
    indices = [Index("date"), Index("title"), Index("goalId"), Index("backlogFolderId")],
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
    /** Repeat every N days. Non-null means this task is a habit. */
    val repeatAgain: Int? = null,
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
