package com.politex.worker

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService
import com.politex.widget.WidgetAlarm
import com.politex.widget.WidgetUpdateHelper

class BellServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (BellCountdownNotifier.isEnabled(ctx) && !BellTimerService.isRunning) {
            val started = BellTimerService.start(ctx)

            if (shouldScheduleFallback(startFailed = !started, sdkInt = Build.VERSION.SDK_INT)) {
                BellTimerService.scheduleFallbackAlarm(ctx)
            }
        }

        runCatching { WidgetAlarm.scheduleNext(ctx) }

        runCatching { WidgetUpdateHelper.ensureTodaySchedule(ctx) }

        WidgetUpdateHelper.updateAllWidgets(ctx)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_bell_service_watchdog"

        internal fun shouldScheduleFallback(startFailed: Boolean, sdkInt: Int): Boolean = startFailed && sdkInt >= Build.VERSION_CODES.O
    }
}
