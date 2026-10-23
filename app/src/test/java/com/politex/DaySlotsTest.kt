package com.politex

import com.politex.ui.util.daySlots
import com.politex.util.Dow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DaySlotsTest {

    private fun cal(day: Int): Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.SEPTEMBER, day, 12, 0, 0)
    }

    private fun dom(c: Calendar): Int = c.get(Calendar.DAY_OF_MONTH)

    private fun slots(
        day: Int,
        saturdayIsSchoolDay: Boolean = false,
        hasSchedule: (Calendar) -> Boolean = { false },
    ) = daySlots(cal(day), saturdayIsSchoolDay, hasSchedule)

    @Test
    fun `среда — сегодня и завтра`() {
        val (l, r) = slots(23)
        assertEquals(23, dom(l))
        assertEquals(24, dom(r))
        assertEquals(3, Dow.of(l))
    }

    @Test
    fun `пятница — слева пятница, справа понедельник`() {
        val (l, r) = slots(25)
        assertEquals(25, dom(l))
        assertEquals(28, dom(r))
    }

    @Test
    fun `суббота без занятий — оба слота сдвигаются на пн и вт`() {
        val (l, r) = slots(26)
        assertEquals(28, dom(l))
        assertEquals(29, dom(r))
    }

    @Test
    fun `суббота с занятиями — сегодня суббота, справа понедельник`() {
        val (l, r) = slots(26, saturdayIsSchoolDay = true)
        assertEquals(26, dom(l))
        assertEquals(6, Dow.of(l))
        assertEquals(28, dom(r))
    }

    @Test
    fun `воскресенье — понедельник и вторник`() {
        val (l, r) = slots(27)
        assertEquals(28, dom(l))
        assertEquals(29, dom(r))
    }

    @Test
    fun `fallback на дату с расписанием — суббота вне учебного графика`() {
        val (l, r) = slots(25) { c -> dom(c) == 26 }
        assertEquals(25, dom(l))
        assertEquals("правый слот — суббота, на неё есть расписание", 26, dom(r))
    }

    @Test
    fun `обычный учебный день — fallback с расписанием не задействуется`() {
        val (l, r) = slots(22) { c -> dom(c) == 27 }
        assertEquals(22, dom(l))
        assertEquals(23, dom(r))
    }
}
