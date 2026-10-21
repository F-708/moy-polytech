package com.politex

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationV6ToV7Test {

    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME
    private val group = "41О"

    private fun createV6Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
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
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_reminders_taskId` ON `task_reminders` (`taskId`)")
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
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `grade_entries` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`subjectName` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                                "`value` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                                "`dateIso` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                        )
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `grade_config` (" +
                                "`subjectName` TEXT NOT NULL, `priorityJson` TEXT NOT NULL, " +
                                "PRIMARY KEY(`subjectName`))",
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 6")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('$group', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '2026-10-05', 1234567890)",
            )
            db.execSQL("INSERT INTO material_folders (subjectName, name, isArchive, sortOrder, createdAt) VALUES ('ТОЭ', 'Лекции', 0, 0, 1)")
            db.execSQL("INSERT INTO material_folders (subjectName, name, isArchive, sortOrder, createdAt) VALUES ('ТОЭ', 'Лабораторные', 0, 1, 2)")
            db.execSQL("INSERT INTO material_folders (subjectName, name, isArchive, sortOrder, createdAt) VALUES ('Архив', 'Архив', 1, 2147483647, 3)")
            db.execSQL(
                "INSERT INTO material_items (folderId, kind, title, fileName, ext, uriPath, pageCount, lastPage, rotationDeg, sortOrder, textContent, createdAt) " +
                    "VALUES (1, 'TEXT', 'Конспект', '', 'txt', '', 0, 0, 0, 0, 'текст конспекта', 100)",
            )
            db.execSQL(
                "INSERT INTO material_items (folderId, kind, title, fileName, ext, uriPath, pageCount, lastPage, rotationDeg, sortOrder, textContent, createdAt) " +
                    "VALUES (2, 'FILE', 'Лаба 1', 'lab1.pdf', 'pdf', '/files/materials/2.pdf', 12, 3, 0, 0, '', 101)",
            )
            db.execSQL(
                "INSERT INTO material_items (folderId, kind, title, fileName, ext, uriPath, pageCount, lastPage, rotationDeg, sortOrder, textContent, createdAt) " +
                    "VALUES (3, 'FILE', 'Старое', 'old.pdf', 'pdf', '/files/materials/3.pdf', 5, 0, 0, 0, '', 102)",
            )
        }
    }

    @Test
    fun `данные и схема переживают миграцию v6 в v7`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV6Database(context)

        resetSingleton()

        val db = com.politex.data.local.MpkDatabase.getInstance(context)

        val tasks = runBlocking { db.taskDao().getAllTasks() }
        assertEquals("ДЗ должно пережить миграцию", 1, tasks.size)

        val dao = db.materialDao()
        val conspect = runBlocking { dao.getItemById(1) }
        assertNotNull(conspect)
        assertEquals("предмет папки-владельца перенесён", "ТОЭ", conspect?.subjectName)
        assertEquals("обычная папка не архив", false, conspect?.archived)
        assertEquals("текст TEXT-материала пережил миграцию", "текст конспекта", conspect?.textContent)

        val lab = runBlocking { dao.getItemById(2) }
        assertEquals("вторая папка того же предмета", "ТОЭ", lab?.subjectName)
        assertFalse(lab?.archived ?: true)

        val old = runBlocking { dao.getItemById(3) }
        assertEquals("материал из папки-архива помечен archived", true, old?.archived)

        val tables = queryNames(db, "SELECT name FROM sqlite_master WHERE type='table'")
        assertTrue("таблица material_folders не дропнута", tables.contains("material_folders"))

        val tox = runBlocking { dao.getForSubject("ТОЭ", false).first() }
        assertEquals(2, tox.size)
        val archive = runBlocking { dao.getForSubject("Архив", true).first() }
        assertEquals(1, archive.size)

        runBlocking { dao.setArchived(1, true, dao.nextArchiveSortOrder()) }
        val toxAfter = runBlocking { dao.getForSubject("ТОЭ", false).first() }
        assertEquals("архив скрыл материал из предмета", 1, toxAfter.size)

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
