package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.TeacherScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserAnchorTest {

    @Test
    fun `номер урока распознаётся из номера, урока, пары и значка`() {
        assertEquals(5, MpkScheduleParser.detectLessonNumber(listOf("5", "Математика")))
        assertEquals(10, MpkScheduleParser.detectLessonNumber(listOf("10")))
        assertEquals(12, MpkScheduleParser.detectLessonNumber(listOf("12", "Физика")))
        assertEquals(7, MpkScheduleParser.detectLessonNumber(listOf("№ 7")))
        assertEquals(3, MpkScheduleParser.detectLessonNumber(listOf("3 урок")))
        assertEquals(2, MpkScheduleParser.detectLessonNumber(listOf("2 пара")))
    }

    @Test
    fun `цифра кабинета не становится номером урока`() {
        assertNull(MpkScheduleParser.detectLessonNumber(listOf("205", "Математика")))
        assertNull(MpkScheduleParser.detectLessonNumber(listOf("каб. 4", "Физика")))
        assertNull(MpkScheduleParser.detectLessonNumber(listOf("301а")))
        assertNull(MpkScheduleParser.detectLessonNumber(listOf("13")))
        assertNull(MpkScheduleParser.detectLessonNumber(listOf("Математика 205")))
    }

    @Test
    fun `мульти-ран ячейка остаётся одной колонкой`() {
        val xml =
            "<w:document><w:body><w:tbl>" +
                "<w:tr>" +
                "<w:tc><w:tcPr><w:tcW w:w=\"100\"/></w:tcPr>" +
                "<w:p><w:r><w:t>Иван</w:t></w:r><w:r><w:t xml:space=\"preserve\">ов И.И.</w:t></w:r></w:p>" +
                "</w:tc>" +
                "<w:tc><w:p><w:r><w:t>41О</w:t></w:r></w:p></w:tc>" +
                "</w:tr>" +
                "</w:body></w:document>"

        assertEquals("│Иванов И.И.║41О│\n", TeacherScheduleParser.xmlToPseudoTable(xml))
    }

    @Test
    fun `линейка с ведущими пустыми ячейками не становится слотами преподавателя`() {
        val text =
            "║ ПОНЕДЕЛЬНИК 05.10.2026 ║\n" +
                "Иванов И.И.║41О│31П│21С\n" +
                "       ║  │1│2│3\n"

        val parsed = TeacherScheduleParser.parsePlainText(text)
        val slots = parsed.values.flatten()
        assertTrue("слоты преподавателя разобраны", slots.isNotEmpty())
        assertTrue(
            "линейка не должна дать лишних слотов: ${slots.map { it.lessonNumber to it.room }}",
            slots.all { it.lessonNumber in 1..3 && it.room.isEmpty() },
        )
        assertTrue(slots.all { it.dayOfWeek == 1 })
    }
}
