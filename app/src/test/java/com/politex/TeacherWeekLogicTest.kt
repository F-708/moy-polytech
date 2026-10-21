package com.politex

import com.politex.data.repository.TeacherDayLesson
import com.politex.ui.teacher.TeacherWeekLogic
import com.politex.util.DateFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TeacherWeekLogicTest {
    private fun cal(
        y: Int,
        m: Int,
        d: Int,
    ): Calendar = Calendar.getInstance().apply {
        set(y, m - 1, d, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun dates(week: List<Calendar>): List<String> = week.map { DateFormats.dotDate(it) }

    private fun lesson(
        subject: String,
        groups: List<String>,
        start: Int,
        end: Int,
    ) = TeacherDayLesson(1, subject, groups, room = "", start = "", end = "", startMinutes = start, endMinutes = end)

    @Test
    fun `неделя пн-сб из среды`() {
        val week = TeacherWeekLogic.mondayToSaturday(cal(2026, 10, 7))
        assertEquals(6, week.size)
        assertEquals(
            listOf("05.10.2026", "06.10.2026", "07.10.2026", "08.10.2026", "09.10.2026", "10.10.2026"),
            dates(week),
        )
    }

    @Test
    fun `воскресенье относится к предыдущей неделе`() {

        assertEquals("05.10.2026", dates(TeacherWeekLogic.mondayToSaturday(cal(2026, 10, 11))).first())
    }

    @Test
    fun `границы недели - переход месяца`() {

        assertEquals(
            listOf("26.10.2026", "27.10.2026", "28.10.2026", "29.10.2026", "30.10.2026", "31.10.2026"),
            dates(TeacherWeekLogic.mondayToSaturday(cal(2026, 10, 29))),
        )
    }

    @Test
    fun `границы недели - переход года`() {

        assertEquals(
            listOf("28.12.2026", "29.12.2026", "30.12.2026", "31.12.2026", "01.01.2027", "02.01.2027"),
            dates(TeacherWeekLogic.mondayToSaturday(cal(2027, 1, 1))),
        )
    }

    @Test
    fun `сдвиг недели плюс-минус 7 дней`() {
        val week = TeacherWeekLogic.mondayToSaturday(cal(2026, 10, 7))
        assertEquals("12.10.2026", dates(TeacherWeekLogic.shiftWeek(week, 7)).first())
        assertEquals("28.09.2026", dates(TeacherWeekLogic.shiftWeek(week, -7)).first())
    }

    @Test
    fun `агрегация нагрузки - часы через минуты по предметам и группам`() {
        val days =
            listOf(
                listOf(
                    lesson("Математика", listOf("ИС-21"), 480, 570),
                    lesson("Математика", listOf("ИС-21", "ИС-22"), 600, 690),
                ),
                listOf(lesson("Физика", listOf("ИС-22"), 480, 525)),
                emptyList(),
            )
        val load = TeacherWeekLogic.aggregateLoad(days)
        assertEquals(3, load.totalLessons)
        assertEquals(90 + 90 + 45, load.totalMinutes)
        assertEquals(listOf("Математика" to 180, "Физика" to 45), load.bySubject)
        assertEquals(listOf("ИС-21" to 180, "ИС-22" to 135), load.byGroup)
    }

    @Test
    fun `пара без времени не входит в часы но входит в число пар`() {
        val load = TeacherWeekLogic.aggregateLoad(listOf(listOf(lesson("Х", emptyList(), -1, -1))))
        assertEquals(1, load.totalLessons)
        assertEquals(0, load.totalMinutes)
        assertTrue(load.bySubject.isEmpty())
        assertTrue(load.byGroup.isEmpty())
    }

    @Test
    fun `формат минут`() {
        assertEquals("12 ч 30 мин", TeacherWeekLogic.formatMinutes(750))
        assertEquals("2 ч", TeacherWeekLogic.formatMinutes(120))
        assertEquals("45 мин", TeacherWeekLogic.formatMinutes(45))
        assertEquals("0 мин", TeacherWeekLogic.formatMinutes(0))
    }

    @Test
    fun `счётчик дней N из 6`() {
        assertEquals("3 из 6", TeacherWeekLogic.daysWithDataCounter(3))
        assertEquals("0 из 6", TeacherWeekLogic.daysWithDataCounter(0))
        assertNull(TeacherWeekLogic.daysWithDataCounter(6))
        assertNull(TeacherWeekLogic.daysWithDataCounter(7))
    }

    @Test
    fun `дата вне окна кэша 60 дней`() {
        val now = cal(2026, 10, 7)
        assertFalse(TeacherWeekLogic.outsideCacheWindow(cal(2026, 10, 20), now))
        assertTrue(TeacherWeekLogic.outsideCacheWindow(cal(2027, 10, 1), now))
        assertTrue(TeacherWeekLogic.outsideCacheWindow(cal(2026, 1, 1), now))
    }
}
