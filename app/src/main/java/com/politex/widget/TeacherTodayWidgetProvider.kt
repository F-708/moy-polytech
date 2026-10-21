package com.politex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.politex.R
import kotlinx.coroutines.launch

class TeacherTodayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        val pending = goAsync()
        WidgetUpdateHelper.widgetScope.launch {
            try {
                ids.forEach { id -> runCatching { manager.updateAppWidget(id, buildViews(context, id)) } }
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

    companion object {
        private const val MAX_ROWS = 10

        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TeacherTodayWidgetProvider::class.java))
            ids.forEach { id -> runCatching { manager.updateAppWidget(id, buildViews(context, id)) } }
        }

        private suspend fun buildViews(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_teacher_today)
            val style = WidgetStyle.load(context, appWidgetId).colors(context)

            views.setInt(R.id.teacher_today_root, "setBackgroundColor", style.backgroundColor)
            views.setTextColor(R.id.tv_teacher_today_header, style.subColor)
            views.setTextColor(R.id.tv_teacher_today_updated, style.subColor)
            views.setTextColor(R.id.tv_teacher_today_empty, style.mainColor)
            views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)

            val snap = TeacherWidgetData.snapshot(context)
            if (snap == null) {
                views.setTextViewText(R.id.tv_teacher_today_header, "МОЁ СЕГОДНЯ")
                showEmpty(views, "Выберите себя в приложении")
            } else {
                views.setTextViewText(R.id.tv_teacher_today_header, TeacherWidgetLogic.todayHeader(snap.dayOfMonth, snap.month1))
                val updated = TeacherWidgetLogic.updatedLabel(snap.lastSyncEpoch)
                views.setTextViewText(R.id.tv_teacher_today_updated, updated)
                views.setViewVisibility(
                    R.id.tv_teacher_today_updated,
                    if (updated.isBlank()) android.view.View.GONE else android.view.View.VISIBLE,
                )

                views.removeAllViews(R.id.teacher_today_rows)
                if (snap.lessons.isEmpty()) {
                    val state = TeacherWidgetLogic.nextState(snap.lessons, WidgetData.minuteOfDay(context), snap.dow, snap.next)
                    val text =
                        when (state) {
                            is TeacherNextState.DayOff -> TeacherWidgetLogic.dayOffText(state.next)
                            else -> TeacherWidgetLogic.emptyText("Сегодня пар нет.", snap.next)
                        }
                    showEmpty(views, text)
                } else {
                    views.setViewVisibility(R.id.tv_teacher_today_empty, android.view.View.GONE)
                    views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)
                    val now = WidgetData.minuteOfDay(context)
                    snap.lessons.take(MAX_ROWS).forEach { lesson ->
                        views.addView(R.id.teacher_today_rows, buildRow(context, lesson, now, style))
                    }
                    if (snap.lessons.size > MAX_ROWS) {
                        views.addView(R.id.teacher_today_rows, buildMoreRow(context, style, snap.lessons.size - MAX_ROWS))
                    }
                }
            }

            views.setOnClickPendingIntent(
                R.id.teacher_today_root,
                TeacherWidgetUpdater.openAppIntent(context, 6_000_000 + appWidgetId),
            )
            return views
        }

        private fun buildRow(
            context: Context,
            lesson: TeacherWidgetLesson,
            now: Int,
            style: WidgetColors,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)
            val isCurrent = lesson.startMinutes >= 0 && now in lesson.startMinutes..lesson.endMinutes
            val isPast = lesson.endMinutes >= 0 && now > lesson.endMinutes
            val textColor = if (isPast) style.pastColor else style.mainColor

            row.setInt(R.id.row_root, "setBackgroundColor", if (isCurrent) style.highlightColor else style.backgroundColor)

            row.setTextViewText(R.id.row_number, "${lesson.lessonNumber}")
            row.setTextColor(R.id.row_number, textColor)

            row.setTextViewText(R.id.row_time, if (lesson.start.isNotBlank()) "${lesson.start}–${lesson.end}" else "")
            row.setTextColor(R.id.row_time, if (isCurrent) style.mainColor else style.subColor)

            val groups = lesson.groups.joinToString(", ")
            row.setTextViewText(R.id.row_subject, if (groups.isBlank()) lesson.subject else "${lesson.subject} • $groups")
            row.setTextColor(R.id.row_subject, textColor)

            if (lesson.room.isNotBlank()) {
                applyChipPadding(context, row, R.id.row_room)
                row.setTextViewText(R.id.row_room, lesson.room)
                row.setTextColor(R.id.row_room, style.mainColor)
                row.setInt(R.id.row_room, "setBackgroundColor", style.highlightColor)
                row.setViewVisibility(R.id.row_room, android.view.View.VISIBLE)
            } else {
                row.setTextViewText(R.id.row_room, "")
                row.setViewVisibility(R.id.row_room, android.view.View.GONE)
            }
            return row
        }

        private fun applyChipPadding(
            context: Context,
            row: RemoteViews,
            viewId: Int,
        ) {
            val d = context.resources.displayMetrics.density
            val ph = (8 * d + 0.5f).toInt()
            val pv = (2 * d + 0.5f).toInt()
            row.setViewPadding(viewId, ph, pv, ph, pv)
        }

        private fun buildMoreRow(
            context: Context,
            style: WidgetColors,
            hiddenCount: Int,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)
            row.setInt(R.id.row_root, "setBackgroundColor", style.backgroundColor)
            row.setTextViewText(R.id.row_number, "")
            row.setTextViewText(R.id.row_time, "")
            row.setTextViewText(R.id.row_subject, "…ещё $hiddenCount")
            row.setTextColor(R.id.row_subject, style.subColor)
            row.setTextViewText(R.id.row_room, "")
            row.setViewVisibility(R.id.row_room2, android.view.View.GONE)
            return row
        }

        private fun showEmpty(
            views: RemoteViews,
            text: String,
        ) {
            views.setTextViewText(R.id.tv_teacher_today_empty, text)
            views.setViewVisibility(R.id.tv_teacher_today_empty, android.view.View.VISIBLE)
            WidgetUpdateHelper.showIdle(views)
        }
    }
}
