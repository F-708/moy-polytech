package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.politex.util.BellTimerService
import com.politex.widget.WidgetAlarm
import com.politex.widget.WidgetUpdateHelper
import java.util.concurrent.TimeUnit

class WidgetSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        runCatching { WidgetAlarm.scheduleNext(ctx) }
        runCatching { WidgetAlarm.scheduleNextSecond(ctx) }
        runCatching { BellTimerService.sync(ctx) }
        runCatching { WidgetUpdateHelper.updateAllWidgets(ctx) }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_widget_sync"

        fun enqueue(context: Context) {
            runCatching {
                val request =
                    PeriodicWorkRequestBuilder<WidgetSyncWorker>(15, TimeUnit.MINUTES).build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            }
        }
    }
}
