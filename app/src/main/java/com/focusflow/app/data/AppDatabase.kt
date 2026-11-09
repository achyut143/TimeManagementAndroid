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
    entities = [
        TaskEntity::class, SubtaskEntity::class, GoalEntity::class, BacklogFolderEntity::class,
        SkippedOccurrenceEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun goalDao(): GoalDao
    abstract fun backlogFolderDao(): BacklogFolderDao
    abstract fun skippedDao(): SkippedOccurrenceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "focusflow.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

        /** v3: weekly / monthly repeat rules and the repeat number. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatWeekdays` TEXT")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatMonthDay` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatNumber` INTEGER")
                // Number existing repeat tasks by how many days of the same habit came before them.
                db.execSQL(
                    "UPDATE `tasks` SET `repeatNumber` = (SELECT COUNT(DISTINCT t2.`date`) FROM `tasks` t2 " +
                        "WHERE t2.`title` = `tasks`.`title` AND t2.`repeatAgain` IS NOT NULL AND t2.`date` <= `tasks`.`date`) " +
                        "WHERE `repeatAgain` IS NOT NULL AND `date` IS NOT NULL",
                )
            }
        }

        /** v4: repeat series ids (existing series are keyed by title) and skipped occurrences. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `seriesId` TEXT")
                db.execSQL("UPDATE `tasks` SET `seriesId` = `title` WHERE `repeatAgain` IS NOT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_seriesId` ON `tasks` (`seriesId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skipped_occurrences` (`seriesId` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`seriesId`, `date`))",
                )
            }
        }

        /** v5: optional end date for repeat tasks. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeatUntil` INTEGER")
            }
        }
    }
}
