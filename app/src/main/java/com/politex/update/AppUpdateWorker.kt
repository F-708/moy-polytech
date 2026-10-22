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
import com.politex.util.UpdateChannelStore
import com.politex.util.periodicCheckNeedsReschedule
import com.politex.util.updateCheckIntervalHours
import java.io.File
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val found = UpdateCheckController.checkNow(applicationContext)
        if (found is ManualCheckResult.Latest) {
            UpdateInstallController.clearPending(applicationContext)
            return Result.success()
        }
        if (found !is ManualCheckResult.UpdateFound) return Result.success()
        return processFoundUpdate(applicationContext, found.info)
    }

    companion object {
        const val WORK_NAME = "app_update_check"
        const val ONCE_WORK_NAME = "app_update_check_once"
        const val CHANNEL_ID = "mpk_app_updates"
        const val NOTIFICATION_ID = 2027
        const val NOTIFICATION_REJECTED_ID = 2028
        private const val PREFS = "mpk_update_prefs"
        private const val KEY_NOTIFIED_VERSION = "notified_version_name"
        private const val KEY_NOTIFIED_CODE = "notified_version_code"
        private const val KEY_SCHEDULED_CHANNEL = "scheduled_channel"

        internal var downloadOverride: ((Context, UpdateInfo) -> File?)? = null

        internal fun processFoundUpdate(
            context: Context,
            info: UpdateInfo,
        ): Result {
            val appContext = context.applicationContext
            if (UpdateInstallController.state.value !is InstallButtonState.Downloading) {
                UpdateInstallController.rememberFound(appContext, info)
            }
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (alreadyNotified(prefs, info)) return Result.success()
            when (UpdateInstallController.state.value) {
                is InstallButtonState.Downloading -> return Result.retry()
                is InstallButtonState.Downloaded,
                is InstallButtonState.InstallLaunched,
                -> {
                    remember(prefs, info)
                    return Result.success()
                }
                else -> Unit
            }
            val file =
                UpdateInstallController.cachedValidFile(appContext, info)
                    ?: freshDownload(appContext, info)
                    ?: return Result.retry()
            return when (val rejection = UpdateInstallController.validateFile(appContext, file)) {
                ApkRejection.NONE -> {
                    UpdateInstallController.adoptWorkerResult(appContext, info, file)
                    notifyReady(appContext, info, file)
                    remember(prefs, info)
                    Result.success()
                }
                ApkRejection.UNREADABLE -> {
                    file.delete()
                    Result.retry()
                }
                ApkRejection.VERSION -> {
                    file.delete()
                    UpdateInstallController.clearPending(appContext)
                    remember(prefs, info)
                    Result.success()
                }
                ApkRejection.PACKAGE, ApkRejection.SIGNATURE -> {
                    file.delete()
                    UpdateInstallController.clearPending(appContext)
                    notifyRejected(appContext, rejection)
                    remember(prefs, info)
                    Result.success()
                }
            }
        }

        private fun freshDownload(
            context: Context,
            info: UpdateInfo,
        ): File? {
            val file =
                downloadOverride?.invoke(context, info)
                    ?: UpdateDownloader.download(context, info.asset, info.versionName)
                    ?: return null
            if (!UpdateDownloader.matchesChecksum(file, info.sha256)) {
                file.delete()
                return null
            }
            return file
        }

        internal fun alreadyNotified(
            prefs: SharedPreferences,
            info: UpdateInfo,
        ): Boolean =
            prefs.getInt(KEY_NOTIFIED_CODE, 0) == info.versionCode ||
                prefs.getString(KEY_NOTIFIED_VERSION, null) == info.versionName

        fun schedule(context: Context) {
            if (!AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR)) return
            enqueuePeriodic(context, force = false)
        }

        fun reschedule(context: Context) {
            if (!AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR)) return
            enqueuePeriodic(context, force = true)
        }

        private fun enqueuePeriodic(
            context: Context,
            force: Boolean,
        ) {
            val channel = UpdateChannelStore.channel(context)
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val policy =
                if (force || periodicCheckNeedsReschedule(prefs.getString(KEY_SCHEDULED_CHANNEL, null), channel)) {
                    ExistingPeriodicWorkPolicy.UPDATE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }
            val request =
                PeriodicWorkRequestBuilder<AppUpdateWorker>(updateCheckIntervalHours(channel), TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .build(),
                    ).build()
            runCatching {
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, policy, request)
                prefs
                    .edit()
                    .putString(KEY_SCHEDULED_CHANNEL, channel.wire)
                    .apply()
            }
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

        internal fun remember(
            prefs: SharedPreferences,
            info: UpdateInfo,
        ) {
            prefs
                .edit()
                .putString(KEY_NOTIFIED_VERSION, info.versionName)
                .putInt(KEY_NOTIFIED_CODE, info.versionCode)
                .apply()
        }

        internal fun notifyReady(
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

        internal fun notifyRejected(
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