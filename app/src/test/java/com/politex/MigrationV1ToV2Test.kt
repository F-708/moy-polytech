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
class MigrationV1ToV2Test {

    private val dbName = com.politex.data.local.MpkDatabase.DATABASE_NAME

    private fun createV1Database(context: Context) {
        context.deleteDatabase(dbName)
        val helper =
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration
                    .builder(context)
                    .name(dbName)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
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
                                db.execSQL(
                                    "CREATE TABLE IF NOT EXISTS `student_tasks` (" +
                                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                        "`groupName` TEXT NOT NULL, `course` INTEGER NOT NULL, " +
                                        "`subjectName` TEXT NOT NULL, `taskType` TEXT NOT NULL, " +
                                        "`title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                                        "`isCompleted` INTEGER NOT NULL, `deadlineDate` TEXT NOT NULL, " +
                                        "`createdAt` INTEGER NOT NULL)",
                                )
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        },
                    ).build(),
            )
        helper.writableDatabase.use { db ->
            db.execSQL("PRAGMA user_version = 1")
            db.execSQL(
                "INSERT INTO student_tasks " +
                    "(groupName, course, subjectName, taskType, title, description, isCompleted, deadlineDate, createdAt) " +
                    "VALUES ('41О', 4, 'ТОЭ', 'Домашнее задание', 'Лаба по ТОЭ', '', 0, '', 1234567890)",
            )
        }
    }

    @Test
    fun `задание пользователя переживает миграцию v1 в v2`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        createV1Database(context)

        resetSingleton()

        val db =
            com.politex.data.local.MpkDatabase
                .getInstance(context)

        val tasks = kotlinx.coroutines.runBlocking { db.taskDao().getAllTasks() }
        assertEquals("задание должно пережить миграцию", 1, tasks.size)
        assertEquals("Лаба по ТОЭ", tasks.first().title)
        assertEquals("41О", tasks.first().groupName)

        val teacherRows =
            kotlinx.coroutines.runBlocking {
                db.teacherLessonDao().getForSurnameOnDate("ламкина", "16.09.2026")
            }
        assertTrue("новая таблица должна существовать и быть пустой", teacherRows.isEmpty())

        db.close()
        resetSingleton()
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
