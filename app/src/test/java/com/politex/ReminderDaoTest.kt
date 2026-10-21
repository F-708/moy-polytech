package com.politex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReminderDaoTest {
    private lateinit var db: MpkDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertTask(title: String = "Лаба"): Long = db.taskDao().insertTask(
        StudentTaskEntity(
            groupName = "41О",
            course = 4,
            subjectName = "ТОЭ",
            taskType = "Домашнее задание",
            title = title,
        ),
    )

    @Test
    fun `getDueUntil(now) returns past and boundary but not future`() = runBlocking {
        val dao = db.taskReminderDao()
        val taskId = insertTask()
        val now = System.currentTimeMillis()

        val past = now - 60_000L
        val boundary = now
        val future = now + 10 * 60_000L

        dao.insertAll(
            listOf(
                TaskReminderEntity(taskId = taskId, triggerAtMillis = past),
                TaskReminderEntity(taskId = taskId, triggerAtMillis = boundary),
                TaskReminderEntity(taskId = taskId, triggerAtMillis = future),
            ),
        )

        val due = dao.getDueUntil(now)
        assertEquals("прошлое + граница = 2, будущее не входит", 2, due.size)
        assertTrue("граница включительно", due.any { it.triggerAtMillis == boundary })
        assertTrue("будущее не возвращается (QA#8)", due.none { it.triggerAtMillis == future })
    }

    @Test
    fun `getForTask isolates reminders of one task`() = runBlocking {
        val dao = db.taskReminderDao()
        val a = insertTask("А")
        val b = insertTask("Б")
        val now = System.currentTimeMillis()
        dao.insertAll(
            listOf(
                TaskReminderEntity(taskId = a, triggerAtMillis = now + 1000),
                TaskReminderEntity(taskId = b, triggerAtMillis = now + 2000),
            ),
        )
        assertEquals(1, dao.getForTask(a).size)
        assertEquals(1, dao.getForTask(b).size)
    }

    @Test
    fun `replaceForTask swaps atomically and deleteById makes it one-shot`() = runBlocking {
        val dao = db.taskReminderDao()
        val taskId = insertTask()
        val now = System.currentTimeMillis()

        dao.replaceForTask(
            taskId,
            listOf(
                TaskReminderEntity(taskId = taskId, triggerAtMillis = now + 1000),
                TaskReminderEntity(taskId = taskId, triggerAtMillis = now + 2000),
            ),
        )
        assertEquals("две строки после первой замены", 2, dao.getForTask(taskId).size)

        dao.replaceForTask(taskId, listOf(TaskReminderEntity(taskId = taskId, triggerAtMillis = now + 5000)))
        val after = dao.getForTask(taskId)
        assertEquals("одна строка после второй замены", 1, after.size)
        assertEquals(now + 5000, after.first().triggerAtMillis)

        dao.deleteById(after.first().id)
        assertTrue("после отправки напоминаний нет", dao.getForTask(taskId).isEmpty())
    }
}
