package com.politex.widget

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.politex.R
import com.politex.util.BellCountdownNotifier
import com.politex.util.BellTimerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetSecondsTickTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private val shadowAm
        get() = shadowOf(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    private val shadowAwm
        get() = shadowOf(AppWidgetManager.getInstance(ctx))

    @Before
    fun setUp() {
        WidgetData.calendarOverride = { fixedTuesday1030() }
        ShadowAlarmManager.reset()
        WidgetUpdateHelper.getPrefs(ctx).edit().clear().apply()
        WidgetUpdateHelper.invalidateSecondsCache()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
        WidgetData.invalidateCache()
    }

    @After
    fun tearDown() {
        WidgetData.calendarOverride = null
        WidgetUpdateHelper.invalidateSecondsCache()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
        WidgetData.invalidateCache()
    }

    private fun fixedCalendar(
        hour: Int,
        minute: Int,
    ): Calendar = Calendar
        .getInstance(TimeZone.getTimeZone("UTC"))
        .apply {
            clear()
            set(2026, Calendar.OCTOBER, 20, hour, minute, 0)
        }

    private fun fixedTuesday1030(): Calendar = fixedCalendar(10, 30)

    private fun alarmFor(action: String) = shadowAm.scheduledAlarms.firstOrNull {
        shadowOf(it.operation).savedIntent?.action == action
    }

    private fun bindSecondsWidget(id: Int) {
        shadowAwm.bindAppWidgetId(id, ComponentName(ctx, BellCountdownWidgetProvider::class.java))
        WidgetUpdateHelper
            .getPrefs(ctx)
            .edit()
            .putBoolean("widget_seconds_$id", true)
            .apply()
        WidgetUpdateHelper.invalidateSecondsCache()
    }

    private suspend fun drainWidgetScope() {
        WidgetUpdateHelper.widgetScope.coroutineContext[Job]!!.children.toList().forEach { it.join() }
    }

    private fun countdownLabel(widgetId: Int): String? {
        val root = runCatching { shadowAwm.getViewFor(widgetId) }.getOrNull() ?: return null
        return root.findViewById<TextView>(R.id.tv_countdown_label)?.text?.toString()
    }

    private fun bellServiceStartRequested(app: org.robolectric.shadows.ShadowApplication): Boolean {
        var started: Intent? = app.nextStartedService
        var found = false
        while (started != null) {
            if (started.component?.className == "com.politex.util.BellTimerService") found = true
            started = app.nextStartedService
        }
        return found
    }

    @Test
    fun `предикат - строка выключена, секундный виджет жив - тик планируется`() {
        BellCountdownNotifier.setEnabled(ctx, false)
        assertFalse(BellCountdownNotifier.isEnabled(ctx))
        assertTrue(
            WidgetAlarm.shouldScheduleSecondTick(
                hasSecondsWidgets = true,
                serviceRunning = false,
                minutesOfDay = 8 * 60,
            ),
        )
    }

    @Test
    fun `предикат - живой сервис строки ведёт секунды сам, будильник уступает`() {
        assertFalse(
            WidgetAlarm.shouldScheduleSecondTick(
                hasSecondsWidgets = true,
                serviceRunning = true,
                minutesOfDay = 8 * 60,
            ),
        )
    }

    @Test
    fun `предикат - нет секундных виджетов - не планировать`() {
        assertFalse(
            WidgetAlarm.shouldScheduleSecondTick(
                hasSecondsWidgets = false,
                serviceRunning = false,
                minutesOfDay = 8 * 60,
            ),
        )
    }

    @Test
    fun `предикат - границы учебного окна 7-21`() {
        assertFalse(WidgetAlarm.shouldScheduleSecondTick(true, false, 7 * 60 - 1))
        assertTrue(WidgetAlarm.shouldScheduleSecondTick(true, false, 7 * 60))
        assertTrue(WidgetAlarm.shouldScheduleSecondTick(true, false, 21 * 60 - 1))
        assertFalse(WidgetAlarm.shouldScheduleSecondTick(true, false, 21 * 60))
    }

    @Test
    fun `nextSecondTrigger выравнивает на границу следующей секунды`() {
        assertEquals(1_001_000L, WidgetAlarm.nextSecondTrigger(1_000_001L))
        assertEquals(1_001_000L, WidgetAlarm.nextSecondTrigger(1_000_999L))
        assertEquals(1_002_000L, WidgetAlarm.nextSecondTrigger(1_001_000L))
    }

    @Test
    fun `строка выключена - секундный тик всё равно ставится на AlarmManager`() {
        BellCountdownNotifier.setEnabled(ctx, false)

        WidgetAlarm.scheduleSecondTick(
            context = ctx,
            hasSecondsWidgets = true,
            serviceRunning = false,
            minutesOfDay = 9 * 60,
            nowMs = 1_000_500L,
        )

        val alarm = alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK)
        assertNotNull("секундный тик виджетов не обязан зависеть от строки уведомления", alarm)
        assertEquals(1_001_000L, alarm!!.triggerAtTime)
    }

    @Test
    fun `включение строки - живой сервис отменяет будильник секунд`() {
        WidgetAlarm.scheduleSecondTick(ctx, true, false, 9 * 60, 1_000_500L)
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))

        WidgetAlarm.scheduleSecondTick(ctx, true, serviceRunning = true, 9 * 60, 1_001_500L)
        assertNull(alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))
    }

    @Test
    fun `публичный scheduleNextSecond без строки планирует тик по реальным виджетам`() {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)
        assertTrue(tickDiagnostics(), WidgetUpdateHelper.hasSecondsWidgets(ctx))

        WidgetAlarm.scheduleNextSecond(ctx)

        assertNotNull(tickDiagnostics(), alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))
    }

    @Test
    fun `минутный тик виджетов планируется без строки уведомления`() {
        BellCountdownNotifier.setEnabled(ctx, false)

        WidgetAlarm.scheduleNextIf(ctx, hasWork = true)

        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_TICK))
    }

    @Test
    fun `cancelIfNoWork убирает минутный и секундный тики`() {
        WidgetAlarm.scheduleNextIf(ctx, hasWork = true)
        WidgetAlarm.scheduleSecondTick(ctx, true, false, 9 * 60, 1_000_500L)
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))

        WidgetAlarm.cancelIfNoWork(ctx)

        assertNull(alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNull(alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))
    }

    @Test
    fun `receiver обновляет секундный виджет без строки и не постит уведомление`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)

        WidgetAlarmReceiver.handleSecondTick(ctx)

        val label = countdownLabel(7)
        assertNotNull("виджет обязан перерисоваться без строки уведомления", label)
        assertTrue(label!!.isNotBlank())

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(
            "при выключенной строке тик-ресивер не должен постить никаких уведомлений",
            shadowOf(nm).allNotifications.isEmpty(),
        )
    }

    @Test
    fun `минутный тик без строки поднимает виджет-сервис, обновляет виджеты и перепланирует эстафету`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()

        WidgetAlarmReceiver().onReceive(ctx, Intent(WidgetAlarmReceiver.ACTION_TICK))
        drainWidgetScope()

        assertNotNull("виджет перерисован минутным тиком", countdownLabel(7))
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNotNull(tickDiagnostics(), alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))
        assertTrue(
            "минутный тик обязан поднимать widget-only сервис как основной драйвер секунд; ${tickDiagnostics()}",
            bellServiceStartRequested(app),
        )

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(
            "при выключенной строке минутный тик не должен постить никаких уведомлений",
            shadowOf(nm).allNotifications.isEmpty(),
        )
    }

    @Test
    fun `boot без строки, но с seconds-виджетом - поднимает виджет-сервис и планирует тики`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()

        WidgetAlarmReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNotNull("минутный тик после перезагрузки планируется без строки", alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNotNull("после перезагрузки утренний wake вооружён", alarmFor(WidgetAlarmReceiver.ACTION_WINDOW_WAKE))
        assertTrue(
            "после перезагрузки widget-only сервис - основной драйвер секунд; ${tickDiagnostics()}",
            bellServiceStartRequested(app),
        )

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(
            "boot без строки не постит уведомление «до звонка»",
            shadowOf(nm).allNotifications.isEmpty(),
        )

        drainWidgetScope()
        assertNotNull("виджеты обновлены после boot без строки", countdownLabel(7))
    }

    @Test
    fun `boot без строки и без seconds-виджетов - сервис не стартует, секундная эстафета не планируется`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, false)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()

        WidgetAlarmReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertFalse(
            "без строки и без seconds-виджетов сервис не нужен",
            bellServiceStartRequested(app),
        )
        assertNull(alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))

        drainWidgetScope()
    }

    @Test
    fun `boot со включённой строкой стартует сервис`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, true)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()

        WidgetAlarmReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertTrue(
            "при включённой строке boot обязан поднять сервис",
            bellServiceStartRequested(app),
        )

        drainWidgetScope()
    }

    @Test
    fun `драйвер - строка включена - LINE_FGS независимо от виджетов`() {
        assertEquals(
            BellTimerService.TickDriver.LINE_FGS,
            BellTimerService.tickDriverFor(
                bellLineEnabled = true,
                widgetTickNeeded = true,
                serviceRunning = false,
                fgsStartAllowed = true,
            ),
        )
        assertEquals(
            BellTimerService.TickDriver.LINE_FGS,
            BellTimerService.tickDriverFor(
                bellLineEnabled = true,
                widgetTickNeeded = false,
                serviceRunning = false,
                fgsStartAllowed = true,
            ),
        )
    }

    @Test
    fun `драйвер - строка выключена, секундный виджет в окне - WIDGET_FGS`() {
        assertEquals(
            BellTimerService.TickDriver.WIDGET_FGS,
            BellTimerService.tickDriverFor(
                bellLineEnabled = false,
                widgetTickNeeded = true,
                serviceRunning = false,
                fgsStartAllowed = true,
            ),
        )
    }

    @Test
    fun `драйвер - FGS из фона запрещён - фолбэк на алармы`() {
        assertEquals(
            BellTimerService.TickDriver.ALARM_RELAY,
            BellTimerService.tickDriverFor(
                bellLineEnabled = false,
                widgetTickNeeded = true,
                serviceRunning = false,
                fgsStartAllowed = false,
            ),
        )
        assertEquals(
            BellTimerService.TickDriver.LINE_FALLBACK_ALARM,
            BellTimerService.tickDriverFor(
                bellLineEnabled = true,
                widgetTickNeeded = false,
                serviceRunning = false,
                fgsStartAllowed = false,
            ),
        )
    }

    @Test
    fun `драйвер - сервис уже жив или ничего не нужно - NONE`() {
        assertEquals(
            BellTimerService.TickDriver.NONE,
            BellTimerService.tickDriverFor(false, true, serviceRunning = true, fgsStartAllowed = true),
        )
        assertEquals(
            BellTimerService.TickDriver.NONE,
            BellTimerService.tickDriverFor(false, false, serviceRunning = false, fgsStartAllowed = true),
        )
    }

    @Test
    fun `shouldRunTickService и widgetTickNeededFor - строка, виджеты и окно`() {
        assertTrue(BellTimerService.shouldRunTickService(bellLineEnabled = true, widgetTickNeeded = false))
        assertTrue(BellTimerService.shouldRunTickService(bellLineEnabled = false, widgetTickNeeded = true))
        assertFalse(BellTimerService.shouldRunTickService(bellLineEnabled = false, widgetTickNeeded = false))

        assertTrue(BellTimerService.widgetTickNeededFor(true, 10 * 60))
        assertFalse("вне окна секундному тику нечего обновлять", BellTimerService.widgetTickNeededFor(true, 21 * 60))
        assertFalse(BellTimerService.widgetTickNeededFor(true, 6 * 60 + 59))
        assertFalse(BellTimerService.widgetTickNeededFor(false, 10 * 60))
    }

    private fun tickDiagnostics(): String = "minuteOfDay=${WidgetData.minuteOfDay(WidgetData.nowCalendar())} tz=${TimeZone.getDefault().id} " +
        "nowMs=${System.currentTimeMillis()} hasSeconds=${WidgetUpdateHelper.hasSecondsWidgets(ctx)} " +
        "serviceRunning=${BellTimerService.isRunning} lineOn=${BellCountdownNotifier.isEnabled(ctx)}"

    @Test
    fun `sync - строка выключена, секундный виджет жив - запрашивает старт виджет-сервиса`() {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()

        assertTrue(tickDiagnostics(), BellTimerService.sync(ctx))
        assertTrue(tickDiagnostics(), bellServiceStartRequested(app))
    }

    @Test
    fun `sync - строка выключена и секундных виджетов нет - останавливает сервис`() {
        BellCountdownNotifier.setEnabled(ctx, false)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()
        while (app.nextStoppedService != null) {
        }

        assertFalse(BellTimerService.sync(ctx))
        assertNotNull("ненужный сервис обязан останавливаться", app.nextStoppedService)
        assertFalse(bellServiceStartRequested(app))
    }

    @Test
    fun `sync - строка включена - стартует независимо от виджетов`() {
        BellCountdownNotifier.setEnabled(ctx, true)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()

        assertTrue(BellTimerService.sync(ctx))
        assertTrue(bellServiceStartRequested(app))
    }

    @Test
    fun `windowWakeTrigger - ранним утром будит сегодня, вечером и ровно в 6-55 - завтра`() {
        val early = fixedCalendar(5, 0)
        val wakeToday = WidgetAlarm.windowWakeTrigger(early)
        val todayWake = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = wakeToday }
        assertEquals(6, todayWake.get(Calendar.HOUR_OF_DAY))
        assertEquals(55, todayWake.get(Calendar.MINUTE))
        assertEquals(early.get(Calendar.DAY_OF_YEAR), todayWake.get(Calendar.DAY_OF_YEAR))

        val evening = fixedCalendar(21, 30)
        val wakeTomorrow = WidgetAlarm.windowWakeTrigger(evening)
        val tomorrowWake = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = wakeTomorrow }
        assertEquals(6, tomorrowWake.get(Calendar.HOUR_OF_DAY))
        assertEquals(55, tomorrowWake.get(Calendar.MINUTE))
        assertEquals(
            evening.get(Calendar.DAY_OF_YEAR) + 1,
            tomorrowWake.get(Calendar.DAY_OF_YEAR),
        )

        val exact = fixedCalendar(6, 55)
        assertTrue(
            "ровно в 6:55 следующий wake уже завтра",
            WidgetAlarm.windowWakeTrigger(exact) > exact.timeInMillis + 23 * 3600_000L,
        )
    }

    @Test
    fun `window wake - с виджетом ставится, без виджетов нет`() {
        BellCountdownNotifier.setEnabled(ctx, false)

        WidgetAlarm.scheduleWindowWake(ctx)
        assertNull("без виджетов утренний wake не нужен", alarmFor(WidgetAlarmReceiver.ACTION_WINDOW_WAKE))

        bindSecondsWidget(7)
        WidgetAlarm.scheduleWindowWake(ctx)
        val wake = alarmFor(WidgetAlarmReceiver.ACTION_WINDOW_WAKE)
        assertNotNull("при живых виджетах утренний wake обязан стоять", wake)
        assertTrue(
            "wake планируется позже текущего (замороженного) момента",
            wake!!.triggerAtTime > fixedTuesday1030().timeInMillis,
        )
    }

    @Test
    fun `window wake уникален - повторные погасания не плодят будильники`() {
        bindSecondsWidget(7)

        repeat(3) { WidgetAlarm.scheduleWindowWake(ctx) }

        val count = shadowAm.scheduledAlarms.count {
            shadowOf(it.operation).savedIntent?.action == WidgetAlarmReceiver.ACTION_WINDOW_WAKE
        }
        assertEquals("один PendingIntent - один будильник", 1, count)
    }

    @Test
    fun `cancelWindowWake снимает утренний будильник`() {
        bindSecondsWidget(7)
        WidgetAlarm.scheduleWindowWake(ctx)
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_WINDOW_WAKE))

        WidgetAlarm.cancelWindowWake(ctx)
        assertNull(alarmFor(WidgetAlarmReceiver.ACTION_WINDOW_WAKE))
    }

    @Test
    fun `scheduleNext вместе с минутным тиком вооружает утренний wake`() {
        bindSecondsWidget(7)

        WidgetAlarm.scheduleNext(ctx)

        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_WINDOW_WAKE))
    }

    @Test
    fun `wake-тик поднимает виджет-сервис без строки и восстанавливает все цепочки`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()
        ShadowAlarmManager.reset()

        WidgetAlarmReceiver().onReceive(ctx, Intent(WidgetAlarmReceiver.ACTION_WINDOW_WAKE))
        drainWidgetScope()

        assertTrue(
            "wake обязан поднимать widget-only сервис; ${tickDiagnostics()}",
            bellServiceStartRequested(app),
        )
        assertNotNull("wake перевзводит себя на следующий день", alarmFor(WidgetAlarmReceiver.ACTION_WINDOW_WAKE))
        assertNotNull("wake восстанавливает минутную эстафету", alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNotNull("wake восстанавливает секундную эстафету", alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))
        assertNotNull("виджеты перерисованы утром", countdownLabel(7))

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(
            "wake без строки не постит уведомление «до звонка»",
            shadowOf(nm).allNotifications.isEmpty(),
        )
    }
}
