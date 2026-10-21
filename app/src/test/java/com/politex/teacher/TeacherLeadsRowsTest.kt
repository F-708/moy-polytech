package com.politex.teacher

import com.politex.data.local.entity.LessonEntity
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.ui.screens.TeacherRow
import com.politex.ui.screens.buildTeacherRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherLeadsRowsTest {
    private val ivanova = Teacher(name = "Иванова Анна Петровна")
    private val petrov = Teacher(name = "Петров Сергей Иванович")
    private val teachers = listOf(petrov, ivanova)

    private val activeLesson =
        LessonEntity(
            groupName = "41О",
            dayOfWeek = 1,
            lessonNumber = 1,
            timeStart = "09:00",
            timeEnd = "09:45",
            subjectRaw = "Математика",
            roomFirst = "214",
            teacherFirst = "Иванова А.П.",
            dateString = "",
        )

    private fun insights() = TeacherInsights(teachers, listOf(activeLesson))

    @Test
    fun `студенческий режим - активный ведёт у группы, есть заголовок`() {
        val ins = insights()
        assertTrue(ins.isActive(ivanova.name))

        val rows = buildTeacherRows(teachers, "", ins, "41О")
        assertTrue(rows.any { it is TeacherRow.Header && it.title.startsWith("ВЕДУТ У ГРУППЫ") })
    }

    @Test
    fun `учительский режим - insights null, никаких заголовков ВЕДУТ`() {
        val rows = buildTeacherRows(teachers, "", insights = null, groupName = "41О")
        assertFalse("в учительском режиме не должно быть секций-заголовков", rows.any { it is TeacherRow.Header })
    }

    @Test
    fun `учительский режим - все преподаватели одним списком, отсортированы`() {
        val rows = buildTeacherRows(teachers, "", insights = null, groupName = "41О")
        assertEquals(teachers.size, rows.size)
        val names = rows.map { (it as TeacherRow.Item).teacher.name }
        assertEquals(names.sorted(), names)
        assertTrue(names.contains(ivanova.name))
        assertTrue(names.contains(petrov.name))
    }

    @Test
    fun `учительский режим - поиск по ФИО работает`() {
        val rows = buildTeacherRows(teachers, "иванова", insights = null, groupName = "41О")
        assertEquals(1, rows.size)
        assertEquals(ivanova.name, (rows.single() as TeacherRow.Item).teacher.name)
    }
}
