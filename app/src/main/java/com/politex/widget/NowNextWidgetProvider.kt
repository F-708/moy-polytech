package com.politex.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.politex.R
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.util.RouteStartResolver
import com.politex.util.Dow
import com.politex.util.SubjectFormatter
import kotlinx.coroutines.launch

class NowNextWidgetProvider : AppWidgetProvider() {
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
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowNextWidgetProvider::class.java))

            ids.forEach { id -> runCatching { manager.updateAppWidget(id, buildViews(context, id)) } }
        }

        internal fun buildViews(
            context: Context,
            appWidgetId: Int,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_now_next)

            val style = WidgetStyle.load(context, appWidgetId).colors(context)
            val showRoom = WidgetOptions.showRoom(context, appWidgetId)

            WidgetUpdateHelper.applyWidgetBackground(views, context, appWidgetId, style.backgroundColor)
            views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)

            views.setViewVisibility(R.id.tv_empty, android.view.View.GONE)
            listOf(R.id.ll_now_block, R.id.ll_next_block, R.id.v_divider).forEach {
                views.setViewVisibility(it, android.view.View.VISIBLE)
            }
            views.setTextColor(R.id.tv_now_label, style.subColor)
            views.setTextColor(R.id.tv_next_label, style.subColor)
            views.setTextColor(R.id.tv_now_subject, style.mainColor)
            views.setTextColor(R.id.tv_next_subject, style.mainColor)
            views.setTextColor(R.id.tv_now_details, style.subColor)
            views.setTextColor(R.id.tv_next_details, style.subColor)

            views.setInt(R.id.tv_now_room, "setBackgroundColor", style.highlightColor)
            views.setInt(R.id.tv_next_room, "setBackgroundColor", style.highlightColor)
            views.setTextColor(R.id.tv_now_room, style.mainColor)
            views.setTextColor(R.id.tv_next_room, style.mainColor)

            views.setInt(R.id.tv_now_room2, "setBackgroundColor", style.highlightColor)
            views.setInt(R.id.tv_next_room2, "setBackgroundColor", style.highlightColor)
            views.setTextColor(R.id.tv_now_room2, style.mainColor)
            views.setTextColor(R.id.tv_next_room2, style.mainColor)

            val pref = com.politex.util.SubgroupSettings.get(context)

            val teacherSnap = TeacherWidgetData.teacherModeSnapshot(context)
            val teacherMode = teacherSnap != null
            val now = WidgetData.minuteOfDay(context)
            val lessons = teacherSnap?.toEntities() ?: WidgetData.todayLessons(context)

            val current = lessons.firstOrNull { now in it.startMinutes()..it.endMinutes() }

            val next =
                when {
                    current != null -> lessons.firstOrNull { it.lessonNumber > current.lessonNumber }
                    else -> lessons.firstOrNull { it.startMinutes() > now }
                }

            fun subjectOf(lesson: LessonEntity): String =
                TeacherWidgetLogic.lessonTitleText(
                    if (teacherMode) lesson.subjectRaw else SubjectFormatter.getShortName(lesson.subjectRaw),
                    lesson.groupName,
                    teacherMode,
                )

            val nextFromRoom = next?.let { RouteStartResolver.startRoomForLesson(lessons, it, pref) }
            val nowFromRoom = current?.let { RouteStartResolver.startRoomForLesson(lessons, it, pref) }

            val groupBlank = !teacherMode && WidgetUpdateHelper.getSelectedGroup(context).isBlank()
            val emptyText =
                when {
                    groupBlank -> "Выберите группу в приложении"
                    lessons.isEmpty() ->
                        if (teacherMode) {
                            TeacherWidgetLogic.nowNextEmptyText(lessonsEmpty = true, next = teacherSnap?.next)
                        } else {
                            "Сегодня уроков нет"
                        }
                    current == null && next == null ->
                        if (teacherMode) {
                            TeacherWidgetLogic.nowNextEmptyText(lessonsEmpty = false, next = teacherSnap?.next)
                        } else {
                            "Уроки закончились"
                        }
                    else -> null
                }
            if (emptyText != null) {
                views.setTextViewText(R.id.tv_empty, emptyText)

                views.setTextColor(R.id.tv_empty, style.mainColor)
                views.setViewVisibility(R.id.tv_empty, android.view.View.VISIBLE)
                listOf(R.id.ll_now_block, R.id.ll_next_block, R.id.v_divider).forEach {
                    views.setViewVisibility(it, android.view.View.GONE)
                }

                WidgetUpdateHelper.showIdle(views)
            } else if (current != null) {
                views.setTextViewText(R.id.tv_now_label, "СЕЙЧАС")
                views.setTextViewText(R.id.tv_now_subject, subjectOf(current))
                views.setTextViewText(R.id.tv_now_details, detailsOf(current))
                applyRoomChips(context, views, current, showRoom, appWidgetId, 1, pref, nowFromRoom, R.id.tv_now_room, R.id.tv_now_room2)
                if (next != null) {
                    views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                    views.setTextViewText(R.id.tv_next_subject, subjectOf(next))
                    views.setTextViewText(R.id.tv_next_details, detailsOf(next))
                    applyRoomChips(context, views, next, showRoom, appWidgetId, 2, pref, nextFromRoom, R.id.tv_next_room, R.id.tv_next_room2)
                } else {
                    views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                    views.setTextViewText(R.id.tv_next_subject, "Уроков больше нет")
                    views.setTextViewText(R.id.tv_next_details, "")
                    views.setViewVisibility(R.id.tv_next_room, android.view.View.GONE)
                    views.setViewVisibility(R.id.tv_next_room2, android.view.View.GONE)
                }
            } else {

                val bells = CollegeBellSchedule.getBellsForDay(WidgetData.dayOfWeek(context))
                val firstBell = bells.firstOrNull()
                views.setTextViewText(R.id.tv_now_label, "СЕЙЧАС")
                views.setTextViewText(R.id.tv_now_subject, "Перемена")
                views.setTextViewText(
                    R.id.tv_now_details,
                    if (firstBell != null && now < firstBell.startMinutes) {
                        "первый урок в ${firstBell.start}"
                    } else {
                        WidgetData.breakInfo(context, now) ?: "по расписанию звонков"
                    },
                )
                views.setViewVisibility(R.id.tv_now_room, android.view.View.GONE)
                views.setViewVisibility(R.id.tv_now_room2, android.view.View.GONE)

                views.setTextViewText(R.id.tv_next_label, "ДАЛЬШЕ")
                views.setTextViewText(R.id.tv_next_subject, subjectOf(next!!))
                views.setTextViewText(R.id.tv_next_details, detailsOf(next))
                applyRoomChips(context, views, next, showRoom, appWidgetId, 2, pref, nextFromRoom, R.id.tv_next_room, R.id.tv_next_room2)
            }

            val open =
                WidgetUpdateHelper.openAppIntent(
                    context,
                    4_000_000 + appWidgetId,
                    "SCHEDULE",
                    scrollTarget = "CURRENT_LESSON",
                )
            views.setOnClickPendingIntent(R.id.now_next_root, open)
            return views
        }

        private fun detailsOf(lesson: LessonEntity): String = "${lesson.timeStart}–${lesson.timeEnd}"

        private fun applyRoomChips(
            context: Context,
            views: RemoteViews,
            lesson: LessonEntity,
            showRoom: Boolean,
            appWidgetId: Int,
            slotCode: Int,
            pref: com.politex.util.SubgroupPref,
            fromRoom: String?,
            id1: Int,
            id2: Int,
        ) {

            val rooms = WidgetData.chooseRooms(lesson, pref)
            if (!showRoom || rooms.isEmpty()) {
                views.setViewVisibility(id1, android.view.View.GONE)
                views.setViewVisibility(id2, android.view.View.GONE)
                return
            }
            val splitStyle = WidgetData.isSplitStyle(rooms)
            rooms.forEachIndexed { i, room ->
                val viewId = if (i == 0) id1 else id2
                views.setTextViewText(viewId, if (splitStyle) room else "каб. $room")
                views.setViewVisibility(viewId, android.view.View.VISIBLE)
                views.setOnClickPendingIntent(
                    viewId,
                    WidgetUpdateHelper.openAppIntent(
                        context,

                        1_000_000 + appWidgetId * 100 + slotCode * 10 + i,
                        "SCHEDULE",
                        room = room,
                        showRoute = true,
                        fromRoom = fromRoom,
                    ),
                )
            }
            if (rooms.size == 1) views.setViewVisibility(id2, android.view.View.GONE)
        }
    }
}

