package com.politex.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.politex.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31, 33, 35])
class WidgetRemoteViewsCompatTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private val shadowAwm
        get() = shadowOf(AppWidgetManager.getInstance(ctx))

    @Before
    fun setUp() {
        WidgetUpdateHelper.getPrefs(ctx).edit().clear().apply()
        WidgetData.invalidateCache()
        WidgetUpdateHelper.invalidateSecondsCache()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
        TeacherWidgetData.invalidate()
    }

    private fun bind(
        id: Int,
        provider: Class<*>,
    ) {
        shadowAwm.bindAppWidgetId(id, ComponentName(ctx, provider))
    }

    @Test
    fun `каждый widget-layout надувается через RemoteViews apply без падения`() {
        val layouts = listOf(
            R.layout.widget_bell_countdown,
            R.layout.widget_now_next,
            R.layout.widget_bells,
            R.layout.widget_today_schedule,
            R.layout.widget_teacher_today,
            R.layout.widget_teacher_next,
            R.layout.widget_row_list,
            R.layout.widget_row_bell,
            R.layout.widget_fallback,
        )
        layouts.forEach { layoutId ->
            val root = RemoteViews(ctx.packageName, layoutId).apply(ctx, null)
            assertNotNull("layout $layoutId не надулся в RemoteViews", root)
        }
    }

    @Test
    fun `countdown - updateAll рисует виджет`() {
        bind(101, BellCountdownWidgetProvider::class.java)
        BellCountdownWidgetProvider.updateAll(ctx)
        assertNotNull(shadowAwm.getViewFor(101))
    }

    @Test
    fun `now-next - updateAll рисует виджет`() {
        bind(102, NowNextWidgetProvider::class.java)
        NowNextWidgetProvider.updateAll(ctx)
        val root = shadowAwm.getViewFor(102)
        assertNotNull(root)
        assertNotNull(root!!.findViewById<TextView>(R.id.tv_now_label))
    }

    @Test
    fun `today - updateAll рисует виджет`() {
        bind(103, TodayScheduleWidgetProvider::class.java)
        TodayScheduleWidgetProvider.updateAll(ctx)
        assertNotNull(shadowAwm.getViewFor(103))
    }

    @Test
    fun `bells - updateAll рисует виджет со строками звонков`() {
        bind(104, BellsWidgetProvider::class.java)
        BellsWidgetProvider.updateAll(ctx)
        val root = shadowAwm.getViewFor(104)
        assertNotNull(root)
        val rows = root!!.findViewById<LinearLayout>(R.id.bells_rows)
        assertTrue("в виджете звонков должны быть строки", rows.childCount > 0)
    }

    @Test
    fun `teacher today - updateAll рисует виджет без профиля`() = runBlocking {
        bind(105, TeacherTodayWidgetProvider::class.java)
        TeacherTodayWidgetProvider.updateAll(ctx)
        assertNotNull(shadowAwm.getViewFor(105))
    }

    @Test
    fun `teacher next - updateAll рисует виджет без профиля`() = runBlocking {
        bind(106, TeacherNextWidgetProvider::class.java)
        TeacherNextWidgetProvider.updateAll(ctx)
        assertNotNull(shadowAwm.getViewFor(106))
    }

    @Test
    fun `applyOrFallback без views показывает минимальный фалбэк с заголовком и временем`() {
        bind(107, BellCountdownWidgetProvider::class.java)
        WidgetUpdateHelper.applyOrFallback(
            ctx,
            AppWidgetManager.getInstance(ctx),
            107,
            BellCountdownWidgetProvider.FALLBACK_TITLE,
            null,
        )
        val root = shadowAwm.getViewFor(107)
        assertNotNull("фалбэк обязан применяться вместо пустого виджета", root)
        assertEquals(
            BellCountdownWidgetProvider.FALLBACK_TITLE,
            root!!.findViewById<TextView>(R.id.tv_fallback_title).text.toString(),
        )
        val value = root.findViewById<TextView>(R.id.tv_fallback_value).text.toString()
        assertTrue("фалбэк обязан показывать время HH:mm, получено «$value»", value.matches(Regex("\\d{2}:\\d{2}")))
        assertEquals(View.VISIBLE, root.findViewById<TextView>(R.id.tv_fallback_value).visibility)
    }

    @Test
    fun `applyOrFallback с валидными views не подменяет их фалбэком`() {
        bind(108, BellCountdownWidgetProvider::class.java)
        val views = RemoteViews(ctx.packageName, R.layout.widget_bell_countdown)
        views.setTextViewText(R.id.tv_countdown_label, "маркер")
        WidgetUpdateHelper.applyOrFallback(ctx, AppWidgetManager.getInstance(ctx), 108, "X", views)
        val root = shadowAwm.getViewFor(108)
        assertNotNull(root)
        assertEquals("маркер", root!!.findViewById<TextView>(R.id.tv_countdown_label).text.toString())
    }

    @Test
    fun `openAppIntent создаёт PendingIntent с FLAG_IMMUTABLE`() {
        val pi = WidgetUpdateHelper.openAppIntent(ctx, 4_321, "SCHEDULE")
        assertNotNull(pi)
        assertTrue("на S+ без явного флага мутабельности PendingIntent не создаётся", shadowOf(pi).isImmutable)
        assertTrue(shadowOf(pi).flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    fun `будильники виджетов планируются PendingIntent с FLAG_IMMUTABLE`() {
        WidgetAlarm.scheduleNextIf(ctx, hasWork = true)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = shadowOf(am).scheduledAlarms.firstOrNull()
        assertNotNull(alarm)
        assertTrue("тик-будильник обязан быть IMMUTABLE", shadowOf(alarm!!.operation).isImmutable)
    }
}
