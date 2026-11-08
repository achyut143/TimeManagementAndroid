package com.focusflow.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}

@Database(
    entities = [TaskEntity::class, SubtaskEntity::class, GoalEntity::class, BacklogFolderEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun goalDao(): GoalDao
    abstract fun backlogFolderDao(): BacklogFolderDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focusflow.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: backlog folders. Existing tasks start out Unfiled. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `backlog_folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `parentId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `backlogFolderId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_backlogFolderId` ON `tasks` (`backlogFolderId`)")
            }
        }
    }
}
