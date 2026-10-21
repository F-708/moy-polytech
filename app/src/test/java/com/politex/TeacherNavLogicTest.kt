package com.politex

import com.politex.ui.teacher.TeacherNavLogic
import com.politex.ui.teacher.TeacherTab
import org.junit.Assert.assertEquals
import org.junit.Test

class TeacherNavLogicTest {
    @Test
    fun `deep-link extra maps to tab`() {
        assertEquals(TeacherTab.SCHEDULE, TeacherNavLogic.tabForExtra("schedule"))
        assertEquals(TeacherTab.BELLS, TeacherNavLogic.tabForExtra("bells"))
        assertEquals(TeacherTab.MORE, TeacherNavLogic.tabForExtra("more"))
    }

    @Test
    fun `legacy widget tab strings still resolve`() {

        assertEquals(TeacherTab.SCHEDULE, TeacherNavLogic.tabForExtra("today"))
        assertEquals(TeacherTab.BELLS, TeacherNavLogic.tabForExtra("week"))
    }

    @Test
    fun `unknown or null extra falls back to SCHEDULE (root)`() {
        assertEquals(TeacherTab.SCHEDULE, TeacherNavLogic.tabForExtra(null))
        assertEquals(TeacherTab.SCHEDULE, TeacherNavLogic.tabForExtra(""))
        assertEquals(TeacherTab.SCHEDULE, TeacherNavLogic.tabForExtra("garbage"))
    }

    @Test
    fun `case and whitespace tolerant`() {
        assertEquals(TeacherTab.BELLS, TeacherNavLogic.tabForExtra("BELLS"))
        assertEquals(TeacherTab.MORE, TeacherNavLogic.tabForExtra("  More "))
    }
}
