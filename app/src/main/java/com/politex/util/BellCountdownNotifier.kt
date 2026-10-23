package com.politex.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.politex.MainActivity
import com.politex.R
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.Dow
import com.politex.widget.BellCountdownWidgetProvider
import com.politex.widget.WidgetUpdateHelper
import java.util.Calendar

object BellCountdownNotifier {
    const val CHANNEL_ID = "mpk_bell_countdown"
    const val CHANNEL_NAME = "До звонка"

    const val NOTIFICATION_ID = 2027

    private const val PREF_ENABLED = "bell_notification_enabled"
    private const val PREF_STYLE = "bell_notification_style"
    private const val PREF_THEME = "bell_notification_theme"
    private const val PREF_SCOPE = "bell_notification_scope"

    enum class Scope(
        val id: String,
        val title: String,
        val description: String,
    ) {
        MY_LESSONS("my_lessons", "Мои уроки", "Счётчик заканчивается с последним уроком вашей группы"),
        COLLEGE("college", "Уроки колледжа", "Счётчик работает до последнего звонка колледжа"),
        ;

        companion object {
            fun from(id: String?): Scope = entries.firstOrNull { it.id == id } ?: MY_LESSONS
        }
    }

    enum class Theme(
        val id: String,
        val title: String,
        val accent: Int,
        val colorize: Boolean,
    ) {
        SYSTEM("system", "Системная", 0, false),
        APP("app", "Как в приложении", 0, true),
        ;

        companion object {
            fun from(id: String?): Theme = entries.firstOrNull { it.id == id } ?: SYSTEM
        }
    }

    enum class Style(
        val id: String,
        val title: String,
    ) {
        COMPACT("compact", "Компактный"),
        WITH_APP_NAME("app_name", "С названием приложения"),
        WITH_LESSON("lesson", "С номером урока"),
        WITH_PROGRESS("progress", "С полосой прогресса"),
        WITH_SECONDS("seconds", "С секундами"),
        ;

        companion object {

            fun from(id: String?): Style = entries
                .firstOrNull { it.id == id }
                ?.let { if (it == WITH_APP_NAME) COMPACT else it }
                ?: COMPACT
        }
    }

