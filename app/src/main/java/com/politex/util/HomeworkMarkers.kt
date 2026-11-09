package com.politex.util

import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.TaskType
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object HomeworkMarkers {
    internal const val FUZZY_MATCH_THRESHOLD = 0.8

    private const val WINDOW_SCAN_DAYS = 28

    private const val UNKNOWN_END_OF_DAY_MINUTES = 24 * 60

    private val TIME_MINUTES_REGEX = Regex("(\\d{1,2})\\D+(\\d{1,2})")

    private val millisCache = ConcurrentHashMap<String, Long>()

    data class DayContext(
        val dayIso: String,
        val isToday: Boolean,
        val isPast: Boolean,
    )

    private data class WindowKey(
        val createdAt: Long,
        val subjectName: String,
        val deadline: String,
        val isCompleted: Boolean,
    )

    fun dayIsoOf(cal: Calendar): String =
        String.format(
            Locale.ROOT,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )

    fun isPastDay(
        dayIso: String,
        nowMillis: Long,
    ): Boolean {
        if (nowMillis <= 0L) return false
        val day = dateToMillis(dayIso)
        if (day <= 0L) return false
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val today = dateToMillis(dayIsoOf(nowCal))
        if (today <= 0L) return false
        return day < today
    }

    fun dedupeByLatestDate(lessons: List<LessonEntity>): List<LessonEntity> {
        val latestMillis =
            lessons
                .filter { it.dateString.isNotBlank() }
                .maxOfOrNull { dateToMillis(it.dateString) } ?: return lessons
        return lessons.filter { it.dateString.isBlank() || dateToMillis(it.dateString) == latestMillis }
    }

    internal fun dateToMillis(date: String): Long {
        val key = date.trim()
        if (key.isEmpty()) return 0L
        return millisCache.getOrPut(key) {
            com.politex.data.network.MpkScheduleParser.parseDotDate(key)?.timeInMillis ?: 0L
        }
    }

    fun firstActiveDayIso(
        createdAt: Long,
        subject: String,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
    ): String? {
        if (weeklyLessonsByDow.isEmpty()) return null
        val created = Calendar.getInstance().apply { timeInMillis = createdAt }
        for (offset in 0 until WINDOW_SCAN_DAYS) {
            val cal = (created.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dayLessons = weeklyLessonsByDow[Dow.of(cal)] ?: continue
            val active =
                dayLessons.any { lesson ->
                    subjectsMatch(lesson.officialSubjectName, subject) &&
                        lessonEndMillis(cal, lesson.timeEnd) > createdAt
                }
            if (active) return dayIsoOf(cal)
        }
        return null
    }

    fun isHomeworkActiveOn(
        task: StudentTaskEntity,
        day: DayContext,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
        nowMillis: Long,
    ): Boolean {
        if (task.isCompleted) return false
        if (day.isPast || isPastDay(day.dayIso, nowMillis)) return false
        val dayMillis = dateToMillis(day.dayIso)
        if (dayMillis <= 0L) return false
        val startIso = firstActiveDayIso(task.createdAt, task.subjectName, weeklyLessonsByDow) ?: return false
        val startMillis = dateToMillis(startIso)
        if (startMillis > 0L && dayMillis < startMillis) return false
        val deadline = task.deadlineDate.trim()
        if (deadline.isEmpty()) return true
        val deadlineMillis = dateToMillis(deadline)
        if (deadlineMillis <= 0L) return true
        if (dayMillis <= deadlineMillis) return true
        return isOverdueDeadline(deadlineMillis, nowMillis)
    }

    fun markerLessonTypes(
        dayLessons: List<LessonEntity>,
        day: DayContext,
        nowMillis: Long,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
        pendingTasks: List<StudentTaskEntity>,
    ): Map<Int, List<TaskType>> {
        if (day.isPast || dayLessons.isEmpty() || pendingTasks.isEmpty()) return emptyMap()

        val activeCache = HashMap<WindowKey, Boolean>()
        val result = LinkedHashMap<Int, MutableList<TaskType>>()
        for (lesson in dayLessons) {
            val subject = lesson.officialSubjectName
            for (task in pendingTasks) {
                if (!subjectsMatch(subject, task.subjectName)) continue
                val key = WindowKey(task.createdAt, task.subjectName, task.deadlineDate.trim(), task.isCompleted)
                val active = activeCache.getOrPut(key) { isHomeworkActiveOn(task, day, weeklyLessonsByDow, nowMillis) }
                if (!active) continue
                result.getOrPut(lesson.lessonNumber) { mutableListOf() }.add(task.taskTypeEnum)
            }
        }
        return result.mapValues { (_, types) -> types.distinct() }
    }

    fun subjectsMatch(
        lessonSubject: String,
        taskSubject: String,
    ): Boolean {
        if (lessonSubject.isBlank() || taskSubject.isBlank()) return false
        val rawTask = FuzzySearch.normalize(taskSubject.trim(), caseSensitive = true)
        val normTask = FuzzySearch.normalize(SubjectFormatter.normalize(taskSubject), caseSensitive = true)

        return lessonSubject
            .split('/')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .any { rawLesson ->
                val normLesson = FuzzySearch.normalize(SubjectFormatter.normalize(rawLesson), caseSensitive = true)
                val foldedRawLesson = FuzzySearch.normalize(rawLesson, caseSensitive = true)
                normLesson.equals(normTask, ignoreCase = true) ||
                    (
                        normLesson.length >= 6 &&
                            normTask.length >= 6 &&
                            (normLesson.contains(normTask, ignoreCase = true) || normTask.contains(normLesson, ignoreCase = true))
                        ) ||
                    (
                        normLesson.length >= 6 &&
                            normTask.length >= 6 &&
                            SubjectFormatter.levenshteinSimilarity(normLesson, normTask) >= FUZZY_MATCH_THRESHOLD
                        ) ||
                    (
                        foldedRawLesson.length >= 6 &&
                            rawTask.length >= 6 &&
                            SubjectFormatter.levenshteinSimilarity(foldedRawLesson, rawTask) >= FUZZY_MATCH_THRESHOLD
                        )
            }
    }

    private fun isOverdueDeadline(
        deadlineMillis: Long,
        nowMillis: Long,
    ): Boolean {
        if (nowMillis <= 0L) return false
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val today = dateToMillis(dayIsoOf(nowCal))
        if (today <= 0L) return false
        return deadlineMillis < today
    }

    private fun lessonEndMillis(
        dayCal: Calendar,
        timeEnd: String,
    ): Long {
        val cal =
            (dayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        cal.add(Calendar.MINUTE, parseMinutes(timeEnd) ?: UNKNOWN_END_OF_DAY_MINUTES)
        return cal.timeInMillis
    }

    private fun parseMinutes(time: String): Int? {
        val digits = TIME_MINUTES_REGEX.find(time.trim()) ?: return null
        val h = digits.groupValues[1].toIntOrNull() ?: return null
        val m = digits.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}