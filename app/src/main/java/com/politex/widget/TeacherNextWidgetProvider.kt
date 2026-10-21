package com.politex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.politex.R
import kotlinx.coroutines.launch

class TeacherNextWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val pending = goAsync()
        WidgetUpdateHelper.widgetScope.launch {
            try {
                ids.forEach { id -> runCatching { manager.updateAppWidget(id, buildViews(context, id, manager)) } }
            } finally {

                WidgetAlarm.scheduleNext(context)
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle?,
    ) {

        WidgetUpdateHelper.widgetScope.launch {
            runCatching { manager.updateAppWidget(appWidgetId, buildViews(context, appWidgetId, manager)) }
        }
    }

    companion object {

        private const val COMPACT_MAX_HEIGHT_DP = 70

        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TeacherNextWidgetProvider::class.java))
            ids.forEach { id -> runCatching { manager.updateAppWidget(id, buildViews(context, id, manager)) } }
        }

        private suspend fun buildViews(
            context: Context,
            appWidgetId: Int,
            manager: AppWidgetManager? = null,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_teacher_next)
            val style = WidgetStyle.load(context, appWidgetId).colors(context)

            views.setInt(R.id.teacher_next_root, "setBackgroundColor", style.backgroundColor)

            views.setTextColor(R.id.tv_next_status, style.subColor)
            views.setTextColor(R.id.tv_next_time, style.mainColor)
            views.setTextColor(R.id.tv_next_groups, style.subColor)
            views.setTextColor(R.id.tv_next_subject, style.mainColor)
            views.setTextColor(R.id.tv_next_updated, style.subColor)
            views.setTextColor(R.id.tv_next_empty, style.mainColor)

            views.setTextColor(R.id.tv_next_room, style.mainColor)
            views.setInt(R.id.tv_next_room, "setBackgroundColor", style.highlightColor)
            views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)

            val compact =
                (manager ?: AppWidgetManager.getInstance(context))
                    .getAppWidgetOptions(appWidgetId)
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) < COMPACT_MAX_HEIGHT_DP
            views.setViewVisibility(R.id.tv_next_subject, if (compact) android.view.View.GONE else android.view.View.VISIBLE)
            views.setViewVisibility(R.id.tv_next_updated, android.view.View.VISIBLE)

            val snap = TeacherWidgetData.snapshot(context)
            if (snap == null) {
                showEmpty(views, style, "Выберите себя в приложении", compact)
            } else {
                val state = TeacherWidgetLogic.nextState(snap.lessons, WidgetData.minuteOfDay(context), snap.dow, snap.next)
                when (state) {
                    is TeacherNextState.Running -> showLesson(views, "ИДЁТ", state.lesson)
                    is TeacherNextState.NextToday -> showLesson(views, "ДАЛЬШЕ", state.lesson)
                    is TeacherNextState.EmptyToday ->
                        showEmpty(views, style, TeacherWidgetLogic.emptyText("Сегодня пар нет.", state.next), compact)
                    is TeacherNextState.DayDone ->
                        showEmpty(views, style, TeacherWidgetLogic.emptyText("Пары закончились.", state.next), compact)
                    is TeacherNextState.DayOff -> showEmpty(views, style, TeacherWidgetLogic.dayOffText(state.next), compact)
                }
                val updated = TeacherWidgetLogic.updatedLabel(snap.lastSyncEpoch)
                if (updated.isNotBlank()) views.setTextViewText(R.id.tv_next_updated, updated)
            }

            views.setOnClickPendingIntent(
                R.id.teacher_next_root,
                TeacherWidgetUpdater.openAppIntent(context, 5_000_000 + appWidgetId),
            )
            return views
        }

        private fun showLesson(
            views: RemoteViews,
            status: String,
            lesson: TeacherWidgetLesson,
        ) {
            views.setViewVisibility(R.id.ll_next_main, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.tv_next_empty, android.view.View.GONE)
            views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)
            views.setTextViewText(R.id.tv_next_status, status)
            views.setTextViewText(R.id.tv_next_time, if (lesson.start.isNotBlank()) "${lesson.start}–${lesson.end}" else "")
            views.setTextViewText(R.id.tv_next_groups, lesson.groups.joinToString(", "))
            views.setTextViewText(R.id.tv_next_subject, lesson.subject)
            if (lesson.room.isNotBlank()) {
                views.setTextViewText(R.id.tv_next_room, lesson.room)
                views.setViewVisibility(R.id.tv_next_room, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.tv_next_room, android.view.View.GONE)
            }
        }

        private fun showEmpty(
            views: RemoteViews,
            style: WidgetColors,
            text: String,
            compact: Boolean,
        ) {
            views.setViewVisibility(R.id.ll_next_main, android.view.View.GONE)
            views.setTextViewText(R.id.tv_next_empty, text)
            views.setTextColor(R.id.tv_next_empty, style.mainColor)
            views.setViewVisibility(R.id.tv_next_empty, android.view.View.VISIBLE)

            if (!compact) WidgetUpdateHelper.showIdle(views)
        }
    }
}
