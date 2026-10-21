package com.politex.data.repository

import com.politex.data.local.entity.LessonEntity
import com.politex.util.HomeworkMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleArchiveTest {
    private fun ms(
        y: Int,
        m: Int,
        d: Int,
    ): Long = Calendar
        .getInstance()
        .apply {
            clear()
            set(y, m - 1, d)
        }.timeInMillis

    private val cutoff = ms(2026, 1, 1)

    @Test
    fun `дата до отсечки считается устаревшей`() {
        assertTrue(isStaleDate("31.12.2025", cutoff))
        assertTrue(isStaleDate("01.09.2025", cutoff))
    }

    @Test
    fun `дата отсечки и позже остаётся`() {
        assertFalse(isStaleDate("01.01.2026", cutoff))
        assertFalse(isStaleDate("15.09.2026", cutoff))
    }

    @Test
    fun `не-даты не удаляются`() {
        assertFalse(isStaleDate("", cutoff))
        assertFalse(isStaleDate("2025-09-01", cutoff))
        assertFalse(isStaleDate("1.9.2025", cutoff))
        assertFalse(isStaleDate("сегодня", cutoff))
    }

    @Test
    fun `прошедший тот же день недели не равен текущей неделе`() {

        val past = ms(2026, 9, 8)
        val current = ms(2026, 9, 15)
        assertNotEquals(past, current)
        assertTrue(past < current)

        assertFalse("08.09.2026" == "15.09.2026")
    }

    @Test
    fun `граница месяца и года при штамповке дат`() {

        val monday =
            Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.SEPTEMBER, 28)
            }
        assertEquals(Calendar.MONDAY, monday.get(Calendar.DAY_OF_WEEK))
        val saturday = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 5) }
        assertEquals(3, saturday.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.OCTOBER, saturday.get(Calendar.MONTH))

        val decMonday =
            Calendar.getInstance().apply {
                clear()
                set(2026, Calendar.DECEMBER, 28)
            }
        assertEquals(Calendar.MONDAY, decMonday.get(Calendar.DAY_OF_WEEK))
        val nextYearSat = (decMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 5) }
        assertEquals(2027, nextYearSat.get(Calendar.YEAR))
        assertEquals(2, nextYearSat.get(Calendar.DAY_OF_MONTH))
    }

    private fun lesson(
        date: String,
        dow: Int,
        number: Int,
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = dow,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:00",
        subjectRaw = "Тест",
        roomFirst = "1",
        teacherFirst = "",
        dateString = date,
    )

    @Test
    fun `format consistency - dot и ISO приводятся к одной дате`() {
        val parser = com.politex.data.network.MpkScheduleParser
        val dot = parser.parseDotDate("15.09.2026")
        val iso = parser.parseDotDate("2026-09-15")
        assertTrue(dot != null && iso != null)
        assertEquals(dot!!.timeInMillis, iso!!.timeInMillis)
    }

    @Test
    fun `lexicographic trap - 28 сентября позже 5 октября только по строке`() {

        val sep = ms(2026, 9, 28)
        val oct = ms(2026, 10, 5)
        assertTrue(sep < oct)
        assertTrue("28.09.2026" > "05.10.2026")
    }

    @Test
    fun `dedupeByLatestDate берёт позднюю дату по Calendar`() {
        val rows =
            listOf(
                lesson("28.09.2026", 1, 8),
                lesson("05.10.2026", 1, 6),
                lesson("", 1, 3),
            )
        val picked = HomeworkMarkers.dedupeByLatestDate(rows)

        assertEquals(setOf("05.10.2026", ""), picked.map { it.dateString }.toSet())
    }

    @Test
    fun `dedupeByLatestDate с ISO-датой тоже сравнивается корректно`() {
        val rows =
            listOf(
                lesson("2026-09-28", 1, 8),
                lesson("05.10.2026", 1, 6),
            )
        val picked = HomeworkMarkers.dedupeByLatestDate(rows)
        assertEquals(listOf(6), picked.map { it.lessonNumber })
    }
}
