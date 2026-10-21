package com.politex

import com.politex.data.model.CollegeBellSchedule
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TomorrowLessonWidgetTest {
    private val standard = CollegeBellSchedule.getBellsForDay(1)

    @Test
    fun eveningAfterGroupLessons_showsTomorrowMorning() {

        val info =
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertEquals("до 1 урока завтра", info?.second)

        assertEquals(735L, info?.first)
    }

    @Test
    fun eveningAfterCollegeBells_showsTomorrowMorning() {

        val info =
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                19 * 60 + 36,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            )
        assertEquals("до 1 урока завтра", info?.second)

        assertEquals(759L, info?.first)
    }

    @Test
    fun saturdayEvening_countsToMonday() {

        val info =
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                6,
                CollegeBellSchedule.getBellsForDay(6),
                CountdownMode.GROUP_LESSONS,
                8,
            )
        assertEquals("до 1 урока в пн", info?.second)
        assertEquals(2175L, info?.first)
    }

    @Test
    fun beforeLessonsEnd_null() {

        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                13 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun morningGate07_null() {
        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                6 * 60 + 59,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun sunday_showsMondayCountdown() {

        val info =
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                7,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertEquals("до 1 урока в пн", info?.second)
        assertEquals(735L, info?.first)
    }

    @Test
    fun sunday_before07_null() {
        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                6 * 60 + 59,
                7,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun noGroupLessonsToday_null() {

        assertNull(
            BellCountdownWidgetProvider.tomorrowFirstLessonLabel(
                20 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                null,
            ),
        )
    }

    @Test
    fun night_at_00_24_showsFirstLessonToday() {

        val info =
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                24,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertEquals("до 1 урока", info?.second)

        assertEquals(471L, info?.first)
    }

    @Test
    fun night_at_00_09_displayedAsMinutes() {
        val info =
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                9,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            )
        assertEquals("до 1 урока", info?.second)
    }

    @Test
    fun night_after07_null() {

        assertNull(
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                7 * 60,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun night_sunday_null() {
        assertNull(
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                30,
                7,
                CollegeBellSchedule.getBellsForDay(6),
                CountdownMode.GROUP_LESSONS,
                6,
            ),
        )
    }

    @Test
    fun night_noGroupLessonsToday_null() {

        assertNull(
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                30,
                1,
                standard,
                CountdownMode.GROUP_LESSONS,
                null,
            ),
        )
    }

    @Test
    fun night_collegeMode_worksWithoutGroupLessons() {

        val info =
            BellCountdownWidgetProvider.nightFirstLessonLabel(
                30,
                1,
                standard,
                CountdownMode.ALL_BELLS,
                null,
            )
        assertEquals("до 1 урока", info?.second)
    }

    @Test
    fun hoursRounding() {

        assertEquals("12 ч", BellCountdownWidgetProvider.formatHoursRounded(735L))
        assertEquals("13 ч", BellCountdownWidgetProvider.formatHoursRounded(759L))
        assertEquals("1 ч", BellCountdownWidgetProvider.formatHoursRounded(25L))
    }
}
