package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.teacher.TeacherProfile
import com.politex.ui.teacher.TeacherProfileStore
import com.politex.util.TeacherModeStore
import com.politex.worker.resolveWorkerProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TeacherWorkerProfileTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        TeacherProfileStore.set(context, null)
        TeacherModeStore.clear(context)
    }

    @Test
    fun `in-app режим даёт профиль с фамилией`() {
        TeacherModeStore.setTeacher(context, "Тестова Анна Петровна")
        val profile = resolveWorkerProfile(context)
        assertEquals("Тестова Анна Петровна", profile?.name)
        assertEquals("тестова", profile?.surnameKey)
    }

    @Test
    fun `флейворный профиль приоритетнее in-app режима`() {
        TeacherProfileStore.set(context, TeacherProfile("Флавова Ф.Ф.", "флавова"))
        TeacherModeStore.setTeacher(context, "Тестова Анна Петровна")
        assertEquals("Флавова Ф.Ф.", resolveWorkerProfile(context)?.name)
    }

    @Test
    fun `без режима возвращает null`() {
        assertNull(resolveWorkerProfile(context))
    }
}
