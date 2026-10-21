package com.politex

import com.politex.widget.NextLessonRef
import com.politex.widget.TeacherDayRef
import com.politex.widget.TeacherNextState
import com.politex.widget.TeacherWidgetLesson
import com.politex.widget.TeacherWidgetLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherWidgetLogicTest {
    private fun lesson(
        n: Int,
        startMin: Int,
        endMin: Int,
        room: String = "241",
        subject: String = "Математика",
        groups: List<String> = listOf("241"),
    ) = TeacherWidgetLesson(
        lessonNumber = n,
        subject = subject,
        groups = groups,
        room = room,
        start = "%02d:%02d".format(startMin / 60, startMin % 60),
        end = "%02d:%02d".format(endMin / 60, endMin % 60),
        startMinutes = startMin,
        endMinutes = endMin,
    )

    private fun at(h: Int, m: Int) = h * 60 + m

    @Test
    fun state_running_nowInsideLesson() {
        val l = listOf(lesson(1, at(8, 0), at(9, 0)), lesson(2, at(9, 0), at(10, 0)))
        val s = TeacherWidgetLogic.nextState(l, at(8, 30), dow = 3, next = null)
        assertTrue(s is TeacherNextState.Running)
        assertEquals(1, (s as TeacherNextState.Running).lesson.lessonNumber)
    }

    @Test
    fun state_nextToday_nowBeforeFirst() {
        val l = listOf(lesson(1, at(9, 0), at(10, 0)))
        val s = TeacherWidgetLogic.nextState(l, at(8, 0), dow = 3, next = null)
        assertTrue(s is TeacherNextState.NextToday)
        assertEquals(1, (s as TeacherNextState.NextToday).lesson.lessonNumber)
    }

    @Test
    fun state_nextToday_skipsRunningPicksFollowing() {
        val l = listOf(lesson(1, at(8, 0), at(9, 0)), lesson(2, at(9, 0), at(10, 0)))

        assertTrue(TeacherWidgetLogic.nextState(l, at(8, 30), 3, null) is TeacherNextState.Running)

        assertEquals(2, (TeacherWidgetLogic.nextState(l, at(9, 30), 3, null) as TeacherNextState.Running).lesson.lessonNumber)
    }

    @Test
    fun state_dayDone_allPast() {
        val l = listOf(lesson(1, at(8, 0), at(9, 0)))
        val s = TeacherWidgetLogic.nextState(l, at(20, 0), dow = 3, next = null)
        assertTrue(s is TeacherNextState.DayDone)
    }

    @Test
    fun state_emptyToday_carriesNextRef() {
        val next = NextLessonRef("09.10.2026", 4, lesson(1, at(9, 50), at(10, 35)))
        val s = TeacherWidgetLogic.nextState(emptyList(), at(12, 0), dow = 3, next = next)
        assertTrue(s is TeacherNextState.EmptyToday)
        assertEquals(next, (s as TeacherNextState.EmptyToday).next)
    }

    @Test
    fun state_dayOff_sundayNoLessons() {
        val s = TeacherWidgetLogic.nextState(emptyList(), at(12, 0), dow = 7, next = null)
        assertTrue(s is TeacherNextState.DayOff)
    }

    @Test
    fun state_sundayWithLessons_notDayOff() {

        val l = listOf(lesson(1, at(9, 0), at(10, 0)))
        assertTrue(TeacherWidgetLogic.nextState(l, at(9, 30), 7, null) is TeacherNextState.Running)
    }

    @Test
    fun findNext_skipsSundayAndEmptyDays() {
        val days = listOf(
            TeacherDayRef("09.10.2026", 4, emptyList()),
            TeacherDayRef("10.10.2026", 5, listOf(lesson(1, at(8, 0), at(9, 0)))),
            TeacherDayRef("11.10.2026", 6, listOf(lesson(1, at(8, 0), at(9, 0)))),
        )
        val next = TeacherWidgetLogic.findNextLesson(days)
        assertEquals("10.10.2026", next?.dateString)
        assertEquals(5, next?.dow)
    }

    @Test
    fun findNext_sundayDaySkippedEvenIfListed() {
        val days = listOf(
            TeacherDayRef("sun", 7, listOf(lesson(1, at(8, 0), at(9, 0)))),
            TeacherDayRef("mon", 1, listOf(lesson(2, at(8, 0), at(9, 0)))),
        )
        assertEquals("mon", TeacherWidgetLogic.findNextLesson(days)?.dateString)
    }

    @Test
    fun findNext_picksLowestLessonNumber() {
        val days = listOf(
            TeacherDayRef("d", 2, listOf(lesson(3, at(11, 0), at(12, 0)), lesson(1, at(8, 0), at(9, 0)))),
        )
        assertEquals(1, TeacherWidgetLogic.findNextLesson(days)?.lesson?.lessonNumber)
    }

    @Test
    fun findNext_emptyReturnsNull() {
        assertNull(TeacherWidgetLogic.findNextLesson(emptyList()))
    }

    @Test
    fun cacheKey_includesDateMinuteProfile() {
        val base = TeacherWidgetLogic.cacheKey("08.10.2026", 500, "Иванов И.И.")

        assertFalse(base == TeacherWidgetLogic.cacheKey("09.10.2026", 500, "Иванов И.И."))
        assertFalse(base == TeacherWidgetLogic.cacheKey("08.10.2026", 501, "Иванов И.И."))
        assertFalse(base == TeacherWidgetLogic.cacheKey("08.10.2026", 500, "Петров П.П."))

        assertEquals(base, TeacherWidgetLogic.cacheKey("08.10.2026", 500, "Иванов И.И."))
    }

    @Test
    fun updatedLabel_zeroEpoch_blank() {
        assertEquals("", TeacherWidgetLogic.updatedLabel(0L))
    }

    @Test
    fun updatedLabel_positive_hasPrefix() {
        assertTrue(TeacherWidgetLogic.updatedLabel(System.currentTimeMillis()).startsWith("обновлено в "))
    }

    @Test
    fun todayHeader_format() {
        assertEquals("Сегодня, 08.10", TeacherWidgetLogic.todayHeader(8, 10))
    }

    @Test
    fun nextLessonText_includesDowTimeRoom() {
        val next = NextLessonRef("d", 2, lesson(1, at(9, 50), at(10, 35), room = "241"))
        val t = TeacherWidgetLogic.nextLessonText(next)
        assertTrue(t.contains("вт"))
        assertTrue(t.contains("09:50"))
        assertTrue(t.contains("каб. 241"))
    }

    @Test
    fun dayOffText_withoutNext_canonicalMonday() {
        assertEquals("Выходной.\nДо 1-й пары в пн", TeacherWidgetLogic.dayOffText(null))
    }

    @Test
    fun emptyText_withNext_appendsSecondLine() {
        val next = NextLessonRef("d", 4, lesson(1, at(9, 50), at(10, 35)))
        val t = TeacherWidgetLogic.emptyText("Сегодня пар нет.", next)
        assertTrue(t.startsWith("Сегодня пар нет."))
        assertTrue(t.contains("Следующая —"))
    }
}
