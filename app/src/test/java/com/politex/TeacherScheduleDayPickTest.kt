package com.politex

import com.politex.data.repository.teacherTargetCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

class TeacherScheduleDayPickTest {
    private val base = GregorianCalendar(2027, Calendar.JANUARY, 15, 12, 0, 0)

    @Test
    fun `сегодня — базовый день без сдвига`() {
        val cal = teacherTargetCalendar(dayOffset = 0, customDate = null, base = base)
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
    }

    @Test
    fun `завтра — сдвиг на один день с переходом через месяц`() {
        val endOfMonth = GregorianCalendar(2027, Calendar.JANUARY, 31, 12, 0, 0)
        val cal = teacherTargetCalendar(dayOffset = 1, customDate = null, base = endOfMonth)
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
    }

    @Test
    fun `дата из календаря побеждает dayOffset`() {
        val picked = GregorianCalendar(2027, Calendar.MARCH, 5)
        val cal = teacherTargetCalendar(dayOffset = 1, customDate = picked, base = base)
        assertSame("календарь важнее сегодняшне-завтрашнего сдвига", picked, cal)
    }

    @Test
    fun `базовый календарь не мутируется`() {
        val before = base.timeInMillis
        teacherTargetCalendar(dayOffset = 3, customDate = null, base = base)
        assertEquals(before, base.timeInMillis)
        assertNotSame(base, teacherTargetCalendar(dayOffset = 3, customDate = null, base = base))
    }

    @Test
    fun `ключ дня совпадает с форматом dateString в БД`() {

        val cal = GregorianCalendar(2027, Calendar.MARCH, 5, 9, 30)
        val uiKey = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(cal.time)
        val dbKey =
            "%02d.%02d.%04d".format(
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.YEAR),
            )
        assertEquals("05.03.2027", uiKey)
        assertEquals(uiKey, dbKey)
    }
}
