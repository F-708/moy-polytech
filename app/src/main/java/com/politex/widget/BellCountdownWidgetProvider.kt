package com.politex.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.politex.R
import com.politex.data.model.BellItem
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.Dow
import kotlinx.coroutines.launch
import java.util.Calendar

class BellCountdownWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        WidgetUpdateHelper.widgetScope.launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
                WidgetAlarm.scheduleNext(context)
                WidgetAlarm.scheduleNextSecond(context)
                runCatching { com.politex.util.BellTimerService.sync(context) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WidgetAlarm.scheduleNext(context)
        WidgetAlarm.scheduleNextSecond(context)
        runCatching { com.politex.util.BellTimerService.sync(context) }
    }

    override fun onDisabled(context: Context) {
        WidgetAlarm.cancelIfNoWork(context)
        runCatching { com.politex.util.BellTimerService.sync(context) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
        WidgetUpdateHelper.invalidateSecondsCache()
        WidgetAlarm.scheduleNextSecond(context)
        runCatching { com.politex.util.BellTimerService.sync(context) }
    }

    companion object {

        const val SHOW_FROM_MINUTE = 7 * 60

        fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val views = buildViews(context, id)
                manager.updateAppWidget(id, views)
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellCountdownWidgetProvider::class.java))
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        fun updateSecondsWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BellCountdownWidgetProvider::class.java))
            val secondsIds = ids.filter { WidgetOptions.seconds(context, it) }.toIntArray()
            if (secondsIds.isNotEmpty()) updateWidgets(context, manager, secondsIds)
        }

        fun countdownInfo(
            context: Context,
            mode: CountdownMode = CountdownMode.GROUP_LESSONS,
        ): Triple<Long, String, Int>? {
            val cal = java.util.Calendar.getInstance()
            val dow = Dow.of(cal)
            val minutes = WidgetData.minuteOfDay(cal)
            val bells = CollegeBellSchedule.getBellsForDay(dow)

            val lastGroupLesson = if (mode == CountdownMode.GROUP_LESSONS) {
                lastGroupLessonForToday(context, cal)
            } else {
                null
            }

            return countdownForMinute(minutes, dow, bells, mode, lastGroupLesson)
                ?: tomorrowFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
        }

        fun tomorrowFirstLessonLabel(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (minutes < SHOW_FROM_MINUTE) return null

            if (dayOfWeek == 7) return nextBellsDayLabel(minutes, dayOfWeek)
            if (bells.isEmpty()) return null

            val lastEnd = if (mode == CountdownMode.GROUP_LESSONS) {
                lastGroupLesson ?: return null
                bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= lastGroupLesson }?.endMinutes
            } else {
                bells.last().endMinutes
            } ?: return null
            if (minutes <= lastEnd) return null

            return nextBellsDayLabel(minutes, dayOfWeek)
        }

        private fun nextBellsDayLabel(
            minutes: Int,
            dayOfWeek: Int,
        ): Triple<Long, String, Int>? {
            var daysAhead = 1
            var dow = if (dayOfWeek == 7) 1 else dayOfWeek + 1
            if (dow == 7) {
                dow = 1
                daysAhead = 2
            }
            val first = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { !it.isInfoHour }
                ?: return null
            val minutesToStart = (daysAhead * 24 * 60 - minutes) + first.startMinutes
            val label = if (daysAhead > 1 || dayOfWeek == 7) "до 1 урока в пн" else "до 1 урока завтра"
            return Triple(minutesToStart.toLong(), label, 0)
        }

        fun nightFirstLessonLabel(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes >= SHOW_FROM_MINUTE) return null

            if (mode == CountdownMode.GROUP_LESSONS && lastGroupLesson == null) return null

            val first = bells.firstOrNull { !it.isInfoHour } ?: return null
            if (minutes >= first.startMinutes) return null
            return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
        }

        fun countdownForMinute(
            minutes: Int,
            dayOfWeek: Int,
            bells: List<BellItem>,
            mode: CountdownMode,
            lastGroupLesson: Int?,
        ): Triple<Long, String, Int>? {
            if (dayOfWeek == 7 || bells.isEmpty()) return null
            if (minutes < SHOW_FROM_MINUTE) return null

            if (mode == CountdownMode.GROUP_LESSONS) {
                if (lastGroupLesson == null) return null
                val lastBell = bells.lastOrNull {
                    !it.isInfoHour && it.lessonNumber <= lastGroupLesson
                } ?: return null
                if (minutes > lastBell.endMinutes) return null
            }

            bells.forEachIndexed { index, bell ->
                if (minutes in bell.startMinutes..bell.endMinutes) {
                    val left = bell.endMinutes - minutes
                    return Triple(left.toLong(), "до звонка", if (bell.isInfoHour) 0 else bell.lessonNumber)
                }

                val breakEnd = bell.endMinutes + bell.breakAfterMinutes
                if (bell.breakAfterMinutes > 0 && minutes in bell.endMinutes..breakEnd) {
                    val next = bells.getOrNull(index + 1) ?: return null
                    val left = next.startMinutes - minutes
                    return Triple(
                        left.toLong(),
                        if (next.isInfoHour) "до инфочаса" else "до ${next.lessonNumber} урока",
                        0,
                    )
                }
            }

            val first = bells.firstOrNull() ?: return null
            if (minutes < first.startMinutes) {
                return Triple((first.startMinutes - minutes).toLong(), "до 1 урока", 0)
            }
            return null
        }

        @Volatile private var cacheKey: String? = null

        @Volatile private var cacheLastLesson: Int? = null

        internal fun invalidateLastLessonCache() {
            cacheKey = null
            cacheLastLesson = null
        }

        fun lastGroupLessonForToday(context: Context, cal: Calendar): Int? {
            val teacherName = com.politex.util.TeacherModeStore.teacherName(context)
            if (teacherName != null) return lastTeacherLessonForToday(context, cal, teacherName)
            val group = WidgetUpdateHelper.getSelectedGroup(context)
            if (group.isBlank()) return null
            val dow = Dow.of(cal)

            val todayString = WidgetData.todayDateString()

            val key = "$group|$dow|$todayString"
            if (key == cacheKey) return cacheLastLesson
            val result = try {
                kotlinx.coroutines.runBlocking {
                    val lessons = com.politex.data.local.MpkDatabase.getInstance(context)
                        .lessonDao().getLessonsForDaySync(group, dow)
                    val forToday = lessons.filter {
                        it.dateString.isNotBlank() && WidgetData.normalizeDate(it.dateString) == todayString
                    }

                    if (forToday.isNotEmpty()) {
                        forToday.maxOf { it.lessonNumber }
                    } else {
                        lessons.filter { it.dateString.isBlank() }.maxOfOrNull { it.lessonNumber }
                    }
                }
            } catch (_: Exception) {
                return null
            }

            cacheLastLesson = result
            cacheKey = key
            return result
        }

        private fun lastTeacherLessonForToday(context: Context, cal: Calendar, teacherName: String): Int? {
            val dow = Dow.of(cal)
            val todayString = WidgetData.todayDateString()
            val key = "T|$teacherName|$dow|$todayString"
            if (key == cacheKey) return cacheLastLesson
            val result = try {
                TeacherWidgetData.teacherModeSnapshot(context)?.lessons?.maxOfOrNull { it.lessonNumber }
            } catch (_: Exception) {
                return null
            }
            cacheLastLesson = result
            cacheKey = key
            return result
        }

        private fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bell_countdown)

            val style = WidgetStyle.load(context, appWidgetId).colors(context)

            WidgetUpdateHelper.applyWidgetBackground(views, context, appWidgetId, style.backgroundColor)
            views.setViewVisibility(R.id.iv_idle, android.view.View.GONE)

            views.setViewVisibility(R.id.tv_countdown_value, android.view.View.VISIBLE)
            views.setTextColor(R.id.tv_countdown_label, style.subColor)
            views.setTextColor(R.id.tv_countdown_value, style.mainColor)

            val mode = CountdownMode.load(context, appWidgetId)
            val cal = java.util.Calendar.getInstance()
            val dow = Dow.of(cal)
            val minutes = WidgetData.minuteOfDay(cal)
            val bells = CollegeBellSchedule.getBellsForDay(dow)

            val lastGroupLesson = if (mode == CountdownMode.GROUP_LESSONS && dow in 1..6) {
                lastGroupLessonForToday(context, cal)
            } else {
                null
            }

            val info = countdownForMinute(minutes, dow, bells, mode, lastGroupLesson)

            val secondsOn = WidgetOptions.seconds(context, appWidgetId)
            val secondNow = cal.get(Calendar.SECOND)
            fun valueText(minutesLeft: Long): String = if (secondsOn) formatSeconds(minutesLeft * 60 - secondNow) else formatMinutes(minutesLeft)
            when {
                info != null && info.first >= 0 -> {
                    val (minutesLeft, label, _) = info
                    views.setTextViewText(R.id.tv_countdown_label, label)
                    views.setTextViewText(R.id.tv_countdown_value, valueText(minutesLeft))
                }

                else -> {
                    val tomorrow = tomorrowFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)

                    val night = if (tomorrow == null) {
                        nightFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
                    } else {
                        null
                    }
                    when {
                        tomorrow != null -> {
                            views.setTextViewText(R.id.tv_countdown_label, tomorrow.second)
                            views.setTextViewText(R.id.tv_countdown_value, formatHoursRounded(tomorrow.first))
                        }
                        night != null -> {
                            views.setTextViewText(R.id.tv_countdown_label, night.second)
                            views.setTextViewText(R.id.tv_countdown_value, valueText(night.first))
                        }

                        dow in 1..6 && mode == CountdownMode.GROUP_LESSONS && lastGroupLesson == null -> {
                            views.setTextViewText(R.id.tv_countdown_label, "Уроков нет")
                            views.setViewVisibility(R.id.tv_countdown_value, android.view.View.GONE)
                            WidgetUpdateHelper.showIdle(views)
                        }
                        dow in 1..6 && mode == CountdownMode.ALL_BELLS && bells.isEmpty() -> {
                            views.setTextViewText(R.id.tv_countdown_label, "Звонков сегодня нет")
                            views.setViewVisibility(R.id.tv_countdown_value, android.view.View.GONE)
                            WidgetUpdateHelper.showIdle(views)
                        }
                        else -> {
                            views.setTextViewText(
                                R.id.tv_countdown_label,
                                if (dow == 7) "Выходной" else "Сегодня уроков нет",
                            )
                            views.setViewVisibility(R.id.tv_countdown_value, android.view.View.GONE)
                            WidgetUpdateHelper.showIdle(views)
                        }
                    }
                }
            }

            val openIntent = WidgetUpdateHelper.openAppIntent(
                context,
                4_000_000 + appWidgetId,
                "BELLS",
                scrollTarget = "CURRENT_BELL",
            )
            views.setOnClickPendingIntent(R.id.widget_countdown_root, openIntent)
            return views
        }

        fun formatMinutes(totalMinutes: Long): String {
            if (totalMinutes <= 0) return "меньше минуты"
            val h = totalMinutes / 60
            val m = totalMinutes % 60
            return when {
                h == 0L -> "$m мин"
                m == 0L -> "$h ч"
                else -> "$h ч $m мин"
            }
        }

        fun formatHoursRounded(totalMinutes: Long): String {
            val hours = Math.round(totalMinutes / 60.0).toLong().coerceAtLeast(1L)
            return "$hours ч"
        }

        fun formatSeconds(totalSeconds: Long): String {
            val s = totalSeconds.coerceAtLeast(0L)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h == 0L) {
                String.format(java.util.Locale.US, "%d:%02d", m, sec)
            } else {
                String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
            }
        }
    }
}

