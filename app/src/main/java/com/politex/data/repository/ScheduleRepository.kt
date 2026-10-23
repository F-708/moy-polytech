package com.politex.data.repository

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.ProbeResult
import com.politex.data.network.TeacherSlot
import com.politex.util.DateFormats
import com.politex.util.Dow
import com.politex.util.GroupParser
import com.politex.util.lessonsWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

private val DATE_DD_MM_YYYY = Regex("""\d{2}\.\d{2}\.\d{4}""")

private const val ARCHIVE_KEEP_DAYS = 60L

internal fun shouldFetchDay(
    alreadyParsedGroups: Int,
    catalogSize: Int,
    force: Boolean,
): Boolean = catalogSize > 0 && (force || alreadyParsedGroups < (catalogSize + 1) / 2)

internal fun isFullDayDocument(
    groupCount: Int,
    catalogSize: Int,
): Boolean = catalogSize > 0 && groupCount >= (catalogSize + 1) / 2

internal fun cachedOutcomeForDate(
    groupLessons: Int,
    parsedGroups: Int,
    catalogSize: Int,
    date: String,
): SyncOutcome? = when {
    groupLessons > 0 -> SyncOutcome.Ok(groupLessons, date)
    isFullDayDocument(parsedGroups, catalogSize) -> SyncOutcome.FoundNoGroup(date)
    else -> null
}

internal fun incompleteDayMark(
    parsedGroups: Int,
    catalogSize: Int,
): Boolean? = when {
    parsedGroups <= 0 -> null
    isFullDayDocument(parsedGroups, catalogSize) -> false
    else -> true
}

internal fun lessonsForWrite(
    lessons: List<LessonEntity>,
    allowedDates: Set<String>,
): List<LessonEntity> = lessons.filter { it.dateString.isBlank() || it.dateString in allowedDates }

internal fun combineCachedOutcomes(outcomes: List<SyncOutcome?>): SyncOutcome? {
    if (outcomes.isEmpty() || outcomes.any { it == null }) return null
    return outcomes.first()
}

internal fun syncedCountForGroup(
    finalLessons: List<LessonEntity>,
    groupName: String,
    date: String,
): Int = finalLessons.count { it.groupName == groupName && (it.dateString == date || it.dateString.isBlank()) }

sealed class SyncOutcome {
    abstract val count: Int

    abstract val date: String

    data class NotFound(override val date: String) : SyncOutcome() {
        override val count = 0
    }

    data class FoundNoGroup(override val date: String) : SyncOutcome() {
        override val count = 0
    }

    data class Ok(override val count: Int, override val date: String) : SyncOutcome()
}

internal fun resolveSyncOutcome(
    groupLessons: Int,
    documentFound: Boolean,
    targetDate: String,
): SyncOutcome = when {
    groupLessons > 0 -> SyncOutcome.Ok(groupLessons, targetDate)
    documentFound -> SyncOutcome.FoundNoGroup(targetDate)
    else -> SyncOutcome.NotFound(targetDate)
}

internal fun statusMessageFor(outcome: SyncOutcome): String = when (outcome) {
    is SyncOutcome.Ok -> "Расписание на ${outcome.date} загружено (${outcome.count} ${lessonsWord(outcome.count)})"
    is SyncOutcome.FoundNoGroup -> "Расписание на ${outcome.date} опубликовано, но вашей группы в нём пока нет"
    is SyncOutcome.NotFound -> "Расписание на ${outcome.date} ещё не опубликовано"
}

internal fun toastFor(outcome: SyncOutcome, group: String): String = when (outcome) {
    is SyncOutcome.Ok -> "Расписание группы $group на ${outcome.date} обновлено (${outcome.count} ${lessonsWord(outcome.count)})"
    is SyncOutcome.FoundNoGroup -> "Расписание на ${outcome.date} опубликовано, но вашей группы ($group) в нём пока нет"
    is SyncOutcome.NotFound -> "Расписание на ${outcome.date} ещё не опубликовано"
}

internal fun checkingStatusFor(date: String): String = "Проверяю расписание на $date…"

internal fun emptyStateText(
    forDate: String,
    lastOutcomeDate: String = "",
    outcome: SyncOutcome? = null,
): String = if (outcome is SyncOutcome.Ok && outcome.count > 0 && lastOutcomeDate == forDate) {
    "Расписание на $forDate загружено"
} else {
    "Расписания на этот день нет"
}

internal fun isStaleDate(
    date: String,
    cutoffMs: Long,
): Boolean {
    if (!DATE_DD_MM_YYYY.matches(date)) return false
    val cal = Calendar.getInstance()
    cal.clear()
    cal.set(
        date.substring(6, 10).toInt(),
        date.substring(3, 5).toInt() - 1,
        date.substring(0, 2).toInt(),
    )
    return cal.timeInMillis < cutoffMs
}

internal fun nextSchoolDay(
    base: Calendar,
    saturdayIsSchoolDay: Boolean,
): Calendar {
    val cal = (base.clone() as Calendar)
    do {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    } while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY ||
        (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY && !saturdayIsSchoolDay)
    )
    return cal
}

internal fun saturdayIsSchoolDayFor(
    groupName: String,
    existingSaturdayLessons: Boolean,
): Boolean = GroupParser.parse(groupName)?.hasSaturdayClasses == true || existingSaturdayLessons

data class NextDayTarget(val date: Calendar?, val label: String)

internal fun nextSchoolDayWithFallback(
    today: Calendar,
    saturdayIsSchoolDay: Boolean,
    hasSchedule: (Calendar) -> Boolean,
): NextDayTarget {
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val tomorrowDow = Dow.of(tomorrow)
    if (tomorrowDow in 1..5 || (tomorrowDow == 6 && saturdayIsSchoolDay)) return NextDayTarget(tomorrow, "Завтра")
    if (tomorrowDow == 6 && hasSchedule(tomorrow)) return NextDayTarget(tomorrow, "Завтра")
    val monday = (today.clone() as Calendar).apply {
        do {
            add(Calendar.DAY_OF_YEAR, 1)
        } while (Dow.of(this) != 1)
    }
    return if (hasSchedule(monday)) NextDayTarget(monday, "Понедельник") else NextDayTarget(null, "Понедельник")
}

internal fun nextDayCandidates(
    today: Calendar,
    saturdayIsSchoolDay: Boolean,
): List<Calendar> {
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val monday = (today.clone() as Calendar).apply {
        do {
            add(Calendar.DAY_OF_YEAR, 1)
        } while (Dow.of(this) != 1)
    }
    val dow = Dow.of(tomorrow)
    return when {
        dow in 1..5 || (dow == 6 && saturdayIsSchoolDay) -> listOf(tomorrow)
        dow == 6 -> listOf(tomorrow, monday)
        else -> listOf(monday)
    }
}

internal fun looksLikeDocument(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val docx = bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
    val doc = bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
    return docx || doc
}

internal fun teacherRows(
    parsedAll: Map<String, List<TeacherSlot>>,
    date: String,
    requestedDow: Int,
): List<TeacherLessonEntity> = parsedAll
    .flatMap { (rawName, slots) ->

        TeacherInsights.splitTeacherNames(rawName).flatMap { name ->
            val surname = TeacherInsights.surnameOf(name)
            if (surname.isBlank()) {
                emptyList()
            } else {
                slots.map { slot ->
                    TeacherLessonEntity(
                        teacherName = name,
                        surnameKey = surname,
                        dateString = date,
                        dayOfWeek = if (slot.dayOfWeek != 0) slot.dayOfWeek else requestedDow,
                        lessonNumber = slot.lessonNumber,
                        groups = slot.groups.joinToString(", "),
                        subject = slot.subject,
                        room = slot.room,
                    )
                }
            }
        }
    }
    .distinctBy { "${it.surnameKey}_${it.dayOfWeek}_${it.lessonNumber}_${it.room}_${it.subject}_${it.groups}" }

