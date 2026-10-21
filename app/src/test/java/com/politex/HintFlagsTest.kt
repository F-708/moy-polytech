package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.isDemoTask
import com.politex.ui.screens.showPulse
import com.politex.util.HintFlags
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HintFlagsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `round-trip - markDone ставит флаг, isDone читает, ключи независимы`() {
        HintFlags.resetAll(context)
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SWIPE_TASKS))
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))

        HintFlags.markDone(context, HintFlags.KEY_SWIPE_TASKS)
        assertTrue(HintFlags.isDone(context, HintFlags.KEY_SWIPE_TASKS))
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))

        HintFlags.markDone(context, HintFlags.KEY_SCHEDULE_TAPS)
        assertTrue(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))
    }

    @Test
    fun `resetAll сбрасывает оба флага`() {
        HintFlags.markDone(context, HintFlags.KEY_SWIPE_TASKS)
        HintFlags.markDone(context, HintFlags.KEY_SCHEDULE_TAPS)
        HintFlags.resetAll(context)
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SWIPE_TASKS))
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))
    }

    @Test
    fun `демо-задание - только id -1, остальное реальные задания из БД`() {
        assertTrue(isDemoTask(-1L))
        assertFalse(isDemoTask(0L))
        assertFalse(isDemoTask(1L))
        assertFalse(isDemoTask(42L))
    }

    @Test
    fun `пульс только на целевом уроке и только пока флаг не пройден`() {
        assertTrue(showPulse(flagDone = false, isTargetLesson = true))
        assertFalse(showPulse(flagDone = true, isTargetLesson = true))
        assertFalse(showPulse(flagDone = false, isTargetLesson = false))
        assertFalse(showPulse(flagDone = true, isTargetLesson = false))
    }
}
