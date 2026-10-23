package com.politex.dataeditor

import com.politex.content.OwnerOverridesStore
import com.politex.content.TeacherOverride
import com.politex.data.repository.Teacher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TeacherEditorLogicTest {
    private val teacher =
        Teacher(
            name = "Иванов Иван",
            position = "Преподаватель",
            subjects = "Физика",
            phone = "111",
            experience = "5 лет",
            category = "Первая",
            room = "10",
            photo = "ivanov.jpg",
        )

    @Test
    fun `formFor заполняет форму из преподавателя`() {
        val form = TeacherEditorLogic.formFor("Иванов Иван", teacher, null)
        assertEquals("иванов иван", form.key)
        assertEquals("Иванов Иван", form.fio)
        assertEquals("Преподаватель", form.position)
        assertEquals("Физика", form.subjects)
        assertEquals("111", form.phone)
        assertEquals("5 лет", form.experience)
        assertEquals("Первая", form.category)
        assertNull(form.pendingPhoto)
    }

    @Test
    fun `formFor отдаёт приоритет существующей правке`() {
        val override =
            TeacherOverride(
                key = "иванов иван",
                fio = "Иванов Иван Иванович",
                position = "Зав отделением",
                phone = "222",
                subjects = "Физика, Астрономия",
                experience = "20 лет",
                category = "Высшая",
                version = 3,
            )
        val form = TeacherEditorLogic.formFor("иванов иван", teacher, override)
        assertEquals("Иванов Иван Иванович", form.fio)
        assertEquals("Зав отделением", form.position)
        assertEquals("222", form.phone)
        assertEquals("20 лет", form.experience)
        assertEquals("Высшая", form.category)
    }

    @Test
    fun `formFor без преподавателя даёт пустую форму с нормализованным ключом`() {
        val form = TeacherEditorLogic.formFor("  Петрова  АННА ", null, null)
        assertEquals("петрова анна", form.key)
        assertEquals("", form.fio)
    }

    @Test
    fun `isDirty реагирует на каждое поле и на фото`() {
        val initial = TeacherEditorLogic.formFor("Иванов Иван", teacher, null)
        assertFalse(TeacherEditorLogic.isDirty(initial, initial))
        assertTrue(TeacherEditorLogic.isDirty(initial.copy(fio = "Другое ФИО"), initial))
        assertTrue(TeacherEditorLogic.isDirty(initial.copy(position = "Иное"), initial))
        assertTrue(TeacherEditorLogic.isDirty(initial.copy(phone = "999"), initial))
        assertTrue(TeacherEditorLogic.isDirty(initial.copy(subjects = "Химия"), initial))
        assertTrue(TeacherEditorLogic.isDirty(initial.copy(experience = "1 год"), initial))
        assertTrue(TeacherEditorLogic.isDirty(initial.copy(category = "Вторая"), initial))
        assertTrue(TeacherEditorLogic.isDirty(initial.copy(pendingPhoto = File("x.jpg")), initial))
        assertEquals(initial, initial.copy(fio = initial.fio))
        assertFalse(TeacherEditorLogic.isDirty(initial.copy(fio = initial.fio), initial))
    }

    @Test
    fun `validate запрещает пустое фио`() {
        val initial = TeacherEditorLogic.formFor("Иванов Иван", teacher, null)
        assertNull(TeacherEditorLogic.validate(initial))
        val blank = initial.copy(fio = "   ")
        assertNotNull(TeacherEditorLogic.validate(blank))
    }

    @Test
    fun `canSave требует изменений валидности и отсутствия сохранения`() {
        val initial = TeacherEditorLogic.formFor("Иванов Иван", teacher, null)
        val changed = initial.copy(phone = "333")
        assertFalse(TeacherEditorLogic.canSave(initial, initial, saving = false))
        assertTrue(TeacherEditorLogic.canSave(changed, initial, saving = false))
        assertFalse(TeacherEditorLogic.canSave(changed, initial, saving = true))
        assertFalse(TeacherEditorLogic.canSave(changed.copy(fio = ""), initial, saving = false))
    }

    @Test
    fun `statusText покрывает все состояния`() {
        assertEquals("", TeacherEditorLogic.statusText(TeacherEditorStatus.IDLE))
        assertEquals("Сохранено", TeacherEditorLogic.statusText(TeacherEditorStatus.SAVED))
        assertEquals("Правка удалена", TeacherEditorLogic.statusText(TeacherEditorStatus.DELETED))
        assertEquals("Ошибка сохранения", TeacherEditorLogic.statusText(TeacherEditorStatus.ERROR))
    }

    @Test
    fun `ключ формы стабилен после сохранения правки со сменой фио`() {
        val initial = TeacherEditorLogic.formFor("Иванов Иван", teacher, null)
        val saved =
            TeacherOverride(
                key = initial.key,
                fio = "Иванов Иван Иванович",
                position = initial.position,
                version = 1,
            )
        val reopened = TeacherEditorLogic.formFor(initial.key, teacher, saved)
        assertEquals(initial.key, reopened.key)
        assertEquals(OwnerOverridesStore.keyOf("иванов иван"), reopened.key)
    }
}
