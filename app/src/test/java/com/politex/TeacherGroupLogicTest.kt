package com.politex

import com.politex.data.repository.TeacherDayLesson
import com.politex.ui.teacher.TeacherGroupLogic
import com.politex.util.DateFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TeacherGroupLogicTest {
    private fun lesson(
        number: Int,
        vararg groups: String,
    ) = TeacherDayLesson(
        lessonNumber = number,
        subject = "Математика",
        groups = groups.toList(),
        room = "241",
        start = "09:50",
        end = "10:35",
        startMinutes = 590,
        endMinutes = 635,
    )

    @Test
    fun `matches exact group, ignoring case and spaces`() {
        assertTrue(TeacherGroupLogic.matchesGroup(listOf("ИС-21"), "ИС-21"))
        assertTrue(TeacherGroupLogic.matchesGroup(listOf(" ИС-21 "), "ис-21"))
        assertFalse(TeacherGroupLogic.matchesGroup(listOf("ИС-21"), "ИС-22"))
    }

    @Test
    fun `empty target never matches`() {
        assertFalse(TeacherGroupLogic.matchesGroup(listOf("ИС-21"), ""))
        assertFalse(TeacherGroupLogic.matchesGroup(emptyList(), "ИС-21"))
    }

    @Test
    fun `filterForGroup keeps only my lessons for that group`() {
        val lessons = listOf(lesson(1, "ИС-21"), lesson(2, "ИС-22"), lesson(3, "ИС-21", "ИС-22"))
        val result = TeacherGroupLogic.filterForGroup(lessons, "ИС-21")
        assertEquals(listOf(1, 3), result.map { it.lessonNumber })
    }

    @Test
    fun `dayLabel prefixes Сегодня and Завтра`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 8, 12, 0, 0) }
        assertTrue(TeacherGroupLogic.dayLabel(0, cal).startsWith("Сегодня"))
        assertTrue(TeacherGroupLogic.dayLabel(1, cal).startsWith("Завтра"))

        assertTrue(TeacherGroupLogic.dayLabel(0, cal).contains(DateFormats.dotDate(cal).dropLast(5)))
    }
}