class ScheduleRepository(
    private val lessonDao: LessonDao,
    private val networkClient: MpkNetworkClient = MpkNetworkClient(),

    private val catalogProvider: () -> List<String> = { emptyList() },

    private val teacherLessonDao: TeacherLessonDao? = null,
) {
    val diagnosticInfo = networkClient.diagnosticInfo

    @Volatile
    private var cachedCatalog: List<String>? = null

    private val incompleteDayCache = ConcurrentHashMap<String, Boolean>()

    internal fun isKnownIncompleteDay(date: String): Boolean = incompleteDayCache[date] == true

    internal fun rememberDayCompleteness(
        date: String,
        parsedGroups: Int,
        catalogSize: Int,
    ) {
        when (incompleteDayMark(parsedGroups, catalogSize)) {
            true -> incompleteDayCache[date] = true
            false -> incompleteDayCache.remove(date)
            null -> Unit
        }
    }

    internal fun catalog(): List<String> = cachedCatalog ?: catalogProvider().takeIf { it.isNotEmpty() }?.also { cachedCatalog = it } ?: emptyList()

    fun resetCatalog() {
        cachedCatalog = null
    }

    suspend fun syncDayAllGroupsCached(
        date: String,
        dow: Int,
        force: Boolean = false,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val groups = catalog()
        if (groups.isEmpty()) return@withContext Result.failure(IOException("Каталог групп пуст"))

        val already = lessonDao.getDistinctGroupsForDateOrDay(date, dow).size
        if (!shouldFetchDay(already, groups.size, force)) {
            return@withContext Result.success(already)
        }
        syncDayAllGroups(date, dow, groups)
    }

    fun getLessonsForDay(
        groupName: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForDay(groupName, dayOfWeek).flowOn(Dispatchers.IO)

    fun getLessonsForDate(
        groupName: String,
        dateString: String,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForDate(groupName, dateString).flowOn(Dispatchers.IO)

    fun getLessonsForDateOrDay(
        groupName: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForDateOrDay(groupName, dateString, dayOfWeek).flowOn(Dispatchers.IO)

    fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> = lessonDao.getAllLessonsForGroup(groupName).flowOn(Dispatchers.IO)

    fun getDistinctDates(groupName: String): Flow<List<String>> = lessonDao.getDistinctDatesForGroup(groupName).flowOn(Dispatchers.IO)

    fun getLessonsForRoomOnDate(
        room: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = lessonDao.getLessonsForRoomOnDate(room, dateString, dayOfWeek).flowOn(Dispatchers.IO)

    suspend fun getDistinctGroupsForDateOrDay(
        dateString: String,
        dayOfWeek: Int,
    ): List<String> = withContext(Dispatchers.IO) {
        lessonDao.getDistinctGroupsForDateOrDay(dateString, dayOfWeek)
    }

    suspend fun syncScheduleFromWeb(
        groupName: String,
        force: Boolean = false,
    ): Result<Int> = syncScheduleDetailed(groupName, force)
        .fold({ Result.success(it.count) }, { Result.failure(it) })

    suspend fun syncScheduleDetailed(
        groupName: String,
        force: Boolean = false,
        targetDate: String? = null,
    ): Result<SyncOutcome> = withContext(Dispatchers.IO) {
        val today = Calendar.getInstance()

        val saturdaySchool = saturdayIsSchoolDayFor(
            groupName,
            lessonDao.getAllLessonsForGroup(groupName).first().any { it.dayOfWeek == 6 },
        )
        val targetCal = targetDate?.let { parseCalendarDate(it) }
        val candidates = targetCal?.let { listOf(it) } ?: nextDayCandidates(today, saturdaySchool)
        val todayStr = formatDate(today)

        if (!force) {
            val candidateOutcome = candidates.firstNotNullOfOrNull { cachedDateOutcome(groupName, it) }
            if (candidateOutcome != null) {
                val todayDow = Dow.of(today)
                val todayIsSchoolDay = todayDow in 1..5 || (todayDow == 6 && saturdaySchool)
                val todayOutcome = if (todayIsSchoolDay) cachedDateOutcome(groupName, today) else SyncOutcome.Ok(0, todayStr)
                val cached = combineCachedOutcomes(listOf(candidateOutcome, todayOutcome))
                if (cached != null) {
                    networkClient.publishStatus(statusMessageFor(cached), isSyncing = false)
                    return@withContext Result.success(cached)
                }
            }
        }

        networkClient.publishStatus(statusMessage = checkingStatusFor(formatDate(candidates.first())), isSyncing = true)
        var probed: Calendar? = candidates.firstOrNull { !force && cachedDateOutcome(groupName, it) != null }
        var probeNetworkError = false

        val probeFoundUrl = mutableMapOf<String, String>()
        if (probed == null) {
            for (candidate in candidates) {
                when (val probe = networkClient.probeDocument(candidate)) {
                    is ProbeResult.Found -> {
                        probed = candidate
                        probeFoundUrl[formatDate(candidate)] = probe.url
                        break
                    }
                    ProbeResult.NetworkError -> probeNetworkError = true
                    is ProbeResult.NotFound -> Unit
                }
            }
        }
        val tomorrow: Calendar = probed ?: candidates.last()
        val tomorrowStr = formatDate(tomorrow)

        val needToday = force || cachedDateOutcome(groupName, today) == null
        val needTomorrow = force || cachedDateOutcome(groupName, tomorrow) == null

        val fetched = mutableListOf<LessonEntity>()
        var fetchFailed = false
        var documentFound = false

        var targetDocumentFound = false

        val allGroups = (catalog() + groupName).distinct()
        val useCatalog = catalog().isNotEmpty()
        val days = listOf(tomorrow to needTomorrow, today to needToday)
            .filter { it.second }
            .distinctBy { formatDate(it.first) }

        if (!force && needTomorrow && days.isNotEmpty() && days.all { incompleteDayCache[formatDate(it.first)] == true }) {
            val incomplete = SyncOutcome.FoundNoGroup(tomorrowStr)
            networkClient.publishStatus(statusMessage = statusMessageFor(incomplete), isSyncing = false)
            return@withContext Result.success(incomplete)
        }

        if (useCatalog) {
            for ((calendar, _) in days) {
                val date = formatDate(calendar)

                runCatching {
                    fetchAllGroupsForDate(calendar, date, allGroups, probeFoundUrl[date]) { found ->
                        if (found) {
                            documentFound = true
                            if (date == tomorrowStr) targetDocumentFound = true
                        }
                    }
                }
                    .onSuccess { dayLessons ->

                        val dayGroups = dayLessons.map { it.groupName }.distinct().size
                        if (isFullDayDocument(dayGroups, allGroups.size)) fetched += dayLessons

                        rememberDayCompleteness(date, dayGroups, allGroups.size)
                    }.onFailure { fetchFailed = true }
            }
        } else {
            if (needTomorrow) {
                networkClient
                    .fetchScheduleForGroup(groupName, tomorrow, preferredUrl = probeFoundUrl[tomorrowStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
            if (needToday && todayStr != tomorrowStr) {
                networkClient
                    .fetchScheduleForGroup(groupName, today, preferredUrl = probeFoundUrl[todayStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
        }

        val distinct =
            fetched
                .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }

        if (distinct.none { it.groupName == groupName } && documentFound) {
            if (needTomorrow) {
                networkClient
                    .fetchScheduleForGroup(groupName, tomorrow, preferredUrl = probeFoundUrl[tomorrowStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
            if (needToday && todayStr != tomorrowStr) {
                networkClient
                    .fetchScheduleForGroup(groupName, today, preferredUrl = probeFoundUrl[todayStr])
                    .onSuccess { fetched += it }
                    .onFailure { fetchFailed = true }
            }
        }

        val finalLessons =
            fetched
                .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }

        if (finalLessons.isNotEmpty()) {
            writeByGroup(finalLessons, days.map { formatDate(it.first) }.toSet())

            days.forEach { (calendar, _) ->
                runCatching { syncTeacherScheduleForDate(calendar) }
                    .onFailure { if (it is kotlin.coroutines.cancellation.CancellationException) throw it }
            }
            pruneOldLessons()
        }

        val groupCount = syncedCountForGroup(finalLessons, groupName, tomorrowStr)

        val outcome = if (!needTomorrow) {
            cachedDateOutcome(groupName, tomorrow) ?: resolveSyncOutcome(groupCount, targetDocumentFound, tomorrowStr)
        } else {
            resolveSyncOutcome(groupCount, targetDocumentFound, tomorrowStr)
        }

        val offlineDead = probeNetworkError && !documentFound && fetched.isEmpty()
        return@withContext if (outcome is SyncOutcome.NotFound && (fetchFailed || offlineDead)) {
            networkClient.publishStatus(statusMessage = "Не удалось получить расписание на $tomorrowStr", isSyncing = false)
            Result.failure(IOException("Не удалось получить расписание"))
        } else {
            networkClient.publishStatus(statusMessage = statusMessageFor(outcome), isSyncing = false)
            Result.success(outcome)
        }
    }

    suspend fun probeDate(calendar: Calendar): ProbeResult = networkClient.probeDocument(calendar)

    private suspend fun cachedDateOutcome(
        groupName: String,
        cal: Calendar,
    ): SyncOutcome? {
        val catalogSize = catalog().size
        val date = formatDate(cal)
        val parsedGroups =
            if (catalogSize == 0) 0 else lessonDao.getDistinctGroupsForDateOrDay(date, Dow.of(cal)).size
        return cachedOutcomeForDate(lessonDao.getLessonCountForDate(groupName, date), parsedGroups, catalogSize, date)
    }

    suspend fun syncScheduleForDate(
        groupName: String,
        targetCalendar: Calendar,
    ): Result<List<LessonEntity>> = withContext(Dispatchers.IO) {
        val allGroups = (catalog() + groupName).distinct()
        if (allGroups.isEmpty()) return@withContext networkClient.fetchScheduleForGroup(groupName, targetCalendar)

        val date = formatDate(targetCalendar)
        var documentFound = false
        val forGroup =
            runCatching {
                fetchAllGroupsForDate(targetCalendar, date, allGroups) { documentFound = it }
            }
        if (forGroup.isFailure) return@withContext Result.failure(forGroup.exceptionOrNull()!!)
        val all = forGroup.getOrDefault(emptyList())
        if (all.isNotEmpty()) {
            writeByGroup(all, setOf(date))
            pruneOldLessons()
        }

        val mine = all.filter { it.groupName == groupName }

        if (mine.isEmpty() && documentFound) {
            return@withContext networkClient.fetchScheduleForGroup(groupName, targetCalendar)
        }

        if (mine.isEmpty() && !documentFound) {
            return@withContext Result.failure(IOException("Документ на $date не найден"))
        }
        Result.success(mine)
    }

    private suspend fun fetchAllGroupsForDate(
        calendar: Calendar,
        date: String,
        candidateGroups: List<String>,
        preferredUrl: String? = null,
        documentFound: (Boolean) -> Unit = {},
    ): List<LessonEntity> {
        var found: List<LessonEntity> = emptyList()
        networkClient.forEachDocumentUrl(calendar, preferredUrl = preferredUrl) { url ->
            val bytes = networkClient.downloadFileBytes(url)
            if (bytes == null || bytes.isEmpty()) return@forEachDocumentUrl false

            if (looksLikeDocument(bytes)) {
                if (!MpkScheduleParser.documentMatchesDate(bytes, date)) return@forEachDocumentUrl false
                documentFound(true)
            }
            val parsed = MpkScheduleParser.parseFileAllGroups(bytes, date, candidateGroups)
            if (parsed.isNotEmpty()) {
                found = parsed
                true
            } else {
                false
            }
        }
        return found
    }

    private suspend fun writeByGroup(
        lessons: List<LessonEntity>,
        allowedDates: Set<String>,
    ) {
        lessonsForWrite(lessons, allowedDates)
            .groupBy { it.groupName }
            .forEach { (group, groupLessons) ->
                if (group.isNotBlank()) replaceSyncedLessons(group, groupLessons)
            }
    }

    suspend fun syncTeacherScheduleForDate(
        targetCalendar: Calendar,
        force: Boolean = false,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val dao = teacherLessonDao ?: return@withContext Result.success(0)
        val date = formatDate(targetCalendar)

        if (!force && dao.getPresentDates(listOf(date)).contains(date)) {
            return@withContext Result.success(
                dao.getTeachersOnDate(date).size,
            )
        }

        val fetched = networkClient.fetchTeacherSchedule(targetCalendar)
        val parsedAll = fetched.getOrElse { return@withContext Result.failure(it) }
        if (parsedAll.isEmpty()) return@withContext Result.success(0)

        val requestedDow = MpkScheduleParser.dowOfCalendar(targetCalendar)
        val docDow =
            parsedAll.values
                .firstOrNull()
                ?.firstOrNull()
                ?.dayOfWeek ?: 0

        if (docDow != 0 && docDow != requestedDow) {
            return@withContext Result.failure(IOException("Документ за другой день"))
        }

        val rows = teacherRows(parsedAll, date, requestedDow)
        if (rows.isEmpty()) {
            return@withContext Result.failure(IOException("Не удалось разобрать расписание преподавателей"))
        }

        dao.replaceForDate(date, rows)
        Result.success(rows.size)
    }

    suspend fun getTeacherLessonsForDate(
        teacherSurname: String,
        dateString: String,
    ): List<TeacherLessonEntity> = withContext(Dispatchers.IO) {
        val dao = teacherLessonDao ?: return@withContext emptyList()
        dao.getForSurnameOnDate(teacherSurname, dateString)
    }

    suspend fun hasTeacherScheduleForDate(dateString: String): Boolean = withContext(Dispatchers.IO) {
        val dao = teacherLessonDao ?: return@withContext false
        dao.getPresentDates(listOf(dateString)).isNotEmpty()
    }

    suspend fun syncDayAllGroups(
        date: String,
        dow: Int,
        candidateGroups: List<String> = emptyList(),
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (candidateGroups.isEmpty()) return@withContext Result.success(0)
        val calendar = parseCalendarDate(date) ?: return@withContext Result.success(0)

        var documentFound = false
        val fetched =
            try {
                fetchAllGroupsForDate(calendar, date, candidateGroups) { documentFound = it }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

        if (fetched.isEmpty()) {
            return@withContext if (documentFound) {
                Result.success(0)
            } else {
                Result.failure(IOException("Расписание на $date не опубликовано"))
            }
        }

        val forDate =
            fetched
                .filter { it.dateString == date }
                .distinctBy { "${it.groupName}_${it.dayOfWeek}_${it.lessonNumber}_${it.dateString}" }
                .groupBy { it.groupName }

        var written = 0
        for ((group, lessons) in forDate) {
            replaceSyncedLessons(group, lessons)
            written += lessons.size
        }

        pruneOldLessons()
        Result.success(written)
    }

    private fun parseCalendarDate(date: String): Calendar? {
        if (!DATE_DD_MM_YYYY.matches(date)) return null
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(date.substring(6, 10).toInt(), date.substring(3, 5).toInt() - 1, date.substring(0, 2).toInt())
        return cal
    }

    suspend fun pruneOldLessons(keepDays: Long = ARCHIVE_KEEP_DAYS) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - keepDays * 24L * 60L * 60L * 1000L
        val staleDates = lessonDao.getAllDistinctDatesSync().filter { isStaleDate(it, cutoff) }
        if (staleDates.isEmpty()) return@withContext
        lessonDao.deleteLessonsByDates(staleDates)
        teacherLessonDao?.deleteForDates(staleDates)
    }

    internal fun formatDate(calendar: Calendar): String = DateFormats.dotDate(calendar)

    suspend fun replaceSyncedLessons(
        groupName: String,
        lessons: List<LessonEntity>,
    ) = withContext(Dispatchers.IO) {
        lessons.groupBy { it.dateString }.forEach { (dateString, dayLessons) ->
            if (dateString.isBlank()) {
                lessonDao.insertLessons(dayLessons)
            } else {
                lessonDao.replaceForDate(groupName, dateString, dayLessons)
            }
        }
    }

    suspend fun saveLessons(lessons: List<LessonEntity>) = withContext(Dispatchers.IO) {
        lessonDao.insertLessons(lessons)
    }

    suspend fun replaceSchedule(
        groupName: String,
        lessons: List<LessonEntity>,
    ) = withContext(Dispatchers.IO) {
        lessonDao.replaceScheduleForGroup(groupName, lessons)
    }

    suspend fun clearSchedule(groupName: String) = withContext(Dispatchers.IO) {
        lessonDao.clearLessonsForGroup(groupName)
    }

    suspend fun hasSchedule(groupName: String): Boolean = withContext(Dispatchers.IO) {
        lessonDao.getLessonCountForGroup(groupName) > 0
    }
}
