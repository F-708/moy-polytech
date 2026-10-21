package com.politex.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.politex.BuildConfig
import com.politex.R
import java.io.File
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val info = UpdateCheckController.checkForWorker(applicationContext) ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED_VERSION, null) == info.versionName) return Result.success()

        val file =
            UpdateDownloader.download(applicationContext, info.asset, info.versionName)
                ?: return Result.retry()

        if (!UpdateDownloader.matchesChecksum(file, info.sha256)) {
            file.delete()
            return Result.retry()
        }

        return when (val rejection = UpdateDownloader.validate(applicationContext, file)) {
            ApkRejection.NONE -> {
                notifyReady(applicationContext, info, file)
                remember(prefs, info)
                Result.success()
            }
            ApkRejection.UNREADABLE -> {
                file.delete()
                Result.retry()
            }
            ApkRejection.VERSION -> {
                file.delete()
                remember(prefs, info)
                Result.success()
            }
            ApkRejection.PACKAGE, ApkRejection.SIGNATURE -> {
                file.delete()
                notifyRejected(applicationContext, rejection)
                remember(prefs, info)
                Result.success()
            }
        }
    }

    companion object {
        const val WORK_NAME = "app_update_check"
        const val ONCE_WORK_NAME = "app_update_check_once"
        const val CHANNEL_ID = "mpk_app_updates"
        const val NOTIFICATION_ID = 2027
        const val NOTIFICATION_REJECTED_ID = 2028
        private const val PREFS = "mpk_update_prefs"
        private const val KEY_NOTIFIED_VERSION = "notified_version_name"

        fun schedule(context: Context) {
            if (!AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR)) return
            val request =
                PeriodicWorkRequestBuilder<AppUpdateWorker>(12, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .build(),
                    ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun checkOnce(context: Context) {
            if (!AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR)) return
            runCatching {
                val request =
                    OneTimeWorkRequestBuilder<AppUpdateWorker>()
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        ).build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONCE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        private fun remember(
            prefs: SharedPreferences,
            info: UpdateInfo,
        ) {
            prefs.edit().putString(KEY_NOTIFIED_VERSION, info.versionName).apply()
        }

        private fun notifyReady(
            context: Context,
            info: UpdateInfo,
            file: File,
        ) {
            if (!ensureChannel(context)) return
            val install =
                Intent(context, UpdateInstallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(UpdateInstallActivity.EXTRA_APK_PATH, file.absolutePath)
            val pending =
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    install,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val body = info.notes.ifBlank { "Обновление «Мой Политех» загружено. Нажмите, чтобы установить." }
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mpk_emblem)
                    .setContentTitle("Доступно обновление ${info.versionName}")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
            post(context, NOTIFICATION_ID, notification)
        }

        private fun notifyRejected(
            context: Context,
            rejection: ApkRejection,
        ) {
            if (!ensureChannel(context)) return
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_mpk_emblem)
                    .setContentTitle("Обновление отклонено")
                    .setContentText(UpdateDownloader.rejectionMessage(rejection))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(UpdateDownloader.rejectionMessage(rejection)))
                    .setAutoCancel(true)
                    .build()
            post(context, NOTIFICATION_REJECTED_ID, notification)
        }

        private fun ensureChannel(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        "Обновления приложения",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            return true
        }

        private fun post(
            context: Context,
            id: Int,
            notification: android.app.Notification,
        ) {
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (_: SecurityException) {
            }
        }
    }
}