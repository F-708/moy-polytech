package com.politex.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.politex.widget.WidgetUpdateHelper
import java.util.concurrent.TimeUnit

object MpkWorkManagerHelper {
    private const val CHECK_INTERVAL_MINUTES = 15L

    fun setupPeriodicScheduleCheck(context: Context) {
        val isEnabled = WidgetUpdateHelper.isNotificationEnabled(context)
        val workManager = WorkManager.getInstance(context)

        if (isEnabled) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val workRequest =
                PeriodicWorkRequestBuilder<ScheduleCheckWorker>(
                    CHECK_INTERVAL_MINUTES,
                    TimeUnit.MINUTES,
                ).setConstraints(constraints)
                    .build()

            workManager.enqueueUniquePeriodicWork(
                ScheduleCheckWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest,
            )
        } else {
            workManager.cancelUniqueWork(ScheduleCheckWorker.WORK_NAME)
        }
    }

    fun setupTeacherSync(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            PeriodicWorkRequestBuilder<TeacherScheduleWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TeacherScheduleWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelTeacherSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(TeacherScheduleWorker.WORK_NAME)
    }

    fun setupBellServiceWatchdog(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<BellServiceWatchdogWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            BellServiceWatchdogWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun setupPeriodicReminderCheck(context: Context) {
        val request =
            PeriodicWorkRequestBuilder<ReminderCheckWorker>(
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            ReminderCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
