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

    @Query("SELECT * FROM tasks WHERE goalId = :goalId")
    fun observeForGoal(goalId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query(
        """SELECT COUNT(*) FROM tasks
           WHERE date = :day AND title = :title AND startTime = :start AND endTime = :end""",
    )
    suspend fun countOccurrence(title: String, day: LocalDate, start: String, end: String): Int

    @Query("SELECT * FROM tasks WHERE seriesId IS NOT NULL AND date IS NOT NULL AND date <= :day")
    suspend fun seriesTasksUpTo(day: LocalDate): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE seriesId = :seriesId AND date = :day")
    suspend fun countInSeries(seriesId: String, day: LocalDate): Int

    /** Later copies in a series that haven't been touched yet (not marked, no notes or time spent). */
    @Query(
        """SELECT * FROM tasks WHERE seriesId = :seriesId AND date > :after
           AND completed = 0 AND notCompleted = 0 AND notes IS NULL AND timeSpent IS NULL""",
    )
    suspend fun untouchedInSeriesAfter(seriesId: String, after: LocalDate): List<TaskEntity>

    /** Series of the repeat tasks named [title] (a habit / routine in the Habits screen). */
    @Query("SELECT DISTINCT seriesId FROM tasks WHERE title = :title AND repeatAgain IS NOT NULL AND seriesId IS NOT NULL")
    suspend fun seriesIdsForTitle(title: String): List<String>

    @Query("SELECT * FROM tasks WHERE seriesId IN (:seriesIds)")
    suspend fun tasksInSeries(seriesIds: List<String>): List<TaskEntity>

    @Query("UPDATE tasks SET repeatUntil = :until WHERE seriesId IN (:seriesIds)")
    suspend fun endSeries(seriesIds: List<String>, until: LocalDate)

    @Delete
    suspend fun deleteAll(tasks: List<TaskEntity>)

    /** Gives the later copies of a series the same repeat settings. */
    @Query(
        """UPDATE tasks SET repeatAgain = :repeatAgain, repeatWeekdays = :weekdays, repeatMonthDay = :monthDay,
           repeatUntil = :until WHERE seriesId = :seriesId AND date > :after""",
    )
    suspend fun updateRepeatAfter(
        seriesId: String, after: LocalDate, repeatAgain: Int?, weekdays: String?, monthDay: Int?, until: LocalDate?,
    )

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

    @Query("SELECT * FROM goals WHERE id = :id")
    fun observeById(id: String): Flow<GoalEntity?>

    // Upsert, not REPLACE: REPLACE would delete the goal first and unlink its tasks.
    @Upsert
    suspend fun upsert(goal: GoalEntity)

    @Delete
    suspend fun delete(goal: GoalEntity)
}

@Dao
interface SkippedOccurrenceDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(skip: SkippedOccurrenceEntity)

    @Query("SELECT seriesId FROM skipped_occurrences WHERE date = :day")
    suspend fun seriesSkippedOn(day: LocalDate): List<String>

    @Query("DELETE FROM skipped_occurrences WHERE seriesId IN (:seriesIds)")
    suspend fun deleteForSeries(seriesIds: List<String>)

    @Query("SELECT COUNT(*) FROM skipped_occurrences WHERE seriesId = :seriesId AND date = :day")
    suspend fun isSkipped(seriesId: String, day: LocalDate): Int
}
