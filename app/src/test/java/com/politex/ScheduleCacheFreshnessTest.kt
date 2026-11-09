package com.politex

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.ProbeResult
import com.politex.data.network.TeacherSlot
import com.politex.data.repository.FRESH_FUTURE_MS
import com.politex.data.repository.FRESH_TODAY_MS
import com.politex.data.repository.MemoryFetchStamps
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.dayCacheTtlMs
import com.politex.data.repository.isStampedCacheStale
import com.politex.data.repository.nextDayCandidates
import com.politex.data.repository.saturdayIsSchoolDayFor
import com.politex.data.repository.stampKey
import com.politex.util.Dow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Calendar
import java.util.Locale

/**
 * P0-1: «расписание отображается старым».
 *
 * Кэш показывается сразу, но решение «идти ли в сеть за свежим» принимается по штампу последнего
 * успешного фетча: сегодня — 15 минут, будущие дни — 6 часов, прошедшие — окончательны.
 */

/** Календарь на начало суток — как в проде даты в БД без времени. */
private fun day(
    y: Int,
    m: Int,
    d: Int,
): Calendar = Calendar.getInstance().apply {
    clear()
    set(y, m - 1, d)
}

private fun fmt(cal: Calendar): String = String.format(
    Locale.ROOT,
    "%02d.%02d.%04d",
    cal.get(Calendar.DAY_OF_MONTH),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.YEAR),
)

/** DAO, который умеет только отдавать заранее заданные строки и записывать — для проверки «не стёрли». */
private class FreshnessDao(
    val rows: MutableList<LessonEntity> = mutableListOf(),
) : LessonDao {
    val replacedDates = mutableListOf<String>()
    val inserted = mutableListOf<LessonEntity>()
    val deletedDates = mutableListOf<String>()

    override fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName && it.dayOfWeek == dayOfWeek })

    override fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName && it.dateString == dateString })

    override fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName && dateOrDay(it, dateString, dayOfWeek) })

    override fun getLessonsForRoomOnDate(room: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> =
        flowOf(emptyList())

    override fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName })

    override suspend fun getAllLessonsSync(): List<LessonEntity> = rows

    override suspend fun getLessonsForDaySync(groupName: String, dayOfWeek: Int): List<LessonEntity> =
        rows.filter { it.groupName == groupName && it.dayOfWeek == dayOfWeek }

    override suspend fun getDistinctGroupsForDateOrDay(dateString: String, dayOfWeek: Int): List<String> =
        rows.filter { dateOrDay(it, dateString, dayOfWeek) }.map { it.groupName }.distinct().sorted()

    override suspend fun insertLessons(lessons: List<LessonEntity>) {
        inserted += lessons
        rows += lessons
    }

    override suspend fun insertLesson(lesson: LessonEntity): Long {
        inserted += lesson
        rows += lesson
        return 0L
    }

    override suspend fun clearLessonsForGroup(groupName: String) {
        rows.removeAll { it.groupName == groupName }
    }

    override suspend fun deleteLessonsForDate(groupName: String, dateString: String) {
        rows.removeAll { it.groupName == groupName && it.dateString == dateString }
    }

    override suspend fun deleteLessonsForDay(groupName: String, dayOfWeek: Int) = Unit

    override fun getDistinctDatesForGroup(groupName: String): Flow<List<String>> =
        flowOf(rows.filter { it.groupName == groupName && it.dateString != "" }.map { it.dateString }.distinct())

    override suspend fun getLessonCountForGroup(groupName: String): Int = rows.count { it.groupName == groupName }

    override suspend fun getAllDistinctDatesSync(): List<String> =
        rows.map { it.dateString }.filter { it != "" }.distinct()

    override suspend fun getLessonCountForDate(groupName: String, dateString: String): Int =
        rows.count { it.groupName == groupName && it.dateString == dateString }

    override suspend fun deleteLessonsByDate(dateString: String) {
        deletedDates += dateString
        rows.removeAll { it.dateString == dateString }
    }

    override suspend fun deleteLessonsByDates(dates: List<String>) {
        deletedDates += dates
        rows.removeAll { it.dateString in dates }
    }

    override suspend fun replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>) {
        replacedDates += dateString
        inserted += lessons
        rows.removeAll { it.groupName == groupName && it.dateString == dateString }
        rows += lessons
    }

    private fun dateOrDay(l: LessonEntity, dateString: String, dayOfWeek: Int): Boolean =
        (l.dateString != "" && l.dateString == dateString) || (l.dateString == "" && l.dayOfWeek == dayOfWeek)
}

