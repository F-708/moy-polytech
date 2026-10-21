package com.politex

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.entity.GradeEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationV5ToV6Test {

    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME
    private val group = "41О"

    private fun createV5Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `lessons` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`groupName` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                                "`lessonNumber` INTEGER NOT NULL, `timeStart` TEXT NOT NULL, " +
                                "`timeEnd` TEXT NOT NULL, `subjectRaw` TEXT NOT NULL, " +
                                "`roomFirst` TEXT NOT NULL, `teacherFirst` TEXT NOT NULL, " +
                                "`roomSecond` TEXT NOT NULL, `teacherSecond` TEXT NOT NULL, " +
                                "`isSplit` INTEGER NOT NULL, `dateString` TEXT NOT NULL)",
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dayOfWeek` ON `lessons` (`groupName`, `dayOfWeek`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_groupName_dateString` ON `lessons` (`groupName`, `dateString`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_dateString` ON `lessons` (`dateString`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomFirst` ON `lessons` (`roomFirst`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lessons_roomSecond` ON `lessons` (`roomSecond`)")
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `student_tasks` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`groupName` TEXT NOT NULL, `course` INTEGER NOT NULL, " +
                                "`subjectName` TEXT NOT NULL, `taskType` TEXT NOT NULL, " +
                                "`title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                                "`isCompleted` INTEGER NOT NULL, `deadlineDate` TEXT NOT NULL, " +
                                "`createdAt` INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `teacher_lessons` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`teacherName` TEXT NOT NULL, `surnameKey` TEXT NOT NULL, " +
                                "`dateString` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, " +
                                "`lessonNumber` INTEGER NOT NULL, `groups` TEXT NOT NULL, " +
                                "`subject` TEXT NOT NULL, `room` TEXT NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_teacher_lessons_surnameKey_dateString` " +
                                "ON `teacher_lessons` (`surnameKey`, `dateString`)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `task_reminders` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`taskId` INTEGER NOT NULL, `triggerAtMillis` INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` " +
                                "ON `task_reminders` (`taskId`)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `material_folders` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`subjectName` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                                "`isArchive` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                                "`createdAt` INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `material_items` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`folderId` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                                "`title` TEXT NOT NULL, `fileName` TEXT NOT NULL, " +
                                "`ext` TEXT NOT NULL, `uriPath` TEXT NOT NULL, " +
                                "`pageCount` INTEGER NOT NULL, `lastPage` INTEGER NOT NULL, " +
                                "`rotationDeg` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                                "`textContent` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_items_folderId` ON `material_items` (`folderId`)")
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `material_bookmarks` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`itemId` INTEGER NOT NULL, `page` INTEGER NOT NULL, " +
                                "`label` TEXT NOT NULL)",
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_material_bookmarks_itemId` ON `material_bookmarks` (`itemId`)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 5")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('$group', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '2026-10-05', 1234567890)",
            )
            db.execSQL(
                "INSERT INTO material_folders (subjectName, name, isArchive, sortOrder, createdAt) " +
                    "VALUES ('ТОЭ', 'Лекции', 0, 0, 1234567890)",
            )
        }
    }

    @Test
    fun `данные и схема переживают миграцию v5 в v6`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV5Database(context)

        resetSingleton()

        val db = com.politex.data.local.MpkDatabase.getInstance(context)

        val tasks = runBlocking { db.taskDao().getAllTasks() }
        assertEquals("ДЗ должно пережить миграцию", 1, tasks.size)
        assertEquals("Лаба по ТОЭ", tasks.first().title)

        val folders = runBlocking { db.materialDao().getAllFolders().first() }
        assertEquals("папка материалов должна пережить миграцию", 1, folders.size)
        assertEquals("Лекции", folders.first().name)

        val tables = queryNames(db, "SELECT name FROM sqlite_master WHERE type='table'")
        listOf("grade_entries", "grade_config").forEach { expected ->
            assertTrue("таблица $expected должна существовать, есть: $tables", tables.contains(expected))
        }

        val gradeDao = db.gradeDao()
        runBlocking {
            gradeDao.insert(
                GradeEntryEntity(
                    subjectName = "ТОЭ",
                    category = "REGULAR",
                    value = 8,
                    label = "Практ. №1",
                    dateIso = "2026-10-01",
                ),
            )
        }
        val entries = runBlocking { gradeDao.entriesForSubject("ТОЭ").first() }
        assertEquals("оценка должна читаться из мигрированной базы", 1, entries.size)
        assertEquals(8, entries.first().value)
        assertEquals("Практ. №1", entries.first().label)

        db.close()
        resetSingleton()
    }

    private fun queryNames(db: com.politex.data.local.MpkDatabase, sql: String): Set<String> {
        val out = mutableSetOf<String>()
        db.openHelper.readableDatabase.query(sql).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    private fun resetSingleton() {
        try {
            val cls = Class.forName("com.politex.data.local.MpkDatabase")
            val field = cls.getDeclaredField("instance")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {

        }
    }
}
