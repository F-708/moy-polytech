package com.politex.data.repository

import android.content.Context
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.TeacherSlot
import com.politex.ui.teacher.TeacherProfile
import com.politex.util.DateFormats
import com.politex.util.Dow
import com.politex.util.SubjectFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar

internal fun teacherTargetCalendar(
    dayOffset: Int,
    customDate: Calendar?,
    base: Calendar = Calendar.getInstance(),
): Calendar = customDate ?: (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }

internal fun defaultTeacherDayOffset(todayDow: Int): Int = if (todayDow == 7) 1 else 0

internal fun teacherSyncErrorMessage(
    e: Throwable,
    dateKey: String,
): String {
    val msg = e.message.orEmpty()
    return if (msg.contains("не найден") || msg.contains("вместо") || msg.contains("другой день")) {
        "Расписание преподавателя на $dateKey ещё не опубликовано"
    } else {
        "Не удалось загрузить расписание (проверьте интернет)"
    }
}

internal data class TeacherDayLoad(
    val slots: List<TeacherSlot>,
    val error: String,
    val dateKey: String,
)

internal suspend fun loadTeacherDay(
    dateKey: String,
    fromDatabase: suspend () -> List<TeacherSlot>,
    sync: suspend () -> Result<Int>,
): TeacherDayLoad {
    val cached = fromDatabase()
    if (cached.isNotEmpty()) return TeacherDayLoad(cached, "", dateKey)

    var slots = cached
    var error = ""
    runCatching { sync() }
        .onFailure { if (it is CancellationException) throw it }
        .getOrElse { Result.failure(it) }
        .fold(
            onSuccess = { slots = fromDatabase() },
            onFailure = { error = teacherSyncErrorMessage(it, dateKey) },
        )
    return TeacherDayLoad(slots, error, dateKey)
}

data class TeacherDayLesson(
    val lessonNumber: Int,
    val subject: String,
    val groups: List<String>,
    val room: String,
    val start: String,
    val end: String,
    val startMinutes: Int,
    val endMinutes: Int,
)

data class TeacherDay(
    val lessons: List<TeacherDayLesson>,
    val ambiguous: Boolean,
    val error: String,
    val isSynced: Boolean,
    val lastSyncEpoch: Long,
    val date: String = "",
)

internal fun freshTeacherLessons(
    day: TeacherDay?,
    date: String,
): List<TeacherDayLesson> = if (day != null && day.date == date) day.lessons else emptyList()

data class NextLesson(
    val dateString: String,
    val dow: Int,
    val lesson: TeacherDayLesson,
)

enum class LessonStatus { PAST, NOW, NEXT, UPCOMING }

object TeacherDayLogic {
    private val SHORT_DOW = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    fun statuses(
        lessons: List<TeacherDayLesson>,
        nowMinutes: Int,
        isToday: Boolean,
    ): List<LessonStatus> {
        if (!isToday) return List(lessons.size) { LessonStatus.UPCOMING }
        var nextAssigned = false
        return lessons.map { l ->
            when {
                l.startMinutes < 0 -> LessonStatus.UPCOMING
                nowMinutes in l.startMinutes..l.endMinutes -> LessonStatus.NOW
                nowMinutes > l.endMinutes -> LessonStatus.PAST
                !nextAssigned -> {
                    nextAssigned = true
                    LessonStatus.NEXT
                }
                else -> LessonStatus.UPCOMING
            }
        }
    }

    fun shortDow(dow: Int): String = SHORT_DOW[(dow - 1).coerceIn(0, 6)]

    fun emptyDayText(
        dow: Int,
        next: NextLesson?,
        isToday: Boolean = true,
    ): String = when {
        dow == 7 -> "Выходной"
        next != null ->
            (if (isToday) "Сегодня пар нет." else "На этот день пар нет.") +
                " Следующая — ${shortDow(next.dow)} ${next.lesson.start}" +
                if (next.lesson.room.isNotBlank()) ", каб. ${next.lesson.room}" else ""
        else -> if (isToday) "Сегодня пар нет." else "На этот день пар нет."
    }
}

internal fun matchRowsForTeacher(
    rows: List<TeacherLessonEntity>,
    selected: Teacher,
    allTeachers: List<Teacher>,
    dow: Int,
): Pair<List<TeacherLessonEntity>, Boolean> {
    val shown =
        rows
            .mapNotNull { row ->
                val m = TeacherInsights.matchRowToTeacher(row.teacherName, selected, allTeachers)
                if (m == TeacherRowMatch.NO) null else row to m
            }.filter { (row, _) -> row.dayOfWeek == dow || row.dayOfWeek == 0 }
    return shown.map { it.first } to shown.any { it.second == TeacherRowMatch.AMBIGUOUS }
}

private fun splitGroups(raw: String): List<String> = raw.split(", ").filter { it.isNotBlank() }

private fun toLesson(
    row: TeacherLessonEntity,
    dow: Int,
): TeacherDayLesson {
    val bell = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { it.lessonNumber == row.lessonNumber }
    return TeacherDayLesson(
        lessonNumber = row.lessonNumber,
        subject = SubjectFormatter.normalize(row.subject),
        groups = splitGroups(row.groups),
        room = row.room,
        start = bell?.start ?: "",
        end = bell?.end ?: "",
        startMinutes = bell?.startMinutes ?: -1,
        endMinutes = bell?.endMinutes ?: -1,
    )
}

