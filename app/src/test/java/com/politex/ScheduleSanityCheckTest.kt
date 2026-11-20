package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.util.sanityVerdict
import com.politex.util.sanityVerdictTeacher
import com.politex.data.local.entity.TeacherLessonEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSanityCheckTest {
    private val rooms = setOf("101", "102", "201", "204", "301", "401")

    private fun lesson(
        subject: String = "Математика",
        room: String = "204",
        teacher: String = "Ковалев В.П.",
        group: String = "41О",
        date: String = "15.09.2026",
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = 1,
        lessonNumber = 1,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = subject,
        roomFirst = room,
        teacherFirst = teacher,
        dateString = date,
    )

    @Test
    fun `чистый документ проходит`() {
        val rows = listOf(lesson(), lesson(subject = "Физика", room = "301"))
        assertTrue(sanityVerdict(rows, rooms))
    }

    @Test
    fun `пустой список не блокирует запись`() {
        assertTrue(sanityVerdict(emptyList(), rooms))
    }

    @Test
    fun `случайные символы в предмете — коррупция`() {
        val rows = listOf(lesson(subject = "Мате\u0001mat�ика"))
        assertFalse(sanityVerdict(rows, rooms))
    }

    @Test
    fun `управляющий символ в кабинете — коррупция`() {
        val rows = listOf(lesson(room = "20\u00024"))
        assertFalse(sanityVerdict(rows, rooms))
    }

    @Test
    fun `большинство предметов неизвестны — коррупция`() {
        val rows = listOf(
            lesson(subject = "zxcvbnm qwerty"),
            lesson(subject = "asdfghjkl poiuyt"),
            lesson(subject = "Физика"),
        )
        assertFalse(sanityVerdict(rows, rooms))
    }

    @Test
    fun `практика вне реестра проходит`() {
        val rows = listOf(lesson(subject = "Производственная практика", room = "СТД"))
        assertTrue(sanityVerdict(rows, rooms))
    }

    @Test
    fun `кураторский час вне реестра проходит`() {
        val rows = listOf(lesson(subject = "Кураторский час", room = "204"))
        assertTrue(sanityVerdict(rows, rooms))
    }

    @Test
    fun `спортзал и служебные помещения проходят`() {
        val rows = listOf(lesson(subject = "Физическая культура и здоровье", room = "СТД"))
        assertTrue(sanityVerdict(rows, rooms))
    }

    @Test
    fun `неизвестный кабинет формата «текст» — коррупция`() {
        val rows = listOf(lesson(room = "qwerty"))
        assertFalse(sanityVerdict(rows, rooms))
    }

    @Test
    fun `кабинет формата NNN-M известен если базовый есть`() {
        val rows = listOf(lesson(room = "204-1"))
        assertTrue(sanityVerdict(rows, rooms))
    }

    @Test
    fun `фамилия преподавателя из случайных символов — коррупция`() {
        val rows = listOf(lesson(teacher = "1234567890"))
        assertFalse(sanityVerdict(rows, rooms))
    }

    @Test
    fun `одна строка неизвестна из десяти — не коррупция`() {
        val rows = (1..9).map { lesson(subject = "Математика") } + lesson(subject = "Новый предмет практики")
        assertTrue(sanityVerdict(rows, rooms))
    }

    @Test
    fun `teacher-строки с мусором в предмете — коррупция`() {
        val row = TeacherLessonEntity(
            teacherName = "Ковалев В.П.",
            surnameKey = "ковалев",
            dateString = "15.09.2026",
            dayOfWeek = 1,
            lessonNumber = 1,
            groups = "41О",
            subject = "zxcvbnm qwerty",
            room = "204",
        )
        assertFalse(sanityVerdictTeacher(listOf(row)))
    }

    @Test
    fun `teacher-строки чистые проходят`() {
        val row = TeacherLessonEntity(
            teacherName = "Ковалев В.П.",
            surnameKey = "ковалев",
            dateString = "15.09.2026",
            dayOfWeek = 1,
            lessonNumber = 1,
            groups = "41О",
            subject = "Математика",
            room = "204",
        )
        assertTrue(sanityVerdictTeacher(listOf(row)))
    }
}
