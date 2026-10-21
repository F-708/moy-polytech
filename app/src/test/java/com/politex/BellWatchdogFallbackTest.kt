package com.politex

import com.politex.worker.BellServiceWatchdogWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BellWatchdogFallbackTest {
    @Test
    fun `failed FGS start on Android 12+ schedules fallback alarm`() {
        assertTrue(BellServiceWatchdogWorker.shouldScheduleFallback(startFailed = true, sdkInt = 34))
    }

    @Test
    fun `failed start on Android 8-11 also schedules alarm (background start restricted since O)`() {
        assertTrue(BellServiceWatchdogWorker.shouldScheduleFallback(startFailed = true, sdkInt = 28))
    }

    @Test
    fun `below API 26 there is no background-start restriction - no alarm`() {
        assertFalse(BellServiceWatchdogWorker.shouldScheduleFallback(startFailed = true, sdkInt = 25))
    }

    @Test
    fun `successful start needs no fallback`() {
        assertFalse(BellServiceWatchdogWorker.shouldScheduleFallback(startFailed = false, sdkInt = 34))
        assertFalse(BellServiceWatchdogWorker.shouldScheduleFallback(startFailed = false, sdkInt = 24))
    }
}
