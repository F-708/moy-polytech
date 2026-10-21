package com.politex

import com.politex.data.repository.nextSchoolDay
import com.politex.data.repository.saturdayIsSchoolDayFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class W1SyncTargetDateTest {
    private fun cal(
        y: Int,
        m: Int,
        d: Int,
    ): Calendar = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d)
    }

    private fun assertDate(
        expected: String,
        actual: Calendar,
    ) {
        val actualStr = "%02d.%02d.%04d".format(
            actual.get(Calendar.DAY_OF_MONTH),
            actual.get(Calendar.MONTH) + 1,
            actual.get(Calendar.YEAR),
        )
        assertEquals(expected, actualStr)
    }

    @Test
    fun `W1 пятница для группы без субботы - цель понедельник 05_10_2026`() {

        val target = nextSchoolDay(cal(2026, 10, 2), saturdayIsSchoolDay = false)
        assertDate("05.10.2026", target)
        assertEquals(Calendar.MONDAY, target.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `W1 пятница для 1 курса (суббота учебная) - цель суббота 03_10_2026`() {
        val target = nextSchoolDay(cal(2026, 10, 2), saturdayIsSchoolDay = true)
        assertDate("03.10.2026", target)
        assertEquals(Calendar.SATURDAY, target.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun `W1 суббота для группы без субботы - цель понедельник`() {
        val target = nextSchoolDay(cal(2026, 10, 3), saturdayIsSchoolDay = false)
        assertDate("05.10.2026", target)
    }

    @Test
    fun `W1 суббота для 1 курса - цель понедельник (суббота уже сегодня)`() {
        val target = nextSchoolDay(cal(2026, 10, 3), saturdayIsSchoolDay = true)
        assertDate("05.10.2026", target)
    }

    @Test
    fun `W1 воскресенье - всегда понедельник`() {
        val target = nextSchoolDay(cal(2026, 10, 4), saturdayIsSchoolDay = true)
        assertDate("05.10.2026", target)
    }

    @Test
    fun `W1 будний день - следующий день без перескоков`() {

        assertDate("02.10.2026", nextSchoolDay(cal(2026, 10, 1), saturdayIsSchoolDay = false))
        assertDate("02.10.2026", nextSchoolDay(cal(2026, 10, 1), saturdayIsSchoolDay = true))
    }

    @Test
    fun `W1 суббота учебная - 1 курс или фактические субботние уроки в БД`() {
        assertTrue(saturdayIsSchoolDayFor("11О", existingSaturdayLessons = false))
        assertFalse(saturdayIsSchoolDayFor("41О", existingSaturdayLessons = false))

        assertFalse(saturdayIsSchoolDayFor("???", existingSaturdayLessons = false))

        assertTrue(saturdayIsSchoolDayFor("41О", existingSaturdayLessons = true))
    }

    @Test
    fun `W1 четверг - цель пятница при любом флаге субботы`() {
        assertDate("02.10.2026", nextSchoolDay(cal(2026, 10, 1), saturdayIsSchoolDay = true))
    }
}