enum class CountdownMode(val title: String, val description: String) {
    GROUP_LESSONS(
        "До конца моих уроков",
        "Отсчёт заканчивается на последнем уроке вашей группы",
    ),
    ALL_BELLS(
        "До конца всех звонков",
        "Отсчёт идёт до последнего звонка в колледже",
    ),
    ;

    companion object {
        private const val PREF_PREFIX = "countdown_mode_"

        fun load(context: Context, appWidgetId: Int): CountdownMode {
            val name = WidgetUpdateHelper.getPrefs(context).getString(PREF_PREFIX + appWidgetId, null)
            return entries.firstOrNull { it.name == name } ?: GROUP_LESSONS
        }

        fun save(context: Context, appWidgetId: Int, mode: CountdownMode) {
            WidgetUpdateHelper.getPrefs(context)
                .edit()
                .putString(PREF_PREFIX + appWidgetId, mode.name)
                .apply()
        }
    }
}

enum class WidgetStyle(
    val title: String,
) {
    SYSTEM("Системная"),
    APP("Как в приложении"),
    ;

    companion object {
        private const val PREF_PREFIX = "widget_style_"

        val SYSTEM_LIGHT =
            WidgetColors(
                backgroundColor = 0xFFFFFFFF.toInt(),
                mainColor = 0xFF111827.toInt(),
                subColor = 0xFF6B7280.toInt(),
                highlightColor = 0xFFE8F0FA.toInt(),
                pastColor = 0xFF6B7280.toInt(),
            )

        val SYSTEM_DARK =
            WidgetColors(
                backgroundColor = 0xFF001737.toInt(),
                mainColor = 0xFFFFFFFF.toInt(),
                subColor = 0xFFB9C6D8.toInt(),
                highlightColor = 0xFF0B3564.toInt(),
                pastColor = 0xFF5A6B85.toInt(),
            )

        fun systemColors(night: Boolean): WidgetColors = if (night) SYSTEM_DARK else SYSTEM_LIGHT

        fun fromName(name: String?): WidgetStyle = entries.firstOrNull { it.name == name } ?: SYSTEM

        fun load(context: Context, appWidgetId: Int): WidgetStyle = fromName(WidgetUpdateHelper.getPrefs(context).getString(PREF_PREFIX + appWidgetId, null))

        fun save(context: Context, appWidgetId: Int, style: WidgetStyle) {
            WidgetUpdateHelper.getPrefs(context)
                .edit()
                .putString(PREF_PREFIX + appWidgetId, style.name)
                .apply()
        }
    }

    fun colors(context: Context): WidgetColors = when (this) {
        SYSTEM -> {
            val night =
                context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            systemColors(night)
        }
        APP -> {
            val p = com.politex.util.AppThemeStore.load(context).palette
            WidgetColors(
                backgroundColor = p.brandFill.toArgb(),
                mainColor = 0xFFFFFFFF.toInt(),
                subColor = 0xFFD6E2F0.toInt(),
                highlightColor = p.topBar.toArgb(),
                pastColor = 0xFF9FB0C4.toInt(),
            )
        }
    }
}

