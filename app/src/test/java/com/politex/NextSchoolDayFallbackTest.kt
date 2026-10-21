package com.politex

import com.politex.data.repository.NextDayTarget
import com.politex.data.repository.looksLikeDocument
import com.politex.data.repository.nextDayCandidates
import com.politex.data.repository.nextSchoolDayWithFallback
import com.politex.util.lessonsWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NextSchoolDayFallbackTest {
    private fun cal(
        y: Int,
        m: Int,
        d: Int,
    ): Calendar = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d)
    }

    private fun dateStr(cal: Calendar?): String? = cal?.let {
        "%02d.%02d.%04d".format(
            it.get(Calendar.DAY_OF_MONTH),
            it.get(Calendar.MONTH) + 1,
            it.get(Calendar.YEAR),
        )
    }

    private fun schedule(vararg dates: String): (Calendar) -> Boolean = { cal -> dateStr(cal) in dates }

    @Test
    fun `V2 пятница, суббота не учебная, но расписание на субботу есть — суббота, «Завтра»`() {
        val target = nextSchoolDayWithFallback(cal(2026, 10, 2), saturdayIsSchoolDay = false, hasSchedule = schedule("03.10.2026"))
        assertEquals("03.10.2026", dateStr(target.date))
        assertEquals("Завтра", target.label)
    }

    @Test
    fun `V2 пятница, на субботу ничего, понедельник есть — понедельник, «Понедельник»`() {
        val target = nextSchoolDayWithFallback(cal(2026, 10, 2), saturdayIsSchoolDay = false, hasSchedule = schedule("05.10.2026"))
        assertEquals("05.10.2026", dateStr(target.date))
        assertEquals("Понедельник", target.label)
    }

    @Test
    fun `V2 пятница, ничего нет — null с подписью «Понедельник»`() {
        val target: NextDayTarget = nextSchoolDayWithFallback(cal(2026, 10, 2), saturdayIsSchoolDay = false, hasSchedule = schedule())
        assertNull(target.date)
        assertEquals("Понедельник", target.label)
    }

    @Test
    fun `V2 воскресенье — завтра понедельник учебный, «Завтра»`() {
        val target = nextSchoolDayWithFallback(cal(2026, 10, 4), saturdayIsSchoolDay = false, hasSchedule = schedule())
        assertEquals("05.10.2026", dateStr(target.date))
        assertEquals("Завтра", target.label)
    }

    @Test
    fun `V2 учебная суббота (1 курс), завтра суббота — суббота без проверки расписания`() {
        val target = nextSchoolDayWithFallback(cal(2026, 10, 2), saturdayIsSchoolDay = true, hasSchedule = schedule())
        assertEquals("03.10.2026", dateStr(target.date))
        assertEquals("Завтра", target.label)
    }

    @Test
    fun `V2 будний день — завтра всегда «Завтра»`() {
        val target = nextSchoolDayWithFallback(cal(2026, 10, 1), saturdayIsSchoolDay = false, hasSchedule = schedule())
        assertEquals("02.10.2026", dateStr(target.date))
        assertEquals("Завтра", target.label)
    }

    @Test
    fun `V2 кандидаты буднего дня — только завтра`() {
        val days = nextDayCandidates(cal(2026, 10, 1), saturdayIsSchoolDay = false)
        assertEquals(listOf("02.10.2026"), days.map { dateStr(it) })
    }

    @Test
    fun `V2 кандидаты пятницы с учебной субботой — только суббота`() {
        val days = nextDayCandidates(cal(2026, 10, 2), saturdayIsSchoolDay = true)
        assertEquals(listOf("03.10.2026"), days.map { dateStr(it) })
    }

    @Test
    fun `V2 кандидаты пятницы без учебной субботы — суббота и понедельник, сб первая`() {
        val days = nextDayCandidates(cal(2026, 10, 2), saturdayIsSchoolDay = false)
        assertEquals(listOf("03.10.2026", "05.10.2026"), days.map { dateStr(it) })
    }

    @Test
    fun `V2 кандидаты субботы и воскресенья — понедельник`() {
        assertEquals(listOf("05.10.2026"), nextDayCandidates(cal(2026, 10, 3), saturdayIsSchoolDay = false).map { dateStr(it) })
        assertEquals(listOf("05.10.2026"), nextDayCandidates(cal(2026, 10, 4), saturdayIsSchoolDay = true).map { dateStr(it) })
    }

    @Test
    fun `V6a магия docx (PK zip) и doc (OLE2) распознаётся, HTML — нет`() {
        assertTrue(looksLikeDocument(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3)))
        assertTrue(looksLikeDocument(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0)))
        assertFalse(looksLikeDocument("<html><body>404</body></html>".toByteArray()))
        assertFalse(looksLikeDocument(byteArrayOf(0x50, 0x4B)))
        assertFalse(looksLikeDocument(ByteArray(0)))
    }

    @Test
    fun `V1 lessonsWord склоняется по правилам русского языка`() {
        assertEquals("урок", lessonsWord(1))
        assertEquals("урока", lessonsWord(2))
        assertEquals("уроков", lessonsWord(5))
        assertEquals("уроков", lessonsWord(11))
        assertEquals("уроков", lessonsWord(12))
        assertEquals("уроков", lessonsWord(14))
        assertEquals("уроков", lessonsWord(0))
        assertEquals("урок", lessonsWord(21))
        assertEquals("урока", lessonsWord(22))
        assertEquals("уроков", lessonsWord(25))
        assertEquals("урок", lessonsWord(101))
        assertEquals("уроков", lessonsWord(111))
        assertEquals("урока", lessonsWord(102))
        assertEquals("уроков", lessonsWord(112))
        assertEquals("уроков", lessonsWord(14))
    }
}
