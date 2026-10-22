package com.politex.util

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.WidgetAlarm
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        BellCountdownNotifier.createChannel(this)

        try {
            startForeground(BellCountdownNotifier.NOTIFICATION_ID, BellCountdownNotifier.buildNotification(this))
        } catch (e: Exception) {
            android.util.Log.w("BellTimerService", "startForeground rejected in onCreate", e)
            isRunning = false
            stopSelf()
            return
        }
        isRunning = true
        runCatching { WidgetAlarm.cancelSecond(this) }
        startTicking()
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
                        val style = BellCountdownNotifier.getStyle(this@BellTimerService)
                        val secondMode =
                            WidgetUpdateHelper.needSecondTick(
                                WidgetUpdateHelper.hasSecondsWidgets(this@BellTimerService),
                                style,
                            )
                        val nowMs = System.currentTimeMillis()

                        delay(if (secondMode) 1000L - nowMs % 1000L else 60_000L - nowMs % 60_000L)

                        val nowAfter = System.currentTimeMillis()
                        val minuteBoundary = isMinuteBoundary(nowAfter, lastMinute)
                        if (minuteBoundary) lastMinute = nowAfter / 60_000L

                        if (minuteBoundary || style == BellCountdownNotifier.Style.WITH_SECONDS) {
                            try {
                                startForeground(
                                    BellCountdownNotifier.NOTIFICATION_ID,
                                    BellCountdownNotifier.buildNotification(this@BellTimerService),
                                )
                            } catch (_: Exception) {
                                BellCountdownNotifier.show(this@BellTimerService)
                            }
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