data class WidgetColors(
    val backgroundColor: Int,
    val mainColor: Int,
    val subColor: Int,
    val highlightColor: Int,
    val pastColor: Int,
)

object WidgetAlarm {

    private const val SCHOOL_START = 7 * 60
    private const val SCHOOL_END = 21 * 60

    private const val MINUTE_TICK_REQUEST_CODE = 4002
    private const val SECOND_TICK_REQUEST_CODE = 4003

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetAlarmReceiver::class.java).apply {
            action = WidgetAlarmReceiver.ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            MINUTE_TICK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun secondPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetAlarmReceiver::class.java).apply {
            action = WidgetAlarmReceiver.ACTION_SECOND_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            SECOND_TICK_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hasWork(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(
            BellCountdownWidgetProvider::class.java,
            NowNextWidgetProvider::class.java,
            TodayScheduleWidgetProvider::class.java,
            BellsWidgetProvider::class.java,

            TeacherNextWidgetProvider::class.java,
            TeacherTodayWidgetProvider::class.java,
        ).any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
    }

    fun scheduleNext(context: Context) {
        scheduleNextIf(context, hasWork(context))
    }

    internal fun scheduleNextIf(context: Context, hasWork: Boolean) {
        if (!hasWork) return
        val cal = Calendar.getInstance()
        val minutes = WidgetData.minuteOfDay(cal)
        val now = System.currentTimeMillis()

        val triggerAt = if (minutes in SCHOOL_START until SCHOOL_END) {
            now + (60_000L - now % 60_000L)
        } else {
            (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 7)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        }

        WidgetUpdateHelper.setExactOrIdle(context, triggerAt, pendingIntent(context))
    }

    internal fun inSchoolWindow(minutesOfDay: Int): Boolean = minutesOfDay in SCHOOL_START until SCHOOL_END

    internal fun shouldScheduleSecondTick(
        hasSecondsWidgets: Boolean,
        serviceRunning: Boolean,
        minutesOfDay: Int,
    ): Boolean = hasSecondsWidgets && !serviceRunning && inSchoolWindow(minutesOfDay)

    internal fun nextSecondTrigger(nowMs: Long): Long = nowMs + (1000L - nowMs % 1000L)

    fun scheduleNextSecond(context: Context) {
        scheduleSecondTick(
            context = context,
            hasSecondsWidgets = WidgetUpdateHelper.hasSecondsWidgets(context),
            serviceRunning = com.politex.util.BellTimerService.isRunning,
            minutesOfDay = WidgetData.minuteOfDay(WidgetData.nowCalendar()),
            nowMs = System.currentTimeMillis(),
        )
    }

    internal fun scheduleSecondTick(
        context: Context,
        hasSecondsWidgets: Boolean,
        serviceRunning: Boolean,
        minutesOfDay: Int,
        nowMs: Long,
    ) {
        if (!shouldScheduleSecondTick(hasSecondsWidgets, serviceRunning, minutesOfDay)) {
            cancelSecond(context)
            return
        }
        WidgetUpdateHelper.setExactOrIdle(context, nextSecondTrigger(nowMs), secondPendingIntent(context))
    }

    internal fun shouldCancelAlarm(hasWork: Boolean): Boolean = !hasWork

    fun cancelIfNoWork(context: Context) {
        if (shouldCancelAlarm(hasWork(context))) {
            cancel(context)
            cancelSecond(context)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    fun cancelSecond(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(secondPendingIntent(context))
    }
}

class WidgetAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TICK -> {
                val pending = goAsync()
                WidgetUpdateHelper.widgetScope.launch {
                    try {
                        runCatching { WidgetUpdateHelper.ensureTodaySchedule(context) }

                        if (com.politex.util.BellCountdownNotifier.isEnabled(context)) {
                            if (!com.politex.util.BellTimerService.isRunning) {
                                runCatching { com.politex.util.BellCountdownNotifier.show(context) }
                            }
                        } else {
                            runCatching { com.politex.util.BellTimerService.sync(context) }
                        }

                        if (WidgetUpdateHelper.isFullUpdateStale(context)) {
                            WidgetUpdateHelper.updateAllWidgets(context)
                        } else {
                            val serviceAlive = com.politex.util.BellCountdownNotifier.isEnabled(context) &&
                                com.politex.util.BellTimerService.isRunning
                            if (serviceAlive) {
                                NowNextWidgetProvider.updateAll(context)
                            } else {
                                WidgetUpdateHelper.updateMinuteWidgets(context)
                            }
                        }

                        runCatching { TeacherWidgetUpdater.updateAllNow(context) }
                    } finally {
                        runCatching { WidgetAlarm.scheduleNext(context) }
                        runCatching { WidgetAlarm.scheduleNextSecond(context) }
                        pending.finish()
                    }
                }
            }
            ACTION_SECOND_TICK -> {
                val pending = goAsync()
                WidgetUpdateHelper.widgetScope.launch {
                    try {
                        runCatching { handleSecondTick(context) }
                    } finally {
                        pending.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                WidgetAlarm.scheduleNext(context)
                WidgetAlarm.scheduleNextSecond(context)

                runCatching { com.politex.util.BellTimerService.sync(context) }

                val pending = goAsync()
                WidgetUpdateHelper.widgetScope.launch {
                    try {
                        WidgetUpdateHelper.updateAllWidgets(context)

                        TeacherWidgetUpdater.updateAllNow(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.politex.widget.ACTION_BELL_COUNTDOWN_TICK"
        const val ACTION_SECOND_TICK = "com.politex.widget.ACTION_BELL_COUNTDOWN_SECOND_TICK"

        internal suspend fun handleSecondTick(context: Context) {
            BellCountdownWidgetProvider.updateSecondsWidgets(context)
            runCatching { WidgetAlarm.scheduleNextSecond(context) }
        }
    }
}
