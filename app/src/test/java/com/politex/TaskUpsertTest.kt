package com.politex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.repository.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskUpsertTest {
    private lateinit var db: MpkDatabase
    private lateinit var repo: TaskRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repo = TaskRepository(db.taskDao(), db.taskReminderDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(
        id: Long = 0,
        title: String,
    ) = StudentTaskEntity(
        id = id,
        groupName = "41О",
        course = 4,
        subjectName = "ТОЭ",
        taskType = "Домашнее задание",
        title = title,
        deadlineDate = "2026-10-05",
    )

    @Test
    fun `add then edit title keeps a single row with new title`() = runBlocking {

        val newId = repo.saveTask(task(title = "Лаба 1"))
        assertTrue("новая запись должна получить id > 0", newId > 0)

        val sameId = repo.saveTask(task(id = newId, title = "Лаба 1 (исправлено)"))
        assertEquals("id должен сохраниться", newId, sameId)

        val all = db.taskDao().getAllTasks()
        assertEquals("в списке одна запись, дубля нет", 1, all.size)
        assertEquals("Лаба 1 (исправлено)", all.first().title)
        assertEquals(newId, all.first().id)
    }

    @Test
    fun `second new task does not replace the first`() = runBlocking {
        repo.saveTask(task(title = "Лаба 1"))
        repo.saveTask(task(title = "Лаба 2"))
        assertEquals("два новых задания — две строки", 2, db.taskDao().getAllTasks().size)
    }
}
