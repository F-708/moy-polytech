package com.politex.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService

class BellServiceAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_SERVICE) return
        if (!BellCountdownNotifier.isEnabled(context) || BellTimerService.isRunning) return

        if (!BellTimerService.start(context)) BellTimerService.scheduleFallbackAlarm(context)
    }

    companion object {
        const val ACTION_START_SERVICE = "com.politex.worker.ACTION_START_BELL_SERVICE"
    }
}
