package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.TeacherModeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TeacherModeStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        TeacherModeStore.clear(context)
    }

    @Test
    fun `по умолчанию режим преподавателя выключен`() {
        assertFalse(TeacherModeStore.isTeacher(context))
        assertNull(TeacherModeStore.teacherName(context))
    }

    @Test
    fun `setTeacher включает режим и сохраняет ФИО`() {
        TeacherModeStore.setTeacher(context, "Еременко Ольга Васильевна")
        assertTrue(TeacherModeStore.isTeacher(context))
        assertEquals("Еременко Ольга Васильевна", TeacherModeStore.teacherName(context))
    }

    @Test
    fun `setTeacher с пустым именем режим не включает`() {
        TeacherModeStore.setTeacher(context, "   ")
        assertFalse(TeacherModeStore.isTeacher(context))
        assertNull(TeacherModeStore.teacherName(context))
    }

    @Test
    fun `setTeacher обрезает пробелы по краям`() {
        TeacherModeStore.setTeacher(context, "  Савицкая Ольга Владимировна  ")
        assertEquals("Савицкая Ольга Владимировна", TeacherModeStore.teacherName(context))
    }

    @Test
    fun `повторный setTeacher заменяет преподавателя`() {
        TeacherModeStore.setTeacher(context, "Еременко Ольга Васильевна")
        TeacherModeStore.setTeacher(context, "Кайдова Любовь Сергеевна")
        assertEquals("Кайдова Любовь Сергеевна", TeacherModeStore.teacherName(context))
    }

    @Test
    fun `clear выключает режим и стирает имя`() {
        TeacherModeStore.setTeacher(context, "Еременко Ольга Васильевна")
        TeacherModeStore.clear(context)
        assertFalse(TeacherModeStore.isTeacher(context))
        assertNull(TeacherModeStore.teacherName(context))
    }

    @Test
    fun `roundtrip set-clear-set сохраняет последнее значение`() {
        TeacherModeStore.setTeacher(context, "Еременко Ольга Васильевна")
        assertTrue(TeacherModeStore.isTeacher(context))
        TeacherModeStore.clear(context)
        assertFalse(TeacherModeStore.isTeacher(context))
        TeacherModeStore.setTeacher(context, "Мельник Артём Сергеевич")
        assertTrue(TeacherModeStore.isTeacher(context))
        assertEquals("Мельник Артём Сергеевич", TeacherModeStore.teacherName(context))
    }

    @Test
    fun `revision растёт при записи и очистке`() {
        val before = TeacherModeStore.revision.intValue
        TeacherModeStore.setTeacher(context, "Еременко Ольга Васильевна")
        val afterSet = TeacherModeStore.revision.intValue
        assertTrue(afterSet > before)
        TeacherModeStore.clear(context)
        assertTrue(TeacherModeStore.revision.intValue > afterSet)
    }
}