object WidgetData {

    fun minuteOfDay(cal: java.util.Calendar): Int = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

    fun minuteOfDay(context: Context): Int = minuteOfDay(java.util.Calendar.getInstance())

    fun dayOfWeek(context: Context): Int = Dow.of(java.util.Calendar.getInstance())

    @Volatile private var cacheKey = ""

    @Volatile private var cacheLessons: List<LessonEntity> = emptyList()

    internal fun lessonsCacheKey(
        minute: Int,
        group: String,
        date: String,
    ): String = "$date|$minute|$group"

    fun invalidateCache() {
        cacheKey = ""
    }

    fun todayLessons(context: Context): List<LessonEntity> {
        val group = WidgetUpdateHelper.getSelectedGroup(context)
        if (group.isBlank()) return emptyList()
        val todayString = todayDateString()
        val key = lessonsCacheKey(minuteOfDay(context), group, todayString)
        if (key == cacheKey) return cacheLessons
        val dow = dayOfWeek(context)
        val result =
            try {
                kotlinx.coroutines.runBlocking {
                    val all =
                        com.politex.data.local.MpkDatabase
                            .getInstance(context)
                            .lessonDao()
                            .getLessonsForDaySync(group, dow)
                    filterTodayLessons(all, todayString)
                }
            } catch (_: Exception) {
                emptyList()
            }

        cacheLessons = result
        cacheKey = key
        return result
    }

