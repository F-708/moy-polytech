package com.politex

import android.os.Build
import com.politex.util.ExactAlarmGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmHintTest {
    @Test
    fun `подсказка показана на SDK 31+ без разрешения`() {
        assertTrue(ExactAlarmGate.hintNeeded(Build.VERSION_CODES.S, false))
        assertTrue(ExactAlarmGate.hintNeeded(35, false))
    }

    @Test
    fun `подсказка скрыта если разрешение есть или SDK ниже 31`() {
        assertFalse(ExactAlarmGate.hintNeeded(Build.VERSION_CODES.S, true))
        assertFalse(ExactAlarmGate.hintNeeded(35, true))
        assertFalse(ExactAlarmGate.hintNeeded(30, false))
        assertFalse(ExactAlarmGate.hintNeeded(26, true))
    }
}
