package com.politex

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.entity.TeacherOverrideEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationV9ToV10Test {
    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME

    private fun createV9Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(dbName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(9) {
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
                                        "`textContent` TEXT NOT NULL, " +
                                        "`subjectName` TEXT NOT NULL DEFAULT '', " +
                                        "`archived` INTEGER NOT NULL DEFAULT 0, " +
                                        "`textEdited` INTEGER NOT NULL DEFAULT 0, " +
                                        "`createdAt` INTEGER NOT NULL)",
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
                                    "CREATE TABLE IF NOT EXISTS `material_annotations` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`itemId` INTEGER NOT NULL, `pageIndex` INTEGER NOT NULL, " +
                                        "`strokesJson` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)",
                                )
                                db.execSQL(
                                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_material_annotations_itemId_pageIndex` " +
                                        "ON `material_annotations` (`itemId`, `pageIndex`)",
                                )
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
                                        "`weightsJson` TEXT NOT NULL DEFAULT '', " +
                                        "`roundingMode` TEXT NOT NULL DEFAULT '0.1', " +
                                        "`roundUp` INTEGER NOT NULL DEFAULT 0, " +
                                        "`weightsEnabled` INTEGER NOT NULL DEFAULT 1, " +
                                        "PRIMARY KEY(`subjectName`))",
                                )
                            }

                            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                        },
                    ).build(),
            )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 9")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('41О', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '', 1234567890)",
            )
            db.execSQL(
                "INSERT INTO grade_config (subjectName, priorityJson, weightsJson, roundingMode, roundUp, weightsEnabled) " +
                    "VALUES ('ТОЭ', '[\"REGULAR\",\"EXAM\"]', '[25,25,50,100]', '.00', 0, 0)",
            )
            db.execSQL(
                "INSERT INTO teacher_lessons (teacherName, surnameKey, dateString, dayOfWeek, lessonNumber, groups, subject, room) " +
                    "VALUES ('Иванов П.П.', 'иванов', '05.10.2026', 1, 3, '41О', 'Математика', '241')",
            )
        }
    }

    private fun migrated() = com.politex.data.local.MpkDatabase.getInstance(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `teacher_overrides создаётся миграцией v9 в v10`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV9Database(context)
        resetSingleton()
        val db = migrated()

        var n = 0
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='teacher_overrides'")
            .use { c ->
                c.moveToFirst()
                n = c.getInt(0)
            }
        assertEquals("таблица teacher_overrides создана", 1, n)

        runBlocking {
            db.teacherOverrideDao().upsert(
                TeacherOverrideEntity(dateString = "05.10.2026", lessonNumber = 3, kind = TeacherOverrideEntity.KIND_OTHER_ROOM, room = "305"),
            )
        }
        val rows = runBlocking { db.teacherOverrideDao().forDate("05.10.2026") }
        assertEquals("правка пишется", 1, rows.size)
        assertEquals("305", rows[0].room)
        assertEquals(TeacherOverrideEntity.KIND_OTHER_ROOM, rows[0].kind)
        resetSingleton()
    }

    @Test
    fun `unique-индекс dateString+lessonNumber на месте и не пускает дубль`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV9Database(context)
        resetSingleton()
        val db = migrated()

        var name = ""
        var sql = ""
        db.openHelper.readableDatabase
            .query("SELECT name, sql FROM sqlite_master WHERE type='index' AND tbl_name='teacher_overrides' AND sql IS NOT NULL")
            .use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    sql = c.getString(1).orEmpty()
                }
            }
        assertEquals("index_teacher_overrides_dateString_lessonNumber", name)
        assertTrue("индекс UNIQUE: $sql", sql.uppercase().contains("UNIQUE"))
        assertTrue("индекс по (dateString, lessonNumber): $sql", sql.contains("`dateString`") && sql.contains("`lessonNumber`"))

        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO teacher_overrides (dateString, lessonNumber, kind, room, note, createdAt) " +
                "VALUES ('05.10.2026', 3, 'NO_LESSON', '', '', 1)",
        )
        var failed = false
        try {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO teacher_overrides (dateString, lessonNumber, kind, room, note, createdAt) " +
                    "VALUES ('05.10.2026', 3, 'SOMEONE_ELSE', '', '', 2)",
            )
        } catch (e: Exception) {
            failed = true
        }
        assertTrue("дубль (dateString, lessonNumber) отклонён", failed)
        resetSingleton()
    }

    @Test
    fun `upsert по той же паре заменяет правку, deleteForLesson удаляет ровно её`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV9Database(context)
        resetSingleton()
        val dao = migrated().teacherOverrideDao()

        runBlocking {
            dao.upsert(TeacherOverrideEntity(dateString = "05.10.2026", lessonNumber = 3, kind = TeacherOverrideEntity.KIND_OTHER_ROOM, room = "305"))
            dao.upsert(TeacherOverrideEntity(dateString = "05.10.2026", lessonNumber = 3, kind = TeacherOverrideEntity.KIND_NO_LESSON))
            dao.upsert(TeacherOverrideEntity(dateString = "05.10.2026", lessonNumber = 4, kind = TeacherOverrideEntity.KIND_SOMEONE_ELSE))
        }
        val day = runBlocking { dao.forDate("05.10.2026") }
        assertEquals("одна строка на пару", 2, day.size)
        val third = day.first { it.lessonNumber == 3 }
        assertEquals("правку заменили, а не удвоили", TeacherOverrideEntity.KIND_NO_LESSON, third.kind)

        runBlocking { dao.deleteForLesson("05.10.2026", 3) }
        val left = runBlocking { dao.forDate("05.10.2026") }
        assertEquals(listOf(4), left.map { it.lessonNumber })
        resetSingleton()
    }

    @Test
    fun `старые данные переживают миграцию v9 в v10`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV9Database(context)
        resetSingleton()
        val db = migrated()

        val tasks = runBlocking { db.taskDao().getAllTasks() }
        assertEquals("ДЗ студента не стёрто fallback'ом", 1, tasks.size)
        assertEquals("Лаба по ТОЭ", tasks[0].title)

        val config = runBlocking { db.gradeDao().getConfig("ТОЭ") }
        assertEquals("[25,25,50,100]", config?.weightsJson)
        assertFalse("weightsEnabled=0 сохранён", config?.weightsEnabled ?: true)

        var n = 0
        db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM teacher_lessons WHERE dateString='05.10.2026'")
            .use { c ->
                c.moveToFirst()
                n = c.getInt(0)
            }
        assertEquals("кэш расписания преподавателей на месте", 1, n)
        resetSingleton()
    }

    private fun resetSingleton() {
        try {
            val cls = Class.forName("com.politex.data.local.MpkDatabase")
            val field = cls.getDeclaredField("instance")
            field.isAccessible = true

            try {
                (field.get(null) as AutoCloseable?)?.close()
            } catch (_: Exception) {
            }
            field.set(null, null)
        } catch (_: Exception) {
        }
    }
}
