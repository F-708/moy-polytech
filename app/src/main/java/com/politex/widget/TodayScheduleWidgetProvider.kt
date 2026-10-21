package com.politex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.local.entity.LessonEntity
import kotlinx.coroutines.launch

class TodayScheduleWidgetProvider : AppWidgetProvider() {
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    companion object {
        private const val MAX_ROWS = 12

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayScheduleWidgetProvider::class.java))
            ids.forEach { id -> runCatching { manager.updateAppWidget(id, buildViews(context, id)) } }
        }

        internal fun buildViews(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)
            val style = WidgetStyle.load(context, appWidgetId).colors(context)
            val showRoom = WidgetOptions.showRoom(context, appWidgetId)

            WidgetUpdateHelper.applyWidgetBackground(views, context, appWidgetId, style.backgroundColor)
            views.setTextColor(R.id.tv_today_header, style.subColor)
            views.setTextColor(R.id.tv_today_empty, style.subColor)

            val teacherSnap = TeacherWidgetData.teacherModeSnapshot(context)
            val teacherMode = teacherSnap != null
            val now = WidgetData.minuteOfDay(context)
            val lessons = teacherSnap?.toEntities() ?: WidgetData.todayLessons(context)
            val group = WidgetUpdateHelper.getSelectedGroup(context)
            val pref = com.politex.util.SubgroupSettings.get(context)

            views.setTextViewText(
                R.id.tv_today_header,
                TeacherWidgetLogic.widgetHeader(
                    group = group,
                    teacherName = if (teacherMode) com.politex.util.TeacherModeStore.teacherName(context) else null,
                ),
            )

            val emptyText =
                when {
                    teacherMode && teacherSnap?.dow == 7 ->
                        TeacherWidgetLogic.dayOffText(teacherSnap?.next)
                    teacherMode -> TeacherWidgetLogic.emptyText("На сегодня уроков нет", teacherSnap?.next)
                    group.isBlank() -> "Выберите группу в приложении"
                    else -> "На сегодня уроков нет"
                }
            views.setTextViewText(R.id.tv_today_empty, emptyText)

            views.removeAllViews(R.id.today_rows)

            if (lessons.isEmpty()) {
                views.setViewVisibility(R.id.tv_today_empty, android.view.View.VISIBLE)

                WidgetUpdateHelper.showIdle(views)
            } else {
                views.setViewVisibility(R.id.tv_today_empty, android.view.View.GONE)
                views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)
                lessons.take(MAX_ROWS).forEach { lesson ->
                    views.addView(
                        R.id.today_rows,
                        buildRow(
                            context, lesson, now, style, showRoom, appWidgetId, pref,

                            com.politex.ui.util.RouteStartResolver.startRoomForLesson(lessons, lesson, pref),
                            teacherMode,
                        ),
                    )
                }

                if (lessons.size > MAX_ROWS) {
                    views.addView(
                        R.id.today_rows,
                        buildMoreRow(context, style, lessons.size - MAX_ROWS),
                    )
                }
            }

            val open =
                WidgetUpdateHelper.openAppIntent(
                    context,
                    4_000_000 + appWidgetId,
                    "SCHEDULE",
                    scrollTarget = "CURRENT_LESSON",
                )
            views.setOnClickPendingIntent(R.id.today_root, open)
            return views
        }

        private fun buildRow(
            context: Context,
            lesson: LessonEntity,
            now: Int,
            style: WidgetColors,
            showRoom: Boolean,
            appWidgetId: Int,
            pref: com.politex.util.SubgroupPref,
            fromRoom: String,
            teacherMode: Boolean = false,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)

            val isCurrent = now in lesson.startMinutes()..lesson.endMinutes()
            val isPast = now > lesson.endMinutes()

            val textColor =
                when {
                    isCurrent -> style.mainColor
                    isPast -> style.pastColor
                    else -> style.mainColor
                }

            row.setInt(
                R.id.row_root,
                "setBackgroundColor",
                if (isCurrent) style.highlightColor else android.graphics.Color.TRANSPARENT,
            )

            row.setTextViewText(R.id.row_number, "${lesson.lessonNumber}")
            row.setTextColor(R.id.row_number, textColor)

            row.setTextViewText(R.id.row_time, "${lesson.timeStart}–${lesson.timeEnd}")
            row.setTextColor(R.id.row_time, if (isCurrent) style.mainColor else style.subColor)

            row.setTextViewText(
                R.id.row_subject,
                TeacherWidgetLogic.lessonTitleText(
                    if (teacherMode) {
                        lesson.subjectRaw
                    } else {
                        com.politex.util.SubjectFormatter
                            .getShortName(lesson.subjectRaw)
                    },
                    lesson.groupName,
                    teacherMode,
                ),
            )
            row.setTextColor(R.id.row_subject, textColor)

            val rooms = WidgetData.chooseRooms(lesson, pref)
            if (showRoom && rooms.isNotEmpty()) {
                val d = context.resources.displayMetrics.density

                val ph = (10 * d + 0.5f).toInt()

                val pv = (2 * d + 0.5f).toInt()
                val splitStyle = WidgetData.isSplitStyle(rooms)
                rooms.forEachIndexed { i, room ->
                    val viewId = if (i == 0) R.id.row_room else R.id.row_room2
                    row.setTextViewText(viewId, if (splitStyle) room else "каб. $room")
                    row.setTextColor(viewId, style.mainColor)
                    row.setInt(viewId, "setBackgroundColor", style.highlightColor)
                    row.setViewPadding(viewId, ph, pv, ph, pv)
                    row.setViewVisibility(viewId, android.view.View.VISIBLE)
                    row.setOnClickPendingIntent(
                        viewId,
                        WidgetUpdateHelper.openAppIntent(
                            context,

                            2_000_000 + appWidgetId * 100 + lesson.lessonNumber * 2 + i,
                            "MAP",
                            room = room,
                            showRoute = true,
                            fromRoom = fromRoom,
                        ),
                    )
                }
                if (rooms.size == 1) row.setViewVisibility(R.id.row_room2, android.view.View.GONE)
            } else {
                row.setTextViewText(R.id.row_room, "")
                row.setTextColor(R.id.row_room, style.subColor)
                row.setViewVisibility(R.id.row_room2, android.view.View.GONE)
            }

            return row
        }

        private fun buildMoreRow(
            context: Context,
            style: WidgetColors,
            hiddenCount: Int,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row_list)

            row.setInt(R.id.row_root, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
            row.setTextViewText(R.id.row_number, "")
            row.setTextViewText(R.id.row_time, "")
            row.setTextViewText(R.id.row_subject, "…ещё $hiddenCount")
            row.setTextColor(R.id.row_subject, style.subColor)
            row.setTextViewText(R.id.row_room, "")
            return row
        }
    }
}
