package com.politex

import com.politex.data.model.CollegeBellSchedule
import com.politex.util.BellCountdownNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationQuietWindowTest {
    private val resume = BellCountdownNotifier.RESUME_MINUTE
    private val endOfLessons = 14 * 60 + 5

    @Test
    fun duringLessons_visible() {
        assertFalse(BellCountdownNotifier.isQuietWindow(13 * 60, endOfLessons))
    }

    @Test
    fun lastMinuteOfLessons_visible() {
        assertFalse(BellCountdownNotifier.isQuietWindow(endOfLessons, endOfLessons))
    }

    @Test
    fun oneMinuteAfterLessons_quiet() {
        assertTrue(BellCountdownNotifier.isQuietWindow(endOfLessons + 1, endOfLessons))
    }

    @Test
    fun evening_quiet() {
        assertTrue(BellCountdownNotifier.isQuietWindow(22 * 60, endOfLessons))
    }

    @Test
    fun deepNight_quiet() {
        assertTrue(BellCountdownNotifier.isQuietWindow(3 * 60 + 30, endOfLessons))
    }

    @Test
    fun fiveFiftyNine_quiet() {
        assertTrue(BellCountdownNotifier.isQuietWindow(resume - 1, endOfLessons))
    }

    @Test
    fun sixExactly_visibleAgain() {
        assertFalse(BellCountdownNotifier.isQuietWindow(resume, endOfLessons))
    }

    @Test
    fun beforeSix_quietEvenWithoutLessonsData() {

        assertTrue(BellCountdownNotifier.isQuietWindow(5 * 60 + 59, null))
    }

    @Test
    fun atSixResume_plusLessonsEndedFromYesterdayStillVisible() {

        assertFalse(BellCountdownNotifier.isQuietWindow(resume, endOfLessons))
    }

    @Test
    fun sixOhFive_visible() {
        assertFalse(BellCountdownNotifier.isQuietWindow(6 * 60 + 5, endOfLessons))
    }

    @Test
    fun noLessonsToday_quietAllDay() {
        for (minute in intArrayOf(6 * 60, 10 * 60, 15 * 60, 20 * 60, 23 * 60)) {
            assertTrue("minute=$minute", BellCountdownNotifier.isQuietWindow(minute, null))
        }
    }

    @Test
    fun collegeMode_lastBellCutoffIs1935() {
        val bells = CollegeBellSchedule.getBellsForDay(1)
        val end = bells.last { !it.isInfoHour }.endMinutes
        assertEquals(19 * 60 + 35, end)

        assertFalse(BellCountdownNotifier.isQuietWindow(18 * 60, end))
        assertTrue(BellCountdownNotifier.isQuietWindow(end + 1, end))

        assertTrue(BellCountdownNotifier.isQuietWindow(23 * 60, end))
        assertFalse(BellCountdownNotifier.isQuietWindow(6 * 60, end))
    }

    @Test
    fun morningWindow_crossesMidnightIntoNextDay() {

        var quiet = 0
        var visible = 0
        for (minute in 0 until 24 * 60) {
            if (BellCountdownNotifier.isQuietWindow(minute, endOfLessons)) quiet++ else visible++
        }
        assertEquals(resume + (24 * 60 - (endOfLessons + 1)), quiet)
        assertEquals(endOfLessons + 1 - resume, visible)
    }
}
