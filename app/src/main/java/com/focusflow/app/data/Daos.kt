package com.focusflow.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date = :day")
    fun observeOn(day: LocalDate): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date = :day")
    suspend fun tasksOn(day: LocalDate): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date >= :from")
    suspend fun tasksFrom(from: LocalDate): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE repeatAgain IS NOT NULL")
    fun observeRepeating(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date IS NULL ORDER BY title")
    fun observeBacklog(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET backlogFolderId = :newFolder WHERE backlogFolderId = :oldFolder")
    suspend fun refileTasks(oldFolder: String, newFolder: String?)

    @Query("SELECT * FROM tasks WHERE goalId IS NOT NULL")
    fun observeWithGoal(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query(
        """SELECT COUNT(*) FROM tasks
           WHERE date = :day AND title = :title AND startTime = :start AND endTime = :end""",
    )
    suspend fun countOccurrence(title: String, day: LocalDate, start: String, end: String): Int

    @Insert
    suspend fun insert(task: TaskEntity)

    // Upsert updates in place. REPLACE would delete the row first, which
    // cascades to its subtasks.
    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

@Dao
interface SubtaskDao {
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder, createdAt")
    fun observeForTask(taskId: String): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder, createdAt")
    suspend fun forTask(taskId: String): List<SubtaskEntity>

    @Insert
    suspend fun insert(subtask: SubtaskEntity)

    @Update
    suspend fun update(subtask: SubtaskEntity)

    @Update
    suspend fun updateAll(subtasks: List<SubtaskEntity>)

    @Delete
    suspend fun delete(subtask: SubtaskEntity)
}

@Dao
interface BacklogFolderDao {
    @Query("SELECT * FROM backlog_folders ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<BacklogFolderEntity>>

    @Query("SELECT * FROM backlog_folders")
    suspend fun all(): List<BacklogFolderEntity>

    @Upsert
    suspend fun upsert(folder: BacklogFolderEntity)

    @Delete
    suspend fun delete(folder: BacklogFolderEntity)

    @Query("UPDATE backlog_folders SET parentId = :newParent WHERE parentId = :oldParent")
    suspend fun reparentChildren(oldParent: String, newParent: String?)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<GoalEntity>>

    // Upsert, not REPLACE: REPLACE would delete the goal first and unlink its tasks.
    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)
}
