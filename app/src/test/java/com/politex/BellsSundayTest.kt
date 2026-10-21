package com.politex

import com.politex.data.model.BellScheduleType
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.screens.bellsForDisplay
import com.politex.ui.screens.indicatorVisible
import com.politex.ui.screens.sundayNoteVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BellsSundayTest {
    @Test
    fun `BB5 sunday with today type still shows standard bells (inactive, not empty)`() {
        assertEquals(CollegeBellSchedule.STANDARD_BELLS, bellsForDisplay(BellScheduleType.STANDARD))
        assertTrue(bellsForDisplay(BellScheduleType.STANDARD).isNotEmpty())
    }

    @Test
    fun `BB5 sunday note visible only on sunday with today type selected`() {
        assertTrue(sundayNoteVisible(todayDow = 7, isTodaySelected = true))
        assertFalse(sundayNoteVisible(todayDow = 7, isTodaySelected = false))
        assertFalse(sundayNoteVisible(todayDow = 3, isTodaySelected = true))
        assertFalse(sundayNoteVisible(todayDow = 3, isTodaySelected = false))
    }

    @Test
    fun `BB5 other types unchanged`() {
        assertEquals(CollegeBellSchedule.THURSDAY_BELLS, bellsForDisplay(BellScheduleType.THURSDAY))
        assertEquals(CollegeBellSchedule.SATURDAY_BELLS, bellsForDisplay(BellScheduleType.SATURDAY))
    }

    @Test
    fun `CC2 today indicator hidden on sunday, visible on other days`() {
        assertFalse(indicatorVisible(todayDow = 7))
        (1..6).forEach { dow -> assertTrue("dow=$dow", indicatorVisible(todayDow = dow)) }
    }
}
