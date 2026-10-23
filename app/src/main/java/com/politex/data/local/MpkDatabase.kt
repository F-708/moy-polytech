package com.politex.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.politex.data.local.dao.GradeDao
import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.dao.TaskDao
import com.politex.data.local.dao.TaskReminderDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.dao.TeacherOverrideDao
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialFolderEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.local.entity.TeacherOverrideEntity

@Database(
    entities = [
        LessonEntity::class,
        StudentTaskEntity::class,
        TeacherLessonEntity::class,
        TaskReminderEntity::class,
        @Suppress("DEPRECATION")
        MaterialFolderEntity::
        class,
        MaterialItemEntity::class,
        MaterialBookmarkEntity::class,
        MaterialAnnotationEntity::class,
        GradeEntryEntity::class,
        GradeConfigEntity::class,
        TeacherOverrideEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class MpkDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao

    abstract fun taskDao(): TaskDao

    abstract fun teacherLessonDao(): TeacherLessonDao

    abstract fun taskReminderDao(): TaskReminderDao

    abstract fun materialDao(): MaterialDao

    abstract fun gradeDao(): GradeDao

    abstract fun teacherOverrideDao(): TeacherOverrideDao

    companion object {

        internal const val DATABASE_NAME = "mpk_schedule.db"

        private val migration1To2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `teacher_lessons` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `teacherName` TEXT NOT NULL,
                            `surnameKey` TEXT NOT NULL,
                            `dateString` TEXT NOT NULL,
                            `dayOfWeek` INTEGER NOT NULL,
                            `lessonNumber` INTEGER NOT NULL,
                            `groups` TEXT NOT NULL,
                            `subject` TEXT NOT NULL,
                            `room` TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_teacher_lessons_surnameKey_dateString` " +
                            "ON `teacher_lessons` (`surnameKey`, `dateString`)",
                    )
                }
            }

        private val migration2To3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dayOfWeek` ON `lessons` (`groupName`, `dayOfWeek`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dateString` ON `lessons` (`groupName`, `dateString`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_dateString` ON `lessons` (`dateString`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomFirst` ON `lessons` (`roomFirst`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomSecond` ON `lessons` (`roomSecond`)")
                }
            }

        private val migration3To4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `task_reminders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `taskId` INTEGER NOT NULL,
                            `triggerAtMillis` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` " +
                            "ON `task_reminders` (`taskId`)",
                    )
                }
            }

        private val migration4To5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `material_folders` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `subjectName` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `isArchive` INTEGER NOT NULL,
                            `sortOrder` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `material_items` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `folderId` INTEGER NOT NULL,
                            `kind` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `ext` TEXT NOT NULL,
                            `uriPath` TEXT NOT NULL,
                            `pageCount` INTEGER NOT NULL,
                            `lastPage` INTEGER NOT NULL,
                            `rotationDeg` INTEGER NOT NULL,
                            `sortOrder` INTEGER NOT NULL,
                            `textContent` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_material_items_folderId` " +
                            "ON `material_items` (`folderId`)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `material_bookmarks` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `itemId` INTEGER NOT NULL,
                            `page` INTEGER NOT NULL,
                            `label` TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_material_bookmarks_itemId` " +
                            "ON `material_bookmarks` (`itemId`)",
                    )
                }
            }

        private val migration5To6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `grade_entries` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `subjectName` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `value` INTEGER NOT NULL,
                            `label` TEXT NOT NULL,
                            `dateIso` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `grade_config` (
                            `subjectName` TEXT NOT NULL,
                            `priorityJson` TEXT NOT NULL,
                            PRIMARY KEY(`subjectName`)
                        )
                        """.trimIndent(),
                    )
                }
            }

        private val migration6To7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `subjectName` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")

                    db.execSQL(
                        "UPDATE `material_items` SET " +
                            "`subjectName` = COALESCE((SELECT f.`subjectName` FROM `material_folders` f WHERE f.`id` = `material_items`.`folderId`), ''), " +
                            "`archived` = COALESCE((SELECT f.`isArchive` FROM `material_folders` f WHERE f.`id` = `material_items`.`folderId`), 0)",
                    )
                }
            }

        @Volatile
        private var instance: MpkDatabase? = null

        private val migration7To8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `material_annotations` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `itemId` INTEGER NOT NULL,
                            `pageIndex` INTEGER NOT NULL,
                            `strokesJson` TEXT NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_annotations_itemId_pageIndex` " +
                            "ON `material_annotations` (`itemId`, `pageIndex`)",
                    )
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `textEdited` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `weightsJson` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `roundingMode` TEXT NOT NULL DEFAULT '0.1'")
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `roundUp` INTEGER NOT NULL DEFAULT 0")
                }
            }

        private val migration8To9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `grade_config` ADD COLUMN `weightsEnabled` INTEGER NOT NULL DEFAULT 1")
                }
            }

        private val migration9To10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `teacher_overrides` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`dateString` TEXT NOT NULL, `lessonNumber` INTEGER NOT NULL, " +
                            "`kind` TEXT NOT NULL, `room` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_teacher_overrides_dateString_lessonNumber` " +
                            "ON `teacher_overrides` (`dateString`, `lessonNumber`)",
                    )
                }
            }

        private val migration10To11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `material_items` ADD COLUMN `contentHash` TEXT NOT NULL DEFAULT ''")
                }
            }

        fun getInstance(context: Context): MpkDatabase = instance ?: synchronized(this) {
            instance ?: run {
                val built =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            MpkDatabase::class.java,
                            DATABASE_NAME,
                        ).addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8, migration8To9, migration9To10, migration10To11)
                        .fallbackToDestructiveMigration()
                        .build()
                instance = built
                built
            }
        }
    }
}
