package com.politex.util

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.politex.MainActivity
import com.politex.R
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.WidgetAlarm
import com.politex.widget.WidgetData
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BellTimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null

    private var lastMinute = -1L

    private var postedLineMode = true

    enum class TickDriver { LINE_FGS, WIDGET_FGS, LINE_FALLBACK_ALARM, ALARM_RELAY, NONE }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BellCountdownNotifier.createChannel(this)
        WidgetTickNotification.createChannel(this)

        postedLineMode = BellCountdownNotifier.isEnabled(this)
        if (!promoteToForeground()) {
            isRunning = false
            stopSelf()
            return
        }
        isRunning = true
        runCatching { WidgetAlarm.cancelSecond(this) }
        startTicking()
    }

    private fun promoteToForeground(): Boolean =
        try {
            startForeground(BellCountdownNotifier.NOTIFICATION_ID, foregroundNotification())
            true
        } catch (e: Exception) {
            android.util.Log.w("BellTimerService", "startForeground rejected in onCreate", e)
            false
        }

    private fun foregroundNotification(): android.app.Notification =
        if (BellCountdownNotifier.isEnabled(this)) {
            BellCountdownNotifier.buildNotification(this)
        } else {
            WidgetTickNotification.build(this)
        }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (tickJob?.isActive != true) startTicking()
        return START_STICKY
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob =
            scope.launch {
                while (true) {
                    try {
                        val lineOn = BellCountdownNotifier.isEnabled(this@BellTimerService)
                        val secondsWidgets = WidgetUpdateHelper.hasSecondsWidgets(this@BellTimerService)
                        val widgetTick =
                            widgetTickNeededFor(
                                secondsWidgets,
                                WidgetData.minuteOfDay(java.util.Calendar.getInstance()),
                            )
                        if (!shouldRunTickService(lineOn, widgetTick)) {
                            stopSelf()
                            return@launch
                        }

                        val style = BellCountdownNotifier.getStyle(this@BellTimerService)
                        val secondMode = WidgetUpdateHelper.needSecondTick(secondsWidgets, style)
                        val nowMs = System.currentTimeMillis()

                        delay(if (secondMode) 1000L - nowMs % 1000L else 60_000L - nowMs % 60_000L)

                        val nowAfter = System.currentTimeMillis()
                        val minuteBoundary = isMinuteBoundary(nowAfter, lastMinute)
                        if (minuteBoundary) lastMinute = nowAfter / 60_000L

                        if (lineOn != postedLineMode ||
                            (lineOn && (minuteBoundary || style == BellCountdownNotifier.Style.WITH_SECONDS))
                        ) {
                            try {
                                startForeground(BellCountdownNotifier.NOTIFICATION_ID, foregroundNotification())
                            } catch (_: Exception) {
                                if (lineOn) BellCountdownNotifier.show(this@BellTimerService)
                            }
                            postedLineMode = lineOn
                        }

                        if (secondMode) {

                            BellCountdownWidgetProvider.updateSecondsWidgets(this@BellTimerService)
                        }

                        if (minuteBoundary) {
                            BellCountdownWidgetProvider.updateAll(this@BellTimerService)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("BellTimerService", "tick iteration failed — loop kept alive", e)
                    }
                }
            }
    }

    override fun onDestroy() {
        tickJob?.cancel()

        scope.cancel()
        isRunning = false
        runCatching { WidgetAlarm.scheduleNextSecond(this) }
        super.onDestroy()
    }

    companion object {

        @Volatile
        var isRunning: Boolean = false

        private const val FALLBACK_REQUEST_CODE = 0xBE11

        internal fun isMinuteBoundary(nowMs: Long, lastMinute: Long): Boolean = nowMs / 60_000L != lastMinute

        internal fun shouldRunTickService(
            bellLineEnabled: Boolean,
            widgetTickNeeded: Boolean,
        ): Boolean = bellLineEnabled || widgetTickNeeded

        internal fun widgetTickNeededFor(
            hasSecondsWidgets: Boolean,
            minutesOfDay: Int,
        ): Boolean = hasSecondsWidgets && WidgetAlarm.inSchoolWindow(minutesOfDay)

        internal fun tickDriverFor(
            bellLineEnabled: Boolean,
            widgetTickNeeded: Boolean,
            serviceRunning: Boolean,
            fgsStartAllowed: Boolean,
        ): TickDriver =
            when {
                serviceRunning -> TickDriver.NONE
                bellLineEnabled -> if (fgsStartAllowed) TickDriver.LINE_FGS else TickDriver.LINE_FALLBACK_ALARM
                widgetTickNeeded -> if (fgsStartAllowed) TickDriver.WIDGET_FGS else TickDriver.ALARM_RELAY
                else -> TickDriver.NONE
            }

        fun widgetTickNeeded(context: Context): Boolean =
            widgetTickNeededFor(
                WidgetUpdateHelper.hasSecondsWidgets(context),
                WidgetData.minuteOfDay(java.util.Calendar.getInstance()),
            )

        fun sync(context: Context): Boolean {
            val lineOn = BellCountdownNotifier.isEnabled(context)
            val widgetTick = widgetTickNeeded(context)
            if (!shouldRunTickService(lineOn, widgetTick)) {
                stop(context)
                return false
            }
            if (!start(context)) {
                when (tickDriverFor(lineOn, widgetTick, serviceRunning = false, fgsStartAllowed = false)) {
                    TickDriver.LINE_FALLBACK_ALARM -> scheduleFallbackAlarm(context)
                    TickDriver.ALARM_RELAY -> runCatching { WidgetAlarm.scheduleNextSecond(context) }
                    else -> Unit
                }
            }
            return true
        }

        fun start(context: Context): Boolean {
            val intent = Intent(context, BellTimerService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                val fgsBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                android.util.Log.w(
                    "BellTimerService",
                    if (fgsBlocked) "FGS start not allowed from background — caller should schedule fallback alarm" else "start failed",
                    e,
                )
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, BellTimerService::class.java))
            } catch (_: Exception) {
            }
        }

        fun scheduleFallbackAlarm(context: Context) {
            val pi = PendingIntent.getBroadcast(
                context,
                FALLBACK_REQUEST_CODE,
                Intent(context, com.politex.worker.BellServiceAlarmReceiver::class.java)
                    .setAction(com.politex.worker.BellServiceAlarmReceiver.ACTION_START_SERVICE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val now = System.currentTimeMillis()
            WidgetUpdateHelper.setExactOrIdle(context, now + (60_000L - now % 60_000L), pi)
        }
    }
}

internal object WidgetTickNotification {
    const val CHANNEL_ID = "mpk_widget_tick"

    private const val CONTENT_REQUEST_CODE = 0xBE12

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Виджеты",
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = "Посекундное обновление виджетов на домашнем экране"
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                    setSound(null, null)
                }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }

    fun build(context: Context): android.app.Notification {
        createChannel(context)
        val openIntent =
            PendingIntent.getActivity(
                context,
                CONTENT_REQUEST_CODE,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return androidx.core.app.NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mpk_emblem)
            .setContentIntent(openIntent)
            .setContentTitle("Виджеты обновляются")
            .setContentText("Секундный отсчёт на домашнем экране")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build()
    }
}
