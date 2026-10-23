package com.politex.widget

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeChangedReceiverTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun isRegisteredFor(action: String): Boolean {
        val name = TimeChangedReceiver::class.java.name
        return ctx.packageManager
            .queryBroadcastReceivers(Intent(action), 0)
            .any { it.activityInfo?.name == name }
    }

    @Test
    fun `ресивер объявлен в манифесте на смену времени, даты и часового пояса`() {
        assertTrue("ACTION_TIME_CHANGED", isRegisteredFor(Intent.ACTION_TIME_CHANGED))
        assertTrue("ACTION_DATE_CHANGED", isRegisteredFor(Intent.ACTION_DATE_CHANGED))
        assertTrue("ACTION_TIMEZONE_CHANGED", isRegisteredFor(Intent.ACTION_TIMEZONE_CHANGED))
    }

    @Test
    fun `onTimeChanged перепланирует без исключений`() {
        kotlinx.coroutines.runBlocking { TimeChangedReceiver.onTimeChanged(ctx) }
    }
}
