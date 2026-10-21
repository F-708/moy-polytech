package com.politex

import com.politex.data.repository.TeacherDayLesson
import com.politex.ui.viewmodel.toLessonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TeacherLessonMappingTest {

    private fun lesson(
        number: Int = 2,
        subject: String = "Математика",
        groups: List<String> = listOf("11А", "12Б"),
        room: String = "204",
        start: String = "09:35",
        end: String = "10:55",
    ) = TeacherDayLesson(
        lessonNumber = number,
        subject = subject,
        groups = groups,
        room = room,
        start = start,
        end = end,
        startMinutes = 575,
        endMinutes = 655,
    )

    @Test
    fun `маппинг переносит все поля карточки расписания`() {
        val entity = lesson().toLessonEntity("20.10.2026")
        assertEquals("11А, 12Б", entity.groupName)
        assertEquals(2, entity.lessonNumber)
        assertEquals("09:35", entity.timeStart)
        assertEquals("10:55", entity.timeEnd)
        assertEquals("Математика", entity.subjectRaw)
        assertEquals("204", entity.roomFirst)
        assertEquals("20.10.2026", entity.dateString)
    }

    @Test
    fun `маппинг зануляет преподавателя и сплит`() {
        val entity = lesson().toLessonEntity("20.10.2026")
        assertEquals("", entity.teacherFirst)
        assertEquals("", entity.teacherSecond)
        assertEquals("", entity.roomSecond)
        assertFalse(entity.isSplit)
    }

    @Test
    fun `день недели вычисляется из даты`() {
        assertEquals(2, lesson().toLessonEntity("20.10.2026").dayOfWeek)
        assertEquals(6, lesson().toLessonEntity("24.10.2026").dayOfWeek)
        assertEquals(7, lesson().toLessonEntity("25.10.2026").dayOfWeek)
    }

    @Test
    fun `битая дата даёт dayOfWeek 0`() {
        assertEquals(0, lesson().toLessonEntity("не дата").dayOfWeek)
    }

    @Test
    fun `пустой список групп даёт пустую строку группы`() {
        assertEquals("", lesson(groups = emptyList()).toLessonEntity("20.10.2026").groupName)
    }

    @Test
    fun `одна группа без разделителя`() {
        assertEquals("43Э", lesson(groups = listOf("43Э")).toLessonEntity("20.10.2026").groupName)
    }
}
