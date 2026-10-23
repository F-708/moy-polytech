package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.network.ProbeResult
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.TeachersRepository
import com.politex.data.repository.matchRowsForTeacher
import com.politex.data.repository.nextDayCandidates
import com.politex.util.Dow
import com.politex.util.GroupParser
import com.politex.util.NotificationHelper
import com.politex.util.TeacherModeStore
import com.politex.widget.TeacherWidgetData
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal enum class TeacherDayDecision { NOTIFY, MARK_CHECKED, RETRY_LATER }

internal fun teacherDayDecision(
    documentPresent: Boolean,
    teacherRows: Int,
): TeacherDayDecision = when {
    teacherRows > 0 -> TeacherDayDecision.NOTIFY
    documentPresent -> TeacherDayDecision.MARK_CHECKED
    else -> TeacherDayDecision.RETRY_LATER
}

internal fun teacherMarkKey(
    kind: String,
    surnameKey: String,
    date: String,
): String = "${kind}_T_${surnameKey}_$date"

internal fun scheduleDayTitle(dow: Int): String = when (dow) {
    1 -> "понедельник"
    2 -> "вторник"
    3 -> "среду"
    4 -> "четверг"
    5 -> "пятницу"
    6 -> "субботу"
    else -> "следующий учебный день"
}

class ScheduleCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!WidgetUpdateHelper.isNotificationEnabled(applicationContext)) {
            return@withContext Result.success()
        }

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        if (currentMinutes < 10 * 60 || currentMinutes > 21 * 60) {
            return@withContext Result.success()
        }

        val teacherName = TeacherModeStore.teacherName(applicationContext)
        if (teacherName != null) {
            return@withContext doTeacherWork(teacherName, calendar)
        }

        val groupName = WidgetUpdateHelper.getSelectedGroup(applicationContext)
        val groupInfo = GroupParser.parse(groupName)
        val isFirstCourse = groupInfo?.course == 1

        val repository = com.politex.AppGraph.scheduleRepository

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        val prefs = WidgetUpdateHelper.getPrefs(applicationContext)
        fun marked(cal: Calendar): Boolean {
            val ds = dateFormat.format(cal.time)
            return prefs.getBoolean("notified_${groupName}_$ds", false) ||
                prefs.getBoolean("checked_${groupName}_$ds", false)
        }

        fun mark(key: String) {
            val editor = prefs.edit().putBoolean(key, true)
            staleMarkKeys(prefs.all.keys, System.currentTimeMillis()).forEach { editor.remove(it) }
            editor.apply()
        }
        val live = nextDayCandidates(calendar, saturdayIsSchoolDay = isFirstCourse).filterNot { marked(it) }
        if (live.isEmpty()) {
            return@withContext Result.success()
        }
        var targetCalendar = live.last()
        for (candidate in live) {
            if (repository.probeDate(candidate) is ProbeResult.Found) {
                targetCalendar = candidate
                break
            }
        }

        val targetDayOfWeek = Dow.of(targetCalendar)

        val targetDateString = dateFormat.format(targetCalendar.time)

        val prefKey = "notified_${groupName}_$targetDateString"

        val checkedKey = "checked_${groupName}_$targetDateString"

        val result = repository.syncScheduleForDate(groupName, targetCalendar)

        result.fold(
            onSuccess = { allLessons ->
                if (allLessons.isNotEmpty()) {
                    WidgetUpdateHelper.updateAllWidgets(applicationContext)

                    val targetLessons =
                        allLessons.filter {
                            it.dateString == targetDateString
                        }

                    val fallbackLessons =
                        if (targetLessons.isEmpty()) {
                            allLessons.filter { it.dateString.isBlank() && it.dayOfWeek == targetDayOfWeek }
                        } else {
                            emptyList()
                        }
                    val notifyLessons = targetLessons.ifEmpty { fallbackLessons }

                    if (notifyLessons.isNotEmpty()) {
                        val titleDay = scheduleDayTitle(targetDayOfWeek)

                        NotificationHelper.showScheduleReleaseNotification(
                            context = applicationContext,
                            dayTitle = titleDay,
                            lessonCount = notifyLessons.size,
                            targetDayOfWeek = targetDayOfWeek,
                            targetDate = targetDateString,
                        )

                        mark(prefKey)
                    } else {
                        mark(checkedKey)
                    }
                } else {
                    mark(checkedKey)
                }
                Result.success()
            },
            onFailure = {
                Result.success()
            },
        )
    }

    private suspend fun doTeacherWork(
        teacherName: String,
        calendar: Calendar,
    ): Result {
        val context = applicationContext
        val surnameKey = TeacherInsights.surnameOf(teacherName)
        if (surnameKey.isBlank()) return Result.success()

        val repository = com.politex.AppGraph.scheduleRepository
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
        val prefs = WidgetUpdateHelper.getPrefs(context)

        fun marked(cal: Calendar): Boolean {
            val ds = dateFormat.format(cal.time)
            return prefs.getBoolean(teacherMarkKey("notified", surnameKey, ds), false) ||
                prefs.getBoolean(teacherMarkKey("checked", surnameKey, ds), false)
        }

        fun mark(key: String) {
            val editor = prefs.edit().putBoolean(key, true)
            staleMarkKeys(prefs.all.keys, System.currentTimeMillis()).forEach { editor.remove(it) }
            editor.apply()
        }

        val live = nextDayCandidates(calendar, saturdayIsSchoolDay = true).filterNot { marked(it) }
        if (live.isEmpty()) return Result.success()

        val teachers = TeachersRepository(context).loadTeachers()
        val selected = Teacher(name = teacherName)

        for (candidate in live) {
            val date = dateFormat.format(candidate.time)
            val dow = Dow.of(candidate)

            val cachedBefore = runCatching { repository.hasTeacherScheduleForDate(date) }.getOrDefault(false)
            if (!cachedBefore) {
                runCatching { repository.syncTeacherScheduleForDate(candidate) }
            }

            val rows =
                runCatching {
                    matchRowsForTeacher(
                        repository.getTeacherLessonsForDate(surnameKey, date),
                        selected,
                        teachers,
                        dow,
                    ).first
                }.getOrDefault(emptyList())
            val documentPresent = cachedBefore ||
                runCatching { repository.hasTeacherScheduleForDate(date) }.getOrDefault(false)

            when (teacherDayDecision(documentPresent, rows.size)) {
                TeacherDayDecision.NOTIFY -> {
                    TeacherWidgetData.invalidate()
                    WidgetUpdateHelper.updateAllWidgets(context)

                    NotificationHelper.showScheduleReleaseNotification(
                        context = context,
                        dayTitle = scheduleDayTitle(dow),
                        lessonCount = rows.size,
                        targetDayOfWeek = dow,
                        targetDate = date,
                    )

                    mark(teacherMarkKey("notified", surnameKey, date))
                    return Result.success()
                }

                TeacherDayDecision.MARK_CHECKED -> mark(teacherMarkKey("checked", surnameKey, date))
                TeacherDayDecision.RETRY_LATER -> Unit
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_schedule_check_worker"

        private val DATE_IN_KEY = Regex("""\d{2}\.\d{2}\.\d{4}""")

        internal fun staleMarkKeys(
            allKeys: Set<String>,
            nowMillis: Long,
            keepDays: Int = 7,
        ): List<String> {
            val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
            val cutoff = nowMillis - keepDays * 24L * 60 * 60 * 1000
            return allKeys.filter { key ->
                (key.startsWith("notified_") || key.startsWith("checked_")) &&
                    DATE_IN_KEY.find(key)?.value?.let { ds ->
                        runCatching { fmt.parse(ds)?.time }.getOrNull()?.let { it < cutoff } ?: false
                    } ?: false
            }
        }
    }
}
