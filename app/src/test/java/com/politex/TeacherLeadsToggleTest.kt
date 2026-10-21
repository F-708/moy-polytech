package com.politex

import com.politex.ui.screens.canToggleLeads
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherLeadsToggleTest {
    @Test
    fun `авто-факт уверен и не снят — шильд НЕ кликабельна`() {
        assertFalse(canToggleLeads(autoActive = true, manualOff = false))
    }

    @Test
    fun `авто-факт уверен но пользователь снял — кликабельна для возврата`() {
        assertTrue(canToggleLeads(autoActive = true, manualOff = true))
    }

    @Test
    fun `авто-факт не активен — ручная отметка всегда доступна`() {
        assertTrue(canToggleLeads(autoActive = false, manualOff = false))
        assertTrue(canToggleLeads(autoActive = false, manualOff = true))
    }
}
