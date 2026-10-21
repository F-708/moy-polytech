package com.politex

import android.os.Build
import com.politex.util.ExactAlarmGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmGateTest {
    @Test
    fun `JJ3 запрос нужен на SDK 31+ когда разрешения нет и в этой сессии ещё не спрашивали`() {
        assertTrue(ExactAlarmGate.shouldRequest(Build.VERSION_CODES.S, canScheduleExact = false, askedThisSession = false))
        assertTrue(ExactAlarmGate.shouldRequest(35, canScheduleExact = false, askedThisSession = false))
    }

    @Test
    fun `JJ3 запрос не нужен когда разрешение есть или SDK ниже 31`() {
        assertFalse(ExactAlarmGate.shouldRequest(Build.VERSION_CODES.S, canScheduleExact = true, askedThisSession = false))
        assertFalse(ExactAlarmGate.shouldRequest(35, canScheduleExact = true, askedThisSession = false))
        assertFalse(ExactAlarmGate.shouldRequest(30, canScheduleExact = false, askedThisSession = false))
        assertFalse(ExactAlarmGate.shouldRequest(26, canScheduleExact = true, askedThisSession = false))
    }

    @Test
    fun `JJ3 запрос не повторяется в пределах сессии взаимодействия`() {
        assertFalse(ExactAlarmGate.shouldRequest(Build.VERSION_CODES.S, canScheduleExact = false, askedThisSession = true))
        assertFalse(ExactAlarmGate.shouldRequest(35, canScheduleExact = false, askedThisSession = true))
    }

    @Test
    fun `JJ3 hintNeeded совпадает по смыслу с shouldRequest без учёта сессии`() {
        assertTrue(ExactAlarmGate.hintNeeded(Build.VERSION_CODES.S, canScheduleExact = false))
        assertFalse(ExactAlarmGate.hintNeeded(Build.VERSION_CODES.S, canScheduleExact = true))
        assertFalse(ExactAlarmGate.hintNeeded(30, canScheduleExact = false))
    }
}