    fun isEnabled(context: Context): Boolean = WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_ENABLED, false)

    enum class ServiceAction { NONE, START, STOP }

    internal fun serviceActionFor(enabled: Boolean, running: Boolean): ServiceAction = when {
        !enabled -> ServiceAction.STOP
        !running -> ServiceAction.START
        else -> ServiceAction.NONE
    }

    internal fun ensureServiceAlive(context: Context) {
        if (serviceActionFor(isEnabled(context), BellTimerService.isRunning) == ServiceAction.START) {
            if (!BellTimerService.start(context)) BellTimerService.scheduleFallbackAlarm(context)
        }
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean,
    ) {
        WidgetUpdateHelper
            .getPrefs(context)
            .edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()
        if (enabled) {
            BellTimerService.sync(context)
        } else {
            if (!BellTimerService.sync(context)) cancel(context)
            com.politex.widget.WidgetAlarm.scheduleNextSecond(context)
        }
    }

    fun getStyle(context: Context): Style = Style.from(WidgetUpdateHelper.getPrefs(context).getString(PREF_STYLE, null))

    fun getTheme(context: Context): Theme = Theme.from(WidgetUpdateHelper.getPrefs(context).getString(PREF_THEME, null))

    fun setTheme(
        context: Context,
        theme: Theme,
    ) {
        WidgetUpdateHelper
            .getPrefs(context)
            .edit()
            .putString(PREF_THEME, theme.id)
            .apply()
        if (isEnabled(context)) show(context)
    }

    fun setStyle(
        context: Context,
        style: Style,
    ) {
        WidgetUpdateHelper
            .getPrefs(context)
            .edit()
            .putString(PREF_STYLE, style.id)
            .apply()
        if (isEnabled(context)) show(context)
    }

    fun getScope(context: Context): Scope = Scope.from(WidgetUpdateHelper.getPrefs(context).getString(PREF_SCOPE, null))

    fun setScope(
        context: Context,
        scope: Scope,
    ) {
        WidgetUpdateHelper
            .getPrefs(context)
            .edit()
            .putString(PREF_SCOPE, scope.id)
            .apply()
        if (isEnabled(context)) show(context)
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Постоянная строка «сколько осталось до звонка»"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }
    }

    fun show(context: Context) {
        if (!isEnabled(context)) return
        ensureServiceAlive(context)
        if (!canPost(context)) return
        val notification = buildNotification(context)

        if (isQuietWindowNow(context) && !BellTimerService.isRunning) {
            cancel(context)
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    const val RESUME_MINUTE = 6 * 60

    fun isQuietWindow(
        nowMinutes: Int,
        lessonsEndedMinute: Int?,
    ): Boolean {
        if (nowMinutes < RESUME_MINUTE) return true
        if (lessonsEndedMinute == null) return true
        return nowMinutes > lessonsEndedMinute
    }

    private fun lessonsEndedMinuteToday(context: Context): Int? {
        val cal = Calendar.getInstance()

        val dow = Dow.of(cal)
        if (dow == 7) return null
        val bells = CollegeBellSchedule.getBellsForDay(dow)
        return when (getScope(context)) {
            Scope.COLLEGE -> bells.lastOrNull { !it.isInfoHour }?.endMinutes
            Scope.MY_LESSONS -> {
                val last = BellCountdownWidgetProvider.lastGroupLessonForToday(context, cal) ?: return null
                bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= last }?.endMinutes
            }
        }
    }

    private fun morningInfo(context: Context): Triple<Long, String, Int>? {
        val cal = Calendar.getInstance()
        val now = com.politex.widget.WidgetData.minuteOfDay(cal)
        if (isQuietWindow(now, lessonsEndedMinuteToday(context))) return null

        val first = CollegeBellSchedule.getBellsForDay(modeLessonsDay(cal)).firstOrNull() ?: return null
        if (now >= first.startMinutes) return null
        return Triple((first.startMinutes - now).toLong(), "до 1 урока", 0)
    }

    private fun modeLessonsDay(cal: Calendar): Int = Dow.of(cal)

    private fun isQuietWindowNow(context: Context): Boolean {
        val cal = Calendar.getInstance()
        return isQuietWindow(
            com.politex.widget.WidgetData.minuteOfDay(cal),
            lessonsEndedMinuteToday(context),
        )
    }

    fun buildNotification(context: Context): android.app.Notification {
        createChannel(context)

        val scope = getScope(context)
        val mode =
            when (scope) {
                Scope.MY_LESSONS -> com.politex.widget.CountdownMode.GROUP_LESSONS
                Scope.COLLEGE -> com.politex.widget.CountdownMode.ALL_BELLS
            }
        val info = BellCountdownWidgetProvider.countdownInfo(context, mode)

        if (info == null || info.first < 0) {
            morningInfo(context)?.let { return buildVisibleNotification(context, it) }

            eveningOrNightInfo(context, mode)?.let { return buildVisibleNotification(context, it) }
            return buildHiddenNotification(context)
        }

        return buildVisibleNotification(context, info)
    }

    private fun eveningOrNightInfo(
        context: Context,
        mode: com.politex.widget.CountdownMode,
    ): Triple<Long, String, Int>? {
        val cal = Calendar.getInstance()

        val dow = Dow.of(cal)
        if (dow == 7) return null
        val minutes = com.politex.widget.WidgetData.minuteOfDay(cal)
        val bells = CollegeBellSchedule.getBellsForDay(dow)
        val lastGroupLesson =
            if (mode == com.politex.widget.CountdownMode.GROUP_LESSONS) {
                BellCountdownWidgetProvider.lastGroupLessonForToday(context, cal)
            } else {
                null
            }
        return BellCountdownWidgetProvider.tomorrowFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
            ?: BellCountdownWidgetProvider.nightFirstLessonLabel(minutes, dow, bells, mode, lastGroupLesson)
    }

    private fun buildVisibleNotification(
        context: Context,
        info: Triple<Long, String, Int>,
    ): android.app.Notification {
        val style = getStyle(context)

        val secondsNow = Calendar.getInstance().get(Calendar.SECOND)
        val valueText =
            if (style == Style.WITH_SECONDS) {
                formatMinutesSeconds(info.first, secondsNow)
            } else {
                BellCountdownWidgetProvider.formatMinutes(info.first)
            }

        val (title, progressPercent, hasProgress) = buildContent(info, style, valueText)

        val openIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val theme = getTheme(context)

        val accent =
            if (theme == Theme.APP) {
                AppThemeStore.load(context).palette.brandFill.toArgb()
            } else {
                theme.accent
            }

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mpk_emblem)
                .setContentIntent(openIntent)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setShowWhen(false)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())

        if (accent != 0) {
            builder.color = accent
            if (theme.colorize) {
                builder.setColorized(true)
            }
        }

        builder.setContentTitle(title)
        builder.setContentText(info.second)

        val views = android.widget.RemoteViews(context.packageName, R.layout.notification_bell_countdown)

        val cut = valueText.lastIndexOf(' ')
        if (cut > 0) {
            views.setTextViewText(R.id.nbell_value, valueText.substring(0, cut))
            views.setTextViewText(R.id.nbell_unit, valueText.substring(cut + 1))
        } else {
            views.setTextViewText(R.id.nbell_value, valueText)
            views.setTextViewText(R.id.nbell_unit, "")
        }
        views.setTextViewText(R.id.nbell_label, info.second)

        views.setViewVisibility(R.id.nbell_app, android.view.View.GONE)
        views.setViewVisibility(
            R.id.nbell_progress,
            if (style == Style.WITH_PROGRESS && hasProgress) android.view.View.VISIBLE else android.view.View.GONE,
        )
        if (style == Style.WITH_PROGRESS && hasProgress) {
            views.setProgressBar(R.id.nbell_progress, 100, progressPercent, false)
        }
        if (style == Style.WITH_LESSON) {
            views.setTextViewText(R.id.nbell_label, "${info.second} • ${currentLessonLabel(info)}")
        }
        builder.setCustomContentView(views)

        return builder.build()
    }

    private fun buildHiddenNotification(context: Context): android.app.Notification {
        val openIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val views =
            android.widget.RemoteViews(context.packageName, R.layout.notification_bell_countdown).apply {
                setTextViewText(R.id.nbell_value, "Уроков нет")
                setTextViewText(R.id.nbell_unit, "")
                setTextViewText(R.id.nbell_label, "")
                setViewVisibility(R.id.nbell_app, android.view.View.GONE)
                setViewVisibility(R.id.nbell_progress, android.view.View.GONE)
            }
        return NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mpk_emblem)
            .setContentIntent(openIntent)
            .setContentTitle("Уроков нет")
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(false)
            .build()
    }

    private fun canPost(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun refresh(context: Context) {
        if (isEnabled(context)) show(context) else cancel(context)
    }

    private data class Content(
        val title: String,
        val progressPercent: Int,
        val hasProgress: Boolean,
    )

    private const val PROGRESS_LESSON_MINUTES = 45L

    private fun buildContent(
        info: Triple<Long, String, Int>?,
        style: Style,
        valueText: String,
    ): Content {
        if (info == null || info.first < 0) {
            return Content(title = "Уроки закончились", progressPercent = 100, hasProgress = false)
        }

        val (minutesLeft, label, _) = info

        val progress =
            (
                (PROGRESS_LESSON_MINUTES - minutesLeft).coerceIn(0, PROGRESS_LESSON_MINUTES) * 100 /
                    PROGRESS_LESSON_MINUTES
                ).toInt()

        return Content(
            title = label.replaceFirstChar { it.uppercase() } + ": $valueText",
            progressPercent = progress,
            hasProgress = style == Style.WITH_PROGRESS,
        )
    }

    fun formatMinutesSeconds(
        minutes: Long,
        seconds: Int,
    ): String = BellCountdownWidgetProvider.formatSeconds(minutes * 60 - seconds)

    private fun currentLessonLabel(info: Triple<Long, String, Int>?): String {
        val lessonNumber = info?.third ?: 0
        return if (lessonNumber > 0) "$lessonNumber урок" else "Перемена"
    }
}
