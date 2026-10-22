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

    private lateinit var realTz: TimeZone

    private val shadowAm
        get() = shadowOf(ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    private val shadowAwm
        get() = shadowOf(AppWidgetManager.getInstance(ctx))

    @Before
    fun setUp() {
        realTz = TimeZone.getDefault()
        freezeAtTuesday1030(realTz)
        ShadowAlarmManager.reset()
        WidgetUpdateHelper.getPrefs(ctx).edit().clear().apply()
        WidgetUpdateHelper.invalidateSecondsCache()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
        WidgetData.invalidateCache()
    }

    @After
    fun tearDown() {
        WidgetUpdateHelper.invalidateSecondsCache()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
        WidgetData.invalidateCache()
        TimeZone.setDefault(realTz)
    }

    private fun alarmFor(action: String) =
        shadowAm.scheduledAlarms.firstOrNull {
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
        assertTrue(WidgetUpdateHelper.hasSecondsWidgets(ctx))

        WidgetAlarm.scheduleNextSecond(ctx)

        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))
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
    fun `минутный тик без строки обновляет виджеты и перепланирует оба будильника`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)

        WidgetAlarmReceiver().onReceive(ctx, Intent(WidgetAlarmReceiver.ACTION_TICK))
        drainWidgetScope()

        assertNotNull("виджет перерисован минутным тиком", countdownLabel(7))
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNotNull(alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(
            "при выключенной строке минутный тик не должен постить никаких уведомлений",
            shadowOf(nm).allNotifications.isEmpty(),
        )
    }

    @Test
    fun `boot без строки планирует тики виджетов и не стартует сервис`() = runBlocking {
        BellCountdownNotifier.setEnabled(ctx, false)
        bindSecondsWidget(7)
        val app = shadowOf(ctx.applicationContext as Application)
        app.clearStartedServices()

        WidgetAlarmReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNotNull("минутный тик после перезагрузки планируется без строки", alarmFor(WidgetAlarmReceiver.ACTION_TICK))
        assertNotNull("секундный тик после перезагрузки планируется без строки", alarmFor(WidgetAlarmReceiver.ACTION_SECOND_TICK))
        assertFalse(
            "при выключенной строке boot не поднимает BellTimerService",
            bellServiceStartRequested(app),
        )

        drainWidgetScope()
        assertNotNull("виджеты обновлены после boot без строки", countdownLabel(7))
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

    private fun freezeAtTuesday1030(base: TimeZone) {
        val real = System.currentTimeMillis()
        val target =
            Calendar.getInstance(base).apply {
                timeInMillis = real
                set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        while (target.timeInMillis <= real) target.add(Calendar.DAY_OF_YEAR, 7)
        val delta = (target.timeInMillis - real).toInt()
        TimeZone.setDefault(
            object : TimeZone() {
                init {
                    @Suppress("LeakingThis")
                    id = "WIDGET_SECONDS_TICK/FROZEN"
                }

                override fun getOffset(
                    era: Int,
                    year: Int,
                    month: Int,
                    day: Int,
                    dayOfWeek: Int,
                    milliseconds: Int,
                ): Int = base.getOffset(era, year, month, day, dayOfWeek, milliseconds) + delta

                override fun getOffset(millis: Long): Int = base.getOffset(millis) + delta

                override fun setRawOffset(offsetMillis: Int) = Unit

                override fun getRawOffset(): Int = base.rawOffset + delta

                override fun useDaylightTime(): Boolean = false

                override fun inDaylightTime(date: java.util.Date): Boolean = false
            },
        )
    }
}