/** Клиент без сети: считает обращения и не отдаёт ни одного документа. */
private class FreshnessClient(
    initialClock: Long = System.currentTimeMillis(),
) : MpkNetworkClient() {
    @Volatile var clock: Long = initialClock

    var probeCalls = 0
    var documentResolutions = 0
    var groupFetches = 0

    override fun nowMillis(): Long = clock

    override suspend fun probeDocument(calendar: Calendar, nameBase: String): ProbeResult {
        probeCalls++
        return ProbeResult.NotFound(404)
    }

    override suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String,
        preferredUrl: String?,
        visit: suspend (String) -> Boolean,
    ) {
        documentResolutions++
    }

    override suspend fun downloadFileBytes(url: String): ByteArray? {
        documentResolutions++
        return null
    }

    override suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar,
        maxRetries: Int,
        preferredUrl: String?,
    ): Result<List<LessonEntity>> {
        groupFetches++
        return Result.failure(IOException("offline test"))
    }

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<TeacherSlot>>> =
        Result.failure(IOException("offline test"))
}

class ScheduleCacheFreshnessTest {
    private val group = "41О"
    private val catalog = listOf(group, "11Э", "31П", "21С")

    private fun lesson(
        date: String,
        number: Int,
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = 0,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Тест",
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = date,
    )

    @Test
    fun `TTL — сегодня короткий, будущее длинное, прошедшее окончательно`() {
        val now = day(2026, 11, 6)

        assertEquals(FRESH_TODAY_MS, dayCacheTtlMs("06.11.2026", now))
        assertEquals(FRESH_FUTURE_MS, dayCacheTtlMs("10.11.2026", now))
        assertNull("прошедший день не обновляем", dayCacheTtlMs("05.11.2026", now))
        assertEquals(FRESH_FUTURE_MS, dayCacheTtlMs("06.11.2027", now))
    }

    @Test
    fun `свежесть сегодняшнего кэша — 15 минут`() {
        val nowMillis = day(2026, 11, 6).timeInMillis + 12 * 60 * 60 * 1000
        val now = day(2026, 11, 6)

        assertFalse(isStampedCacheStale("06.11.2026", nowMillis - 5 * 60_000L, nowMillis, now))
        assertFalse(isStampedCacheStale("06.11.2026", nowMillis - 14 * 60_000L, nowMillis, now))
        assertTrue(isStampedCacheStale("06.11.2026", nowMillis - 20 * 60_000L, nowMillis, now))
        assertTrue(isStampedCacheStale("06.11.2026", nowMillis - 3 * 60 * 60_000L, nowMillis, now))
    }

    @Test
    fun `свежесть будущего дня — 6 часов`() {
        val nowMillis = day(2026, 11, 6).timeInMillis
        val now = day(2026, 11, 6)

        assertFalse(isStampedCacheStale("09.11.2026", nowMillis - 60 * 60_000L, nowMillis, now))
        assertFalse(isStampedCacheStale("09.11.2026", nowMillis - 5 * 60 * 60_000L, nowMillis, now))
        assertTrue(isStampedCacheStale("09.11.2026", nowMillis - 7 * 60 * 60_000L, nowMillis, now))
    }

    @Test
    fun `нет штампа — кэш считается устаревшим (один раз после обновления приложения)`() {
        val nowMillis = day(2026, 11, 6).timeInMillis
        assertTrue(isStampedCacheStale("06.11.2026", null, nowMillis, day(2026, 11, 6)))
    }

    @Test
    fun `прошедший день не устаревает никогда, даже без штампа`() {
        val nowMillis = day(2026, 11, 6).timeInMillis
        assertFalse(isStampedCacheStale("01.09.2026", null, nowMillis, day(2026, 11, 6)))
        assertFalse(isStampedCacheStale("01.09.2026", nowMillis - 90L * 24 * 60 * 60_000L, nowMillis, day(2026, 11, 6)))
    }