    fun chooseRooms(
        lesson: LessonEntity,
        pref: com.politex.util.SubgroupPref,
    ): List<String> {
        val both =
            listOf(lesson.roomFirst.trim(), lesson.roomSecond.trim())
                .filter { it.isNotBlank() }
        if (!lesson.isSplit) return both.take(1)
        val idx = com.politex.util.SubgroupSettings.splitIndex(pref) ?: return both
        return listOfNotNull(both.getOrNull(idx) ?: both.firstOrNull())
    }

    fun isSplitStyle(rooms: List<String>): Boolean = rooms.size > 1

    internal fun todayDateString(): String {
        val cal = java.util.Calendar.getInstance()
        return String.format(
            java.util.Locale.ROOT,
            "%02d.%02d.%04d",
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.YEAR),
        )
    }

    internal fun normalizeDate(s: String): String = s.replace('-', '.')

    fun filterTodayLessons(
        all: List<LessonEntity>,
        todayString: String,
    ): List<LessonEntity> {
        val today = all.filter { normalizeDate(it.dateString) == todayString }
        val source = if (today.isNotEmpty()) today else all.filter { it.dateString.isBlank() }
        return source
            .filter { it.startMinutes() >= 0 && it.endMinutes() >= 0 }
            .sortedBy { it.lessonNumber }
    }

    fun breakInfo(
        context: Context,
        minute: Int,
    ): String? {
        val bells = CollegeBellSchedule.getBellsForDay(dayOfWeek(context))
        val bell =
            bells.firstOrNull {
                it.breakAfterMinutes > 0 &&
                    minute > it.endMinutes &&
                    minute < it.endMinutes + it.breakAfterMinutes
            } ?: return null
        val next = bells.getOrNull(bells.indexOf(bell) + 1) ?: return null
        return "Перемена до ${next.start}"
    }
}

internal fun LessonEntity.startMinutes(): Int = parseHhMm(timeStart)

internal fun LessonEntity.endMinutes(): Int = parseHhMm(timeEnd)

private fun parseHhMm(value: String): Int {
    val parts = value.split(":")
    if (parts.size < 2) return -1
    val h = parts[0].trim().toIntOrNull() ?: return -1
    val m = parts[1].trim().toIntOrNull() ?: return -1
    return h * 60 + m
}
