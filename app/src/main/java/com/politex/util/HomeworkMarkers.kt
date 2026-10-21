package com.politex.util

import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.TaskType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object HomeworkMarkers {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    internal const val FUZZY_MATCH_THRESHOLD = 0.8

    data class DayContext(
        val dayIso: String,
        val isToday: Boolean,
        val isPast: Boolean,
    )

    fun dayIsoOf(cal: Calendar): String = isoFormat.format(cal.time)

    fun dedupeByLatestDate(lessons: List<LessonEntity>): List<LessonEntity> {
        val latestMillis =
            lessons
                .filter { it.dateString.isNotBlank() }
                .maxOfOrNull { dateToMillis(it.dateString) } ?: return lessons
        return lessons.filter { it.dateString.isBlank() || dateToMillis(it.dateString) == latestMillis }
    }

    internal fun dateToMillis(date: String): Long = com.politex.data.network.MpkScheduleParser
        .parseDotDate(date)
        ?.timeInMillis ?: 0L

    fun markerLessonTypes(
        dayLessons: List<LessonEntity>,
        day: DayContext,
        nowMillis: Long,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
        pendingTasks: List<StudentTaskEntity>,
    ): Map<Int, List<TaskType>> {
        if (day.isPast || dayLessons.isEmpty() || pendingTasks.isEmpty()) return emptyMap()

        val result = LinkedHashMap<Int, MutableList<TaskType>>()
        for (lesson in dayLessons) {
            if (lessonPassed(lesson, day, nowMillis)) continue
            val subject = lesson.officialSubjectName
            for (task in pendingTasks) {
                val deadline = task.deadlineDate.trim()
                val matches =
                    if (!subjectsMatch(subject, task.subjectName)) {
                        false
                    } else if (deadline.isNotBlank()) {
                        sameIsoDay(deadline, day.dayIso)
                    } else {
                        val targetIso = resolveNextOccurrenceIso(task.createdAt, subject, weeklyLessonsByDow)
                        targetIso != null && sameIsoDay(targetIso, day.dayIso)
                    }
                if (matches) {
                    result.getOrPut(lesson.lessonNumber) { mutableListOf() }.add(task.taskTypeEnum)
                }
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
                    (normLesson.length >= 6 && normTask.length >= 6 &&
                        (normLesson.contains(normTask, ignoreCase = true) || normTask.contains(normLesson, ignoreCase = true))) ||
                    (normLesson.length >= 6 && normTask.length >= 6 &&
                        SubjectFormatter.levenshteinSimilarity(normLesson, normTask) >= FUZZY_MATCH_THRESHOLD) ||
                    (foldedRawLesson.length >= 6 && rawTask.length >= 6 &&
                        SubjectFormatter.levenshteinSimilarity(foldedRawLesson, rawTask) >= FUZZY_MATCH_THRESHOLD)
            }
    }

    fun resolveNextOccurrenceIso(
        createdAt: Long,
        subject: String,
        weeklyLessonsByDow: Map<Int, List<LessonEntity>>,
    ): String? {
        val created = (Calendar.getInstance().apply { timeInMillis = createdAt }).clone() as Calendar
        for (offset in 0 until 28) {
            val cal = (created.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val dow = Dow.of(cal)
            val dayLessons = weeklyLessonsByDow[dow] ?: continue
            val subjectLessons =
                dayLessons
                    .filter { subjectsMatch(it.officialSubjectName, subject) }
            for (lesson in subjectLessons) {
                val endMillis = endOfDayMillis(cal, lesson.timeEnd)
                if (endMillis > createdAt) return dayIsoOf(cal)
            }
        }
        return null
    }

    private fun lessonPassed(
        lesson: LessonEntity,
        day: DayContext,
        nowMillis: Long,
    ): Boolean {
        if (!day.isToday) return false
        val nowMinutes =
            run {
                val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            }
        val endMinutes = parseMinutes(lesson.timeEnd) ?: return false
        return endMinutes <= nowMinutes
    }

    private fun endOfDayMillis(
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
        val minutes = parseMinutes(timeEnd) ?: (24 * 60)
        cal.add(Calendar.MINUTE, minutes)
        return cal.timeInMillis
    }

    private fun parseMinutes(time: String): Int? {
        val digits = Regex("(\\d{1,2})\\D+(\\d{1,2})").find(time.trim()) ?: return null
        val h = digits.groupValues[1].toIntOrNull() ?: return null
        val m = digits.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    private fun sameIsoDay(
        a: String,
        b: String,
    ): Boolean = normalizeIso(a) == normalizeIso(b) && normalizeIso(a).isNotBlank()

    private fun normalizeIso(value: String): String = value.trim().replace("-", ".").replace("/", ".")
}
