package com.politex

import com.politex.worker.SyncDecision
import com.politex.worker.shouldTeacherSync
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TeacherScheduleWorkerTest {
    private fun hm(h: Int, m: Int) = h * 60 + m

    @Test
    fun `06-40 weekday is MORNING_FORCE`() {
        assertEquals(SyncDecision.MORNING_FORCE, shouldTeacherSync(hm(6, 40), Calendar.MONDAY))
    }

    @Test
    fun `07-20 is SKIP before 10-00`() {
        assertEquals(SyncDecision.SKIP, shouldTeacherSync(hm(7, 20), Calendar.TUESDAY))
    }

    @Test
    fun `14-00 is PERIODIC`() {
        assertEquals(SyncDecision.PERIODIC, shouldTeacherSync(hm(14, 0), Calendar.WEDNESDAY))
    }

    @Test
    fun `21-30 is SKIP`() {
        assertEquals(SyncDecision.SKIP, shouldTeacherSync(hm(21, 30), Calendar.THURSDAY))
    }

    @Test
    fun `saturday behaves like weekday`() {
        assertEquals(SyncDecision.MORNING_FORCE, shouldTeacherSync(hm(6, 40), Calendar.SATURDAY))
        assertEquals(SyncDecision.PERIODIC, shouldTeacherSync(hm(14, 0), Calendar.SATURDAY))
    }

    @Test
    fun `sunday is always SKIP`() {
        assertEquals(SyncDecision.SKIP, shouldTeacherSync(hm(6, 40), Calendar.SUNDAY))
        assertEquals(SyncDecision.SKIP, shouldTeacherSync(hm(14, 0), Calendar.SUNDAY))
    }

    @Test
    fun `window boundaries inclusive`() {
        assertEquals(SyncDecision.MORNING_FORCE, shouldTeacherSync(hm(6, 30), Calendar.FRIDAY))
        assertEquals(SyncDecision.MORNING_FORCE, shouldTeacherSync(hm(7, 15), Calendar.FRIDAY))
        assertEquals(SyncDecision.SKIP, shouldTeacherSync(hm(7, 16), Calendar.FRIDAY))
        assertEquals(SyncDecision.PERIODIC, shouldTeacherSync(hm(10, 0), Calendar.FRIDAY))
        assertEquals(SyncDecision.PERIODIC, shouldTeacherSync(hm(21, 0), Calendar.FRIDAY))
        assertEquals(SyncDecision.SKIP, shouldTeacherSync(hm(21, 1), Calendar.FRIDAY))
    }
}