private fun toLesson(
    slot: TeacherSlot,
    dow: Int,
): TeacherDayLesson {
    val bell = CollegeBellSchedule.getBellsForDay(dow).firstOrNull { it.lessonNumber == slot.lessonNumber }
    return TeacherDayLesson(
        lessonNumber = slot.lessonNumber,
        subject = SubjectFormatter.normalize(slot.subject),
        groups = slot.groups,
        room = slot.room,
        start = bell?.start ?: "",
        end = bell?.end ?: "",
        startMinutes = bell?.startMinutes ?: -1,
        endMinutes = bell?.endMinutes ?: -1,
    )
}

private const val PROFILE_PREFS = "mpk_teacher_profile"
private const val KEY_LAST_SYNC = "lastSyncEpoch"

object TeacherDayLoader {
    fun readLastSyncEpoch(context: Context): Long = context
        .getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_LAST_SYNC, 0L)

    fun markSyncedNow(context: Context) {
        context
            .getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
    }

    suspend fun loadDay(
        context: Context,
        repository: ScheduleRepository,
        profile: TeacherProfile,
        dateString: String,
        allTeachers: List<Teacher>? = null,
    ): TeacherDay = withContext(Dispatchers.IO) {
        val cal = MpkScheduleParser.parseDotDate(dateString)
        val dow = cal?.let { Dow.of(it) } ?: 0
        val teachers = allTeachers ?: TeachersRepository(context).loadTeachers()
        val selected = Teacher(name = profile.name)
        var ambiguous = false
        val result =
            loadTeacherDay(
                dateKey = dateString,
                fromDatabase = {
                    val (rows, amb) = readMatchedRows(repository, profile, selected, teachers, dateString, dow)
                    ambiguous = amb
                    rows.map {
                        TeacherSlot(it.lessonNumber, splitGroups(it.groups), it.subject, it.room, it.dayOfWeek)
                    }
                },
                sync = {
                    cal?.let { repository.syncTeacherScheduleForDate(it) }
                        ?: Result.failure(IOException("Неверная дата $dateString"))
                },
            )
        TeacherDay(
            lessons = result.slots.map { toLesson(it, dow) },
            ambiguous = ambiguous,
            error = result.error,
            isSynced = repository.hasTeacherScheduleForDate(dateString),
            lastSyncEpoch = readLastSyncEpoch(context),
            date = result.dateKey,
        )
    }

    suspend fun findNextLesson(
        context: Context,
        repository: ScheduleRepository,
        profile: TeacherProfile,
        baseDate: String,
        allTeachers: List<Teacher>? = null,
        maxDays: Int = 7,
    ): NextLesson? = withContext(Dispatchers.IO) {
        val base = MpkScheduleParser.parseDotDate(baseDate) ?: return@withContext null
        val teachers = allTeachers ?: TeachersRepository(context).loadTeachers()
        val selected = Teacher(name = profile.name)
        var anyCached = false
        var firstSchoolCal: Calendar? = null
        for (offset in 1..maxDays) {
            val cal = teacherTargetCalendar(offset, null, base)
            val dow = Dow.of(cal)
            if (dow == 7) continue
            if (firstSchoolCal == null) firstSchoolCal = cal
            val dateKey = DateFormats.dotDate(cal)
            if (repository.hasTeacherScheduleForDate(dateKey)) anyCached = true
            firstRow(repository, profile, selected, teachers, dateKey, dow)
                ?.let { return@withContext NextLesson(dateKey, dow, toLesson(it, dow)) }
        }

        val cal = firstSchoolCal
        if (!anyCached && cal != null) {
            val dateKey = DateFormats.dotDate(cal)
            val dow = Dow.of(cal)
            runCatching { repository.syncTeacherScheduleForDate(cal) }
                .onFailure { if (it is CancellationException) throw it }
            firstRow(repository, profile, selected, teachers, dateKey, dow)
                ?.let { return@withContext NextLesson(dateKey, dow, toLesson(it, dow)) }
        }
        null
    }

    private suspend fun firstRow(
        repository: ScheduleRepository,
        profile: TeacherProfile,
        selected: Teacher,
        allTeachers: List<Teacher>,
        dateString: String,
        dow: Int,
    ): TeacherLessonEntity? = readMatchedRows(repository, profile, selected, allTeachers, dateString, dow).first.firstOrNull()

    private suspend fun readMatchedRows(
        repository: ScheduleRepository,
        profile: TeacherProfile,
        selected: Teacher,
        allTeachers: List<Teacher>,
        dateString: String,
        dow: Int,
    ): Pair<List<TeacherLessonEntity>, Boolean> {
        val key = profile.surnameKey.ifBlank { TeacherInsights.surnameOf(profile.name) }
        val rows = repository.getTeacherLessonsForDate(key, dateString)
        return matchRowsForTeacher(rows, selected, allTeachers, dow)
    }
}