    @Test
    fun `устаревший кэш сегодняшнего дня требует обновления`() = runBlocking {
        val todayStr = fmt(Calendar.getInstance())
        val dao = FreshnessDao(mutableListOf(lesson(todayStr, 1), lesson(todayStr, 2)))
        val client = FreshnessClient()
        val stamps = MemoryFetchStamps()
        stamps.put(stampKey(todayStr, group), client.clock - 20 * 60_000L)

        val repo = ScheduleRepository(dao, client, catalogProvider = { catalog }, fetchStamps = stamps)

        assertEquals(listOf(todayStr), repo.staleCachedDays(group))
        val freshness = repo.dayFreshness(group, todayStr)
        assertTrue("кэш есть и он устарел", freshness != null && freshness.stale)
        assertEquals(2, freshness?.lessonCount)
    }

    @Test
    fun `свежий кэш молчит — фоновое обновление не нужно`() = runBlocking {
        val todayStr = fmt(Calendar.getInstance())
        val dao = FreshnessDao(mutableListOf(lesson(todayStr, 1)))
        val client = FreshnessClient()
        val stamps = MemoryFetchStamps()
        stamps.put(stampKey(todayStr, group), client.clock - 2 * 60_000L)

        val repo = ScheduleRepository(dao, client, catalogProvider = { catalog }, fetchStamps = stamps)

        assertTrue("свежий кэш не требует сети", repo.staleCachedDays(group).isEmpty())
        assertFalse(repo.dayFreshness(group, todayStr)?.stale ?: true)
    }

    @Test
    fun `кэша нет — staleCachedDays пуст, первый проход в сеть делает сам синк`() = runBlocking {
        val repo = ScheduleRepository(FreshnessDao(), FreshnessClient(), catalogProvider = { catalog })
        assertTrue(repo.staleCachedDays(group).isEmpty())
        assertNull(repo.dayFreshness(group, fmt(Calendar.getInstance())))
    }

    @Test
    fun `сеть упала при устаревшем кэше — старый кэш НЕ стирается`() = runBlocking {
        val todayStr = fmt(Calendar.getInstance())
        val rows = mutableListOf(lesson(todayStr, 1), lesson(todayStr, 2), lesson(todayStr, 3))
        val dao = FreshnessDao(rows)
        val client = FreshnessClient()
        val stamps = MemoryFetchStamps()
        stamps.put(stampKey(todayStr, group), client.clock - 5 * 60 * 60_000L)

        val repo = ScheduleRepository(dao, client, catalogProvider = { catalog }, fetchStamps = stamps)

        // Принудительное обновление при мёртвой сети: результат неуспешный/пустой, но кэш цел.
        repo.syncScheduleDetailed(group, force = true)

        assertEquals("строки не должны исчезнуть", 3, dao.rows.count { it.groupName == group && it.dateString == todayStr })
        assertTrue("ничего не перезаписывали пустотой", dao.replacedDates.isEmpty())
    }

    @Test
    fun `удачный фетч ставит штамп, и день перестаёт быть устаревшим`() = runBlocking {
        val stamps = MemoryFetchStamps()
        val client = FreshnessClient()
        val repo = ScheduleRepository(FreshnessDao(), client, catalogProvider = { catalog }, fetchStamps = stamps)

        repo.stampDay(group, "06.11.2026")

        val stamp = stamps.get(stampKey("06.11.2026", group))
        assertTrue("штамп записан", stamp != null && stamp > 0)
        assertEquals(stamp, repo.fetchedAt(group, "06.11.2026"))
    }

    @Test
    fun `штампы живут по ключу дата-группа и не мешают друг другу`() = runBlocking {
        val stamps = MemoryFetchStamps()
        val client = FreshnessClient()
        val repo = ScheduleRepository(FreshnessDao(), client, catalogProvider = { catalog }, fetchStamps = stamps)

        repo.stampDay(group, "06.11.2026")
        repo.stampDay("11Э", "06.11.2026")

        assertTrue(repo.fetchedAt(group, "06.11.2026") != null)
        assertTrue(repo.fetchedAt("11Э", "06.11.2026") != null)
        assertNull("чужая дата не получает штамп", repo.fetchedAt(group, "07.11.2026"))
    }

    @Test
    fun `кандидаты автосинка — будущие учебные дни, сегодня среди них нет`() {
        val today = day(2026, 11, 6)
        val candidates = nextDayCandidates(today, saturdayIsSchoolDay = false).map { fmt(it) }
        assertTrue(candidates.isNotEmpty())
        assertTrue("сегодня в кандидатах автосинка быть не должно", fmt(today) !in candidates)
        assertEquals(Dow.of(today), 5)
        assertTrue(saturdayIsSchoolDayFor("41О", existingSaturdayLessons = false) == false)
    }
}