package com.politex

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
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
class TaskReminderCascadeTest {
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

    private suspend fun seedTaskWithReminders(): Pair<Long, List<TaskReminderEntity>> {
        val id =
            repo.addTask(
                StudentTaskEntity(
                    groupName = "41О",
                    course = 4,
                    subjectName = "ТОЭ",
                    taskType = "Домашнее задание",
                    title = "Параграф 12",
                ),
            )
        val reminders =
            listOf(
                TaskReminderEntity(taskId = id, triggerAtMillis = 1_800_000_000_000L),
                TaskReminderEntity(taskId = id, triggerAtMillis = 1_800_000_060_000L),
            )
        db.taskReminderDao().replaceForTask(id, reminders)
        return id to db.taskReminderDao().getForTask(id)
    }

    @Test
    fun `delete убирает и задание и напоминания`() = runBlocking {
        val (id, _) = seedTaskWithReminders()

        repo.deleteTask(requireNotNull(repo.getTaskById(id)))

        assertTrue("задание удалено", db.taskDao().getAllTasks().isEmpty())
        assertTrue("напоминания удалены каскадом", db.taskReminderDao().getForTask(id).isEmpty())
    }

    @Test
    fun `undo возвращает задание и снимок напоминаний`() = runBlocking {
        val (id, _) = seedTaskWithReminders()
        val saved = requireNotNull(repo.getTaskById(id))

        val snapshot = db.taskReminderDao().getForTask(id)
        assertEquals(2, snapshot.size)

        repo.deleteTask(saved)
        assertTrue(db.taskReminderDao().getForTask(id).isEmpty())

        repo.addTask(saved.copy())
        db.taskReminderDao().insertAll(snapshot)

        val restoredTask = db.taskDao().getTaskById(id)
        assertEquals("задание восстановилось с тем же id", id, restoredTask?.id)
        assertEquals("Параграф 12", restoredTask?.title)
        val restoredReminders = db.taskReminderDao().getForTask(id)
        assertEquals(2, restoredReminders.size)
        assertEquals(snapshot.map { it.triggerAtMillis }, restoredReminders.map { it.triggerAtMillis })
    }

    @Test
    fun `deleteTaskById тоже каскадный`() = runBlocking {
        val (id, _) = seedTaskWithReminders()

        repo.deleteTaskById(id)

        assertTrue(db.taskDao().getAllTasks().isEmpty())
        assertTrue(db.taskReminderDao().getForTask(id).isEmpty())
    }
}
