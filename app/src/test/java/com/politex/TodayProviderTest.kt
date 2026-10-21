package com.politex

import com.politex.ui.util.dayKeyOf
import com.politex.ui.util.minutesOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Calendar

class TodayProviderTest {
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, minute, 0)
    }

    @Test
    fun `ключ дня одинаков в пределах суток`() {
        val morning = at(2026, 10, 1, 7, 30)
        val evening = at(2026, 10, 1, 23, 59)
        assertEquals(dayKeyOf(morning), dayKeyOf(evening))
    }

    @Test
    fun `ключ дня меняется за полночь — «сегодня» пересчитается`() {
        val before = at(2026, 9, 30, 23, 59)
        val after = at(2026, 10, 1, 0, 0)
        assertNotEquals(dayKeyOf(before), dayKeyOf(after))
    }

    @Test
    fun `переход года даёт разные ключи`() {
        assertNotEquals(dayKeyOf(at(2026, 12, 31)), dayKeyOf(at(2027, 1, 1)))
    }

    @Test
    fun `минуты от начала суток`() {
        assertEquals(0, minutesOfDay(at(2026, 10, 1, 0, 0)))
        assertEquals(8 * 60 + 15, minutesOfDay(at(2026, 10, 1, 8, 15)))
        assertEquals(23 * 60 + 59, minutesOfDay(at(2026, 10, 1, 23, 59)))
    }
}
