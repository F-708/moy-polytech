package com.politex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.model.CollegeBellSchedule

class BellsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        ids.forEach { id ->
            val views = runCatching { buildViews(context, id) }.getOrNull()
            WidgetUpdateHelper.applyOrFallback(context, manager, id, FALLBACK_TITLE, views)
        }
        runCatching { WidgetAlarm.scheduleNext(context) }
    }

    override fun onEnabled(context: Context) = WidgetAlarm.scheduleNext(context)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    companion object {
        const val FALLBACK_TITLE = "Звонки"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellsWidgetProvider::class.java))
            ids.forEach { id ->
                val views = runCatching { buildViews(context, id) }.getOrNull()
                WidgetUpdateHelper.applyOrFallback(context, manager, id, FALLBACK_TITLE, views)
            }
        }

        private fun buildViews(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bells)
            val style = WidgetStyle.load(context, appWidgetId).colors(context)

            WidgetUpdateHelper.applyWidgetBackground(views, context, appWidgetId, style.backgroundColor)
            views.setTextColor(R.id.tv_bells_header, style.subColor)

            val now = WidgetData.minuteOfDay(context)
            val dow = WidgetData.dayOfWeek(context)

            val isSunday = dow == 7
            val bells = CollegeBellSchedule.getBellsForDay(if (isSunday) 1 else dow)

            if (isSunday) {
                views.setTextViewText(R.id.tv_bells_header, "ЗВОНКИ ПН")
            }

            views.removeAllViews(R.id.bells_rows)

            bells.forEach { bell ->
                val isCurrent = !isSunday && now in bell.startMinutes..bell.endMinutes
                val isPast = !isSunday && now > bell.endMinutes

                val row = RemoteViews(context.packageName, R.layout.widget_row_bell)

                row.setInt(
                    R.id.row_root,
                    "setBackgroundColor",
                    if (isCurrent) style.highlightColor else android.graphics.Color.TRANSPARENT,
                )

                val textColor =
                    when {
                        isCurrent -> style.mainColor
                        isPast -> style.pastColor
                        else -> style.mainColor
                    }

                row.setTextViewText(R.id.row_number, if (bell.isInfoHour) "инф" else "${bell.lessonNumber}")
                row.setTextColor(R.id.row_number, textColor)

                row.setTextViewText(R.id.row_time, "${bell.start}–${bell.end}")
                row.setTextColor(R.id.row_time, if (isCurrent) style.mainColor else style.subColor)

                views.addView(R.id.bells_rows, row)
            }

            val open =
                WidgetUpdateHelper.openAppIntent(
                    context,
                    4_000_000 + appWidgetId,
                    "BELLS",
                    scrollTarget = "CURRENT_BELL",
                )
            views.setOnClickPendingIntent(R.id.bells_root, open)
            return views
        }
    }
}
