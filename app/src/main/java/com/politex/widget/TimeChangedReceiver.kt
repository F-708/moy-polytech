package com.politex.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService
import com.politex.worker.MpkWorkManagerHelper
import kotlinx.coroutines.launch

class TimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        when (intent?.action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {

                val pending = goAsync()
                WidgetUpdateHelper.widgetScope.launch {
                    try {
                        onTimeChanged(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {

        internal suspend fun onTimeChanged(context: Context) {

            runCatching { WidgetUpdateHelper.ensureTodaySchedule(context) }

            WidgetUpdateHelper.updateAllWidgets(context)

            WidgetAlarm.scheduleNext(context)

            if (BellCountdownNotifier.isEnabled(context)) {
                BellTimerService.start(context)
            }

            try {
                MpkWorkManagerHelper.setupPeriodicScheduleCheck(context)
                MpkWorkManagerHelper.setupBellServiceWatchdog(context)
                MpkWorkManagerHelper.setupPeriodicReminderCheck(context)
            } catch (_: Exception) {
            }
        }
    }
}
