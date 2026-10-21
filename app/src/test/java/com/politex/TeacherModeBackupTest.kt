package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.util.BackupManager
import com.politex.util.DateFormats
import com.politex.util.TeacherModeStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class TeacherModeBackupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db get() = MpkDatabase.getInstance(context)

    @Before
    fun setUp() = runBlocking {
        TeacherModeStore.clear(context)
        com.politex.widget.WidgetUpdateHelper.getPrefs(context).edit().remove("selected_group").apply()
        db.teacherLessonDao().deleteForDate(DateFormats.dotDate(Calendar.getInstance()))
    }

    @After
    fun tearDown() = runBlocking {
        TeacherModeStore.clear(context)
        db.teacherLessonDao().deleteForDate(DateFormats.dotDate(Calendar.getInstance()))
    }

    private fun seedTodayRow() = runBlocking {
        db.teacherLessonDao().insertLessons(
            listOf(
                TeacherLessonEntity(
                    teacherName = "Тестова А.П.",
                    surnameKey = "тестова",
                    dateString = DateFormats.dotDate(Calendar.getInstance()),
                    dayOfWeek = 0,
                    lessonNumber = 1,
                    groups = "11А",
                    subject = "Математика",
                    room = "100",
                ),
            ),
        )
    }

    @Test
    fun `режим преподавателя попадает в секцию настроек`() = runBlocking {
        TeacherModeStore.setTeacher(context, "Тестова Анна Петровна")
        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.SETTINGS))
        assertEquals(
            "Тестова Анна Петровна",
            JSONObject(json).getJSONObject("settings").getString("teacherMode"),
        )
    }

    @Test
    fun `восстановление возвращает режим преподавателя`() = runBlocking {
        seedTodayRow()
        TeacherModeStore.setTeacher(context, "Тестова Анна Петровна")
        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.SETTINGS))

        TeacherModeStore.clear(context)
        BackupManager.restoreBackup(context, json).getOrThrow()

        assertEquals("Тестова Анна Петровна", TeacherModeStore.teacherName(context))
    }

    @Test
    fun `студенческий бэкап выключает режим при восстановлении`() = runBlocking {
        TeacherModeStore.clear(context)
        val json = BackupManager.buildBackup(context, setOf(BackupManager.BackupSection.SETTINGS))

        TeacherModeStore.setTeacher(context, "Тестова Анна Петровна")
        BackupManager.restoreBackup(context, json).getOrThrow()

        assertNull(TeacherModeStore.teacherName(context))
    }
}
