package com.politex

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationV7ToV8Test {
    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME

    private fun createV7Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(dbName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(7) {
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
                                db.execSQL("ALTER TABLE `material_items` ADD COLUMN `subjectName` TEXT NOT NULL DEFAULT ''")
                                db.execSQL("ALTER TABLE `material_items` ADD COLUMN `archived` INTEGER NOT NULL DEFAULT 0")
                                db.execSQL(
                                    "UPDATE `material_items` SET " +
                                        "`subjectName` = COALESCE((SELECT f.`subjectName` FROM `material_folders` f WHERE f.`id` = `material_items`.`folderId`), ''), " +
                                        "`archived` = COALESCE((SELECT f.`isArchive` FROM `material_folders` f WHERE f.`id` = `material_items`.`folderId`), 0)",
                                )
                    }

                            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                        },
                    ).build(),
            )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 7")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('41О', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '', 1234567890)",
            )
            db.execSQL(
                "INSERT INTO material_items " +
                    "(folderId, kind, title, fileName, ext, uriPath, pageCount, lastPage, rotationDeg, sortOrder, textContent, subjectName, archived, createdAt) " +
                    "VALUES (0, 'TEXT', 'Конспект', '', 'txt', '', 0, 0, 0, 0, 'старый текст', 'ТОЭ', 0, 1)",
            )
            db.execSQL("INSERT INTO grade_config (subjectName, priorityJson) VALUES ('ТОЭ', '[\"REGULAR\",\"PRACTICAL\"]')")
        }
    }

    @Test
    fun `данные переживают миграцию v7 в v8 и новые колонки на месте`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV7Database(context)
        resetSingleton()

        val db = com.politex.data.local.MpkDatabase.getInstance(context)

        val tasks = kotlinx.coroutines.runBlocking { db.taskDao().getAllTasks() }
        assertEquals("ДЗ переживает миграцию", 1, tasks.size)
        val config = kotlinx.coroutines.runBlocking { db.gradeDao().getConfig("ТОЭ") }
        assertEquals("priorityJson сохранён", "[\"REGULAR\",\"PRACTICAL\"]", config?.priorityJson)

        assertEquals("roundingMode дефолт", "0.1", config?.roundingMode)
        assertEquals("weightsJson дефолт", "", config?.weightsJson)
        assertEquals("roundUp дефолт", false, config?.roundUp)

        kotlinx.coroutines.runBlocking { db.materialDao().updateTextContent(1, "новый текст") }
        db.openHelper.readableDatabase
            .query("SELECT textEdited, textContent FROM material_items WHERE id = 1")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
                assertEquals("новый текст", c.getString(1))
            }

        kotlinx.coroutines.runBlocking {
            db.materialDao().upsertAnnotation(
                com.politex.data.local.entity.MaterialAnnotationEntity(
                    itemId = 1,
                    pageIndex = 2,
                    strokesJson = "{\"v\":1,\"strokes\":[]}",
                ),
            )
            db.materialDao().upsertAnnotation(
                com.politex.data.local.entity.MaterialAnnotationEntity(
                    itemId = 1,
                    pageIndex = 2,
                    strokesJson = "{\"v\":1,\"strokes\":[{\"c\":1,\"w\":2.0,\"pts\":[0.1,0.1]}]}",
                ),
            )
        }
        val ann = kotlinx.coroutines.runBlocking { db.materialDao().getAnnotation(1, 2) }
        var n = 0
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM material_annotations WHERE itemId=1 AND pageIndex=2")
            .use { c -> c.moveToFirst(); n = c.getInt(0) }
        assertEquals("upsert REPLACE: одна строка на (item,page)", 1, n)
        assertTrue(ann!!.strokesJson.contains("pts"))
        resetSingleton()
    }

    private fun resetSingleton() {
        try {
            val cls = Class.forName("com.politex.data.local.MpkDatabase")
            val field = cls.getDeclaredField("instance")
            field.isAccessible = true
            (field.get(null) as AutoCloseable?)?.close()
            field.set(null, null)
        } catch (_: Exception) {
        }
    }
}
