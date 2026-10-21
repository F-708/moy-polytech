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
class TaskUndoRoundTripTest {
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

    @Test
    fun `delete then undo restores the same row`() = runBlocking {
        val id =
            repo.addTask(
                StudentTaskEntity(
                    groupName = "41О",
                    course = 4,
                    subjectName = "ТОЭ",
                    taskType = "Лабораторная работа",
                    title = "Лаба 2",
                    deadlineDate = "2026-10-05",
                ),
            )
        val saved = requireNotNull(repo.getTaskById(id))

        repo.deleteTask(saved)
        assertTrue("после удаления список пуст", db.taskDao().getAllTasks().isEmpty())

        repo.addTask(saved.copy())

        val all = db.taskDao().getAllTasks()
        assertEquals("восстановилась ровно одна строка", 1, all.size)
        assertEquals(id, all.first().id)
        assertEquals("Лаба 2", all.first().title)
    }
}
