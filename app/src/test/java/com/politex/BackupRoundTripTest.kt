package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.util.BackupManager
import com.politex.util.BellCountdownNotifier
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db get() = MpkDatabase.getInstance(context)

    @Test
    fun `export then import restores grades and reminders`() = runBlocking {
        val unique = System.nanoTime().toString()
        val taskTitle = "RT-тест $unique"
        val gradeSubject = "Предмет $unique"
        val triggerAt = System.currentTimeMillis() + 3_600_000L

        val taskId = db.taskDao().insertTask(
            StudentTaskEntity(
                groupName = "41О",
                course = 4,
                subjectName = "ТОЭ",
                taskType = "Домашнее задание",
                title = taskTitle,
                deadlineDate = "2026-10-10",
            ),
        )
        db.taskReminderDao().insertAll(
            listOf(TaskReminderEntity(taskId = taskId, triggerAtMillis = triggerAt)),
        )
        val gradeId = db.gradeDao().insert(
            GradeEntryEntity(
                subjectName = gradeSubject,
                category = "REGULAR",
                value = 8,
                label = "Практ. №3",
                dateIso = "2026-10-01",
            ),
        )

        val json = BackupManager.buildBackup(context)

        db.taskDao().deleteTaskById(taskId)
        db.taskReminderDao().deleteForTask(taskId)
        db.gradeDao().deleteById(gradeId)

        val result = BackupManager.restoreBackup(context, json).getOrThrow()
        assertTrue("задание восстановлено", result.tasksRestored >= 1)
        assertTrue("оценка восстановлена", result.gradesRestored >= 1)
        assertTrue("напоминание восстановлено", result.remindersRestored >= 1)

        val newTask = db.taskDao().getAllTasks().first { it.title == taskTitle }
        assertEquals(
            "напоминание висит на НОВОМ id задания",
            listOf(triggerAt),
            db.taskReminderDao().getForTask(newTask.id).map { it.triggerAtMillis },
        )
        val grade = db.gradeDao().allEntries().first().first { it.subjectName == gradeSubject }
        assertEquals(8, grade.value)
        assertEquals("Практ. №3", grade.label)
        assertEquals("2026-10-01", grade.dateIso)
        assertEquals("REGULAR", grade.category)

        val again = BackupManager.restoreBackup(context, json).getOrThrow()
        assertEquals("оценки дедуплицируются", 0, again.gradesRestored)
        assertEquals(
            "задание не задвоилось",
            1,
            db.taskDao().getAllTasks().count { it.title == taskTitle },
        )
    }

    @Test
    fun `restore returns restored group for live ViewModel update`() = runBlocking {
        val json = """{"version":2,"app":"Мой Политех","settings":{"group":"99RT-TEST"},"tasks":[]}"""
        val result = BackupManager.restoreBackup(context, json).getOrThrow()
        assertEquals("99RT-TEST", result.groupRestored)
        assertEquals("99RT-TEST", WidgetUpdateHelper.getSelectedGroup(context))
    }

    @Test
    fun `СРЕД8 - стиль и тема уведомления восстанавливаются симметрично экспорту`() = runBlocking {
        val json = """{"version":2,"settings":{"notificationsStyle":"progress","notificationsTheme":"app"},"tasks":[]}"""
        BackupManager.restoreBackup(context, json).getOrThrow()
        assertEquals(BellCountdownNotifier.Style.WITH_PROGRESS, BellCountdownNotifier.getStyle(context))
        assertEquals(BellCountdownNotifier.Theme.APP, BellCountdownNotifier.getTheme(context))
    }

    @Test
    fun `СРЕД8 - удалённые темы старых бэкапов мягко падают в системную`() = runBlocking {
        listOf("dark", "sky", "brand").forEach { oldId ->
            val json = """{"version":2,"settings":{"notificationsTheme":"$oldId"},"tasks":[]}"""
            BackupManager.restoreBackup(context, json).getOrThrow()
            assertEquals(
                "тема '$oldId' удалена — ожидается фолбэк в системную",
                BellCountdownNotifier.Theme.SYSTEM,
                BellCountdownNotifier.getTheme(context),
            )
        }
    }

    @Test
    fun `СРЕД8 - round trip экспорт-импорт сохраняет стиль и тему`() = runBlocking {
        BellCountdownNotifier.setStyle(context, BellCountdownNotifier.Style.WITH_SECONDS)
        BellCountdownNotifier.setTheme(context, BellCountdownNotifier.Theme.APP)

        val json = BackupManager.buildBackup(context)

        BellCountdownNotifier.setStyle(context, BellCountdownNotifier.Style.COMPACT)
        BellCountdownNotifier.setTheme(context, BellCountdownNotifier.Theme.SYSTEM)

        BackupManager.restoreBackup(context, json).getOrThrow()
        assertEquals(BellCountdownNotifier.Style.WITH_SECONDS, BellCountdownNotifier.getStyle(context))
        assertEquals(BellCountdownNotifier.Theme.APP, BellCountdownNotifier.getTheme(context))
    }
}
