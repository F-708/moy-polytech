package com.politex

import com.politex.ui.screens.DeleteArmState
import com.politex.ui.screens.gradeDeleteAction
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeDeleteActionTest {

    private fun doubleTapDeleteState(tapCount: Int, withinTimeout: Boolean): DeleteArmState {
        var state = DeleteArmState.NONE
        if (tapCount >= 1) state = gradeDeleteAction(state, tapped = true, expired = false)
        if (tapCount >= 2) {
            if (!withinTimeout) state = gradeDeleteAction(state, tapped = false, expired = true)
            state = gradeDeleteAction(state, tapped = true, expired = false)
        }
        return state
    }

    @Test
    fun `первый тап взводит чип`() {
        assertEquals(DeleteArmState.ARMED, gradeDeleteAction(DeleteArmState.NONE, tapped = true, expired = false))
    }

    @Test
    fun `второй тап в окне удаляет`() {
        assertEquals(DeleteArmState.DELETED, gradeDeleteAction(DeleteArmState.ARMED, tapped = true, expired = false))
    }

    @Test
    fun `таймаут снимает взвод`() {
        assertEquals(DeleteArmState.NONE, gradeDeleteAction(DeleteArmState.ARMED, tapped = false, expired = true))
    }

    @Test
    fun `тап после таймаута снова только взводит`() {
        val expired = gradeDeleteAction(DeleteArmState.ARMED, tapped = false, expired = true)
        assertEquals(DeleteArmState.ARMED, gradeDeleteAction(expired, tapped = true, expired = false))
    }

    @Test
    fun `без событий состояние не меняется`() {
        assertEquals(DeleteArmState.NONE, gradeDeleteAction(DeleteArmState.NONE, tapped = false, expired = false))
        assertEquals(DeleteArmState.ARMED, gradeDeleteAction(DeleteArmState.ARMED, tapped = false, expired = false))
    }

    @Test
    fun `expired важнее tapped`() {
        assertEquals(DeleteArmState.NONE, gradeDeleteAction(DeleteArmState.ARMED, tapped = true, expired = true))
    }

    @Test
    fun `спека - 1 тап ARMED, 2 в окне DELETED, 2 после окна ARMED`() {
        assertEquals(DeleteArmState.ARMED, doubleTapDeleteState(tapCount = 1, withinTimeout = true))
        assertEquals(DeleteArmState.DELETED, doubleTapDeleteState(tapCount = 2, withinTimeout = true))
        assertEquals(DeleteArmState.ARMED, doubleTapDeleteState(tapCount = 2, withinTimeout = false))
    }
}
