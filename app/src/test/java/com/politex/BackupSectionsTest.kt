package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.util.AppThemeStore
import com.politex.util.BackupManager
import com.politex.util.BellCountdownNotifier
import com.politex.util.SubjectRenameStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupSectionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db get() = MpkDatabase.getInstance(context)

    @Test
    fun `JJ14 секционный экспорт включает только выбранные секции`() = runBlocking {
        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.GRADES))
        val root = JSONObject(json)
        assertTrue(root.has("grades"))
        assertFalse(root.has("tasks"))
        assertFalse(root.has("lessons"))
        assertFalse(root.has("subjectRenames"))
        assertFalse(root.has("settings"))
    }

    @Test
    fun `JJ14 полный экспорт содержит все секции`() = runBlocking {
        SubjectRenameStore.setCustomName(context, "Физика", "Физ")
        val json = BackupManager.buildBackup(context)
        val root = JSONObject(json)
        assertTrue(root.has("settings"))
        assertTrue(root.has("tasks"))
        assertTrue(root.has("grades"))
        assertTrue(root.has("lessons"))
        assertTrue(root.has("subjectRenames"))
    }

    @Test
    fun `JJ14 частичный бэкап при импорте не затирает остальные данные`() = runBlocking {
        val unique = System.nanoTime().toString(36)
        val gradeSubject = "Предмет $unique"
        val renameOriginal = "Химия $unique"
        db.gradeDao().insert(
            GradeEntryEntity(
                subjectName = gradeSubject,
                category = "REGULAR",
                value = 9,
                label = "КР",
                dateIso = "2026-10-05",
            ),
        )
        SubjectRenameStore.setCustomName(context, renameOriginal, "Хим")

        val taskTitle = "Задание $unique"
        db.taskDao().insertTask(
            StudentTaskEntity(
                groupName = "41О",
                course = 4,
                subjectName = "ТОЭ",
                taskType = "Домашнее задание",
                title = taskTitle,
                deadlineDate = "2026-10-11",
            ),
        )
        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.TASKS))
        val taskId = db.taskDao().getAllTasks().first { it.title == taskTitle }.id
        db.taskDao().deleteTaskById(taskId)

        val result = BackupManager.restoreBackup(context, json).getOrThrow()
        assertTrue("задание восстановлено", result.tasksRestored >= 1)
        assertTrue(db.taskDao().getAllTasks().any { it.title == taskTitle })
        assertTrue(
            "оценка на месте",
            db.gradeDao().allEntries().first().any { it.subjectName == gradeSubject },
        )
        assertEquals("переименование на месте", "Хим", SubjectRenameStore.customName(context, renameOriginal))
    }

    @Test
    fun `JJ14 история расписания round-trip и повторный импорт без дублей`() = runBlocking {
        val unique = System.nanoTime().toString(36)
        val group = "ТТ$unique"
        val lesson = LessonEntity(
            groupName = group,
            dayOfWeek = 1,
            lessonNumber = 1,
            timeStart = "08:30",
            timeEnd = "09:55",
            subjectRaw = "Математика $unique",
            roomFirst = "101",
            teacherFirst = "Иванов",
            dateString = "12.10.2026",
        )
        db.lessonDao().insertLessons(listOf(lesson))
        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.SCHEDULE_HISTORY))
        db.lessonDao().clearLessonsForGroup(group)
        assertEquals(0, db.lessonDao().getLessonCountForGroup(group))

        val first = BackupManager.restoreBackup(context, json).getOrThrow()
        assertEquals(1, first.lessonsRestored)
        val restored = db.lessonDao().getAllLessonsForGroup(group).first()
        assertEquals(1, restored.size)
        assertEquals("08:30", restored[0].timeStart)
        assertEquals("Математика $unique", restored[0].subjectRaw)
        assertEquals("12.10.2026", restored[0].dateString)

        val second = BackupManager.restoreBackup(context, json).getOrThrow()
        assertEquals("дедупликация по содержимому", 0, second.lessonsRestored)
        assertEquals(1, db.lessonDao().getLessonCountForGroup(group))
    }

    @Test
    fun `JJ14 названия предметов round-trip и слияние без затирания`() = runBlocking {
        val unique = System.nanoTime().toString(36)
        val original = "Предмет А $unique"
        val other = "Предмет Б $unique"
        SubjectRenameStore.setCustomName(context, original, "Коротко А")
        SubjectRenameStore.setCustomName(context, other, "Коротко Б")

        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.SUBJECT_NAMES))
        SubjectRenameStore.reset(context, original)

        val result = BackupManager.restoreBackup(context, json).getOrThrow()
        assertTrue(result.subjectRenamesRestored >= 1)
        assertEquals("Коротко А", SubjectRenameStore.customName(context, original))
        assertEquals("не затёрто", "Коротко Б", SubjectRenameStore.customName(context, other))
    }

    @Test
    fun `JJ14 настройки включают тёмную тему`() = runBlocking {
        AppThemeStore.setDark(context, true)
        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.SETTINGS))
        assertTrue(
            "флаг тёмной темы в секции настроек",
            JSONObject(json).getJSONObject("settings").has("appDark"),
        )

        AppThemeStore.setDark(context, false)
        BackupManager.restoreBackup(context, json).getOrThrow()
        assertTrue("тёмная тема восстановлена", AppThemeStore.isDark(context))

        AppThemeStore.setDark(context, false)
    }

    @Test
    fun `JJ14 настройки включают строку уведомлений и её область`() = runBlocking {
        BellCountdownNotifier.setEnabled(context, true)
        BellCountdownNotifier.setScope(context, BellCountdownNotifier.Scope.COLLEGE)

        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.SETTINGS))

        BellCountdownNotifier.setEnabled(context, false)
        BellCountdownNotifier.setScope(context, BellCountdownNotifier.Scope.MY_LESSONS)

        BackupManager.restoreBackup(context, json).getOrThrow()
        assertTrue(BellCountdownNotifier.isEnabled(context))
        assertEquals(BellCountdownNotifier.Scope.COLLEGE, BellCountdownNotifier.getScope(context))

        BellCountdownNotifier.setEnabled(context, false)
    }
}
