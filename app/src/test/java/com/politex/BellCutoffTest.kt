package com.politex

import com.politex.data.model.CollegeBellSchedule
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BellCutoffTest {
    private val standard = CollegeBellSchedule.getBellsForDay(1)
    private val saturday = CollegeBellSchedule.getBellsForDay(6)

    @Test
    fun hiddenBefore0700EvenIfLessonsLater() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                6 * 60 + 59,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun visibleAt0700Sharp() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                7 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )

        assertEquals(75L, info?.first)
        assertEquals("до 1 урока", info?.second)
    }

    @Test
    fun groupLessons_stillVisibleInsideLastLesson() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                13 * 60 + 30,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals("до звонка", info!!.second)
        assertEquals(6, info.third)
        assertEquals(35L, info.first)
    }

    @Test
    fun groupLessons_lastMinuteOfLastLessonStillCounts() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                14 * 60 + 5,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals(0L, info!!.first)
        assertEquals(6, info.third)
    }

    @Test
    fun groupLessons_hiddenOneMinuteAfterGroupEnd() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                14 * 60 + 6,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun groupLessons_breakAfterLastGroupLesson_hiddenNotNextBell() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                14 * 60 + 10,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun groupLessons_afternoonAfterGroupEnd_hidden() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                16 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun groupLessons_breakBetweenGroupLessons_countsToNextGroupLesson() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                12 * 60 + 10,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals("до 5 урока", info!!.second)
        assertEquals(15L, info.first)
    }

    @Test
    fun groupLessons_smallBreakBeforeLastLesson() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                13 * 60 + 12,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertNotNull(info)
        assertEquals("до 6 урока", info!!.second)
        assertEquals(8L, info.first)
    }

    @Test
    fun groupLessons_noLessonsToday_hiddenAllDay() {
        for (minute in intArrayOf(7 * 60, 10 * 60, 15 * 60, 20 * 60)) {
            assertNull(
                BellCountdownWidgetProvider.countdownForMinute(
                    minute,
                    1,
                    standard,
                    CountdownMode.GROUP_LESSONS,
                    null,
                ),
            )
        }
    }

    @Test
    fun saturday_visibleDuringLastLesson() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                15 * 60,
                6,
                saturday,
                CountdownMode.GROUP_LESSONS,
                8,
            )
        assertNotNull(info)
        assertEquals(8, info!!.third)
        assertEquals(25L, info.first)
    }

    @Test
    fun saturday_hiddenAfterLastLesson() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                15 * 60 + 26,
                6,
                saturday,
                CountdownMode.GROUP_LESSONS,
                8,
            ),
        )
    }

    @Test
    fun saturday_groupWith6Lessons_hiddenAfter13_35() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                13 * 60 + 36,
                6,
                saturday,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun allBells_visibleDuringLateLesson() {
        val info =
            BellCountdownWidgetProvider.countdownForMinute(
                18 * 60,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            )
        assertNotNull(info)
        assertEquals("до звонка", info!!.second)
        assertEquals(40L, info.first)
    }

    @Test
    fun allBells_hiddenAfterCollegeLastBell() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                19 * 60 + 36,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            ),
        )
    }

    @Test
    fun sunday_hiddenAllDay_bothModes() {
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                12 * 60,
                7,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
        assertNull(
            BellCountdownWidgetProvider.countdownForMinute(
                12 * 60,
                7,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            ),
        )
    }
}
