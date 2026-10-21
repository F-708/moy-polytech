package com.politex

import com.politex.data.network.TeacherScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class TeacherScheduleLiveDocTest {
    private fun liveBytes(): ByteArray =
        javaClass.getResourceAsStream("/fixtures/05.10.2026-raspisanie-prepodavatelej.doc")!!.readBytes()

    private fun parsed() = TeacherScheduleParser.parse(liveBytes())

    private fun slotsOf(prefix: String) =
        parsed().entries.firstOrNull { it.key.startsWith(prefix) }?.value

    @Test
    fun `документ разбирается и много преподавателей`() {
        val r = parsed()
        assertTrue("преподаватели должны найтись", r.size >= 50)
    }

    @Test
    fun `повторяющаяся шапка 2 НЕДЕЛЯ не становится преподавателем`() {
        val junk = parsed().keys.filter { it.contains("НЕДЕЛЯ", ignoreCase = true) }
        assertTrue("не должно быть мусорного ключа «2 НЕДЕЛЯ»: $junk", junk.isEmpty())
    }

    @Test
    fun `номера уроков не сдвинуты - Автушко без первого урока, второй это 41Г каб 248`() {
        val slots = slotsOf("Автушко")
        assertNotNull("Автушко должна быть в документе", slots)
        assertFalse("урок 1 у Автушко пустой — его быть не должно", slots!!.any { it.lessonNumber == 1 })
        val l2 = slots.first { it.lessonNumber == 2 }
        assertTrue("на 2-м уроке Автушко ведёт у 41Г", l2.groups.contains("41Г"))
        assertEquals("каб. 248", "248", l2.room)
    }

    @Test
    fun `первый урок не теряется - Базулина ведёт 1-й урок у 41П в 123`() {
        val slots = slotsOf("Базулина")
        assertNotNull(slots)
        val l1 = slots!!.firstOrNull { it.lessonNumber == 1 }
        assertNotNull("1-й урок Базулиной не должен теряться", l1)
        assertTrue("1-й урок — у 41П", l1!!.groups.contains("41П"))
        assertEquals("каб. 123", "123", l1.room)
        assertTrue("3-й урок — у 31П", slots.any { it.lessonNumber == 3 && it.groups.contains("31П") })
    }

    @Test
    fun `уроки 11 и 12 не обрезаются`() {
        val maxLesson = parsed().values.flatten().maxOf { it.lessonNumber }
        assertTrue("в документе есть уроки дальше 10-го (найдено до $maxLesson)", maxLesson >= 11)
    }

    @Test
    fun `все слоты помечены понедельником из шапки`() {
        val all = parsed().values.flatten()
        assertTrue(all.isNotEmpty())
        assertTrue("день = ПОНЕДЕЛЬНИК(1)", all.all { it.dayOfWeek == 1 })
    }

    @Test
    fun `matchesRequestedDate принимает 05_10_2026 и отвергает другой день`() {
        val text = TeacherScheduleParser.extractText(liveBytes())
        assertTrue("текст извлекается", text.isNotBlank())
        val mon = GregorianCalendar(2026, Calendar.OCTOBER, 5)
        val tue = GregorianCalendar(2026, Calendar.OCTOBER, 6)
        assertTrue(TeacherScheduleParser.matchesRequestedDate(text, mon))
        assertFalse(TeacherScheduleParser.matchesRequestedDate(text, tue))
    }
}
