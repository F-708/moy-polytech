package com.politex

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.ProbeResult
import com.politex.data.network.TeacherSlot
import com.politex.data.repository.INCOMPLETE_DAY_TTL_MS
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.cachedOutcomeForDate
import com.politex.data.repository.isCompleteDayDocument
import com.politex.data.repository.isFullDayDocument
import com.politex.data.repository.nextDayCandidates
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
 * P0-2: «день-дырка» — отсутствие группы в НЕПОЛНОМ документе объявлялось доказанным фактом,
 * после чего сеть по этой дате больше не спрашивалась.
 */

private fun holeFmt(cal: Calendar): String = String.format(
    Locale.ROOT,
    "%02d.%02d.%04d",
    cal.get(Calendar.DAY_OF_MONTH),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.YEAR),
)

private class HoleDao : LessonDao {
    val rows = mutableListOf<LessonEntity>()
    val replacedDates = mutableListOf<String>()

    override fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForRoomOnDate(room: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override suspend fun getAllLessonsSync(): List<LessonEntity> = rows
    override suspend fun getLessonsForDaySync(groupName: String, dayOfWeek: Int): List<LessonEntity> = emptyList()
    override suspend fun getDistinctGroupsForDateOrDay(dateString: String, dayOfWeek: Int): List<String> = emptyList()

    override suspend fun insertLessons(lessons: List<LessonEntity>) {
        rows += lessons
    }

    override suspend fun insertLesson(lesson: LessonEntity): Long {
        rows += lesson
        return 0L
    }

    override suspend fun clearLessonsForGroup(groupName: String) = Unit
    override suspend fun deleteLessonsForDate(groupName: String, dateString: String) = Unit
    override suspend fun deleteLessonsForDay(groupName: String, dayOfWeek: Int) = Unit
    override fun getDistinctDatesForGroup(groupName: String): Flow<List<String>> = flowOf(emptyList())
    override suspend fun getLessonCountForGroup(groupName: String): Int = rows.count { it.groupName == groupName }
    override suspend fun getAllDistinctDatesSync(): List<String> = emptyList()
    override suspend fun getLessonCountForDate(groupName: String, dateString: String): Int =
        rows.count { it.groupName == groupName && it.dateString == dateString }

    override suspend fun deleteLessonsByDate(dateString: String) = Unit
    override suspend fun deleteLessonsByDates(dates: List<String>) = Unit

    override suspend fun replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>) {
        replacedDates += dateString
        rows += lessons
    }
}

/** Клиент, который считает именно обращения к документам (probe не в счёт). */
private class HoleClient(
    initialClock: Long = System.currentTimeMillis(),
) : MpkNetworkClient() {
    @Volatile var clock: Long = initialClock

    var probeCalls = 0
    var documentResolutions = 0

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

    override suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar,
        maxRetries: Int,
        preferredUrl: String?,
    ): Result<List<LessonEntity>> = Result.failure(IOException("offline test"))

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<TeacherSlot>>> =
        Result.failure(IOException("offline test"))
}

class ScheduleHoleDayTest {
    private val group = "41О"
    private val catalogSize = 64

    /** Непустой каталог: включает ветку «документ на все группы», где и живёт проверка полноты. */
    private val workingCatalog = (1..64).map { "G$it" }

    @Test
    fun `полнота требует 85 процентов каталога, половина больше не считается`() {
        assertTrue(isCompleteDayDocument(55, catalogSize))
        assertTrue(isCompleteDayDocument(64, catalogSize))
        assertFalse(isCompleteDayDocument(54, catalogSize))
        assertFalse(isCompleteDayDocument(32, catalogSize))
        assertFalse(isCompleteDayDocument(40, catalogSize))
        assertFalse(isCompleteDayDocument(0, catalogSize))
        assertFalse("без каталога полноту доказать нельзя", isCompleteDayDocument(55, 0))
    }

    @Test
    fun `порог записи и порог доказанной полноты — разные вещи`() {
        // Писать частичный документ полезно (isFullDayDocument — половина каталога),
        // но объявлять «вашей группы нет» на его основании нельзя (isCompleteDayDocument — 85%).
        assertTrue(isFullDayDocument(32, catalogSize))
        assertFalse(isCompleteDayDocument(32, catalogSize))
    }

    @Test
    fun `неполный документ без группы не даёт FoundNoGroup из кэша`() {
        assertNull(cachedOutcomeForDate(0, 32, catalogSize, "06.11.2026"))
        assertNull(cachedOutcomeForDate(0, 54, catalogSize, "06.11.2026"))
        assertNull("2 группы — тем более", cachedOutcomeForDate(0, 2, catalogSize, "06.11.2026"))
    }

    @Test
    fun `полный документ без группы даёт FoundNoGroup`() {
        assertEquals(
            SyncOutcome.FoundNoGroup("06.11.2026"),
            cachedOutcomeForDate(0, 55, catalogSize, "06.11.2026"),
        )
        assertEquals(
            SyncOutcome.FoundNoGroup("06.11.2026"),
            cachedOutcomeForDate(0, 64, catalogSize, "06.11.2026"),
        )
    }

    @Test
    fun `уроки группы всегда дают Ok, независимо от полноты документа`() {
        assertEquals(SyncOutcome.Ok(4, "06.11.2026"), cachedOutcomeForDate(4, 2, catalogSize, "06.11.2026"))
    }

    @Test
    fun `метка неполного дня живёт TTL, а не вечно`() = runBlocking {
        val client = HoleClient()
        val repo = ScheduleRepository(HoleDao(), client, catalogProvider = { emptyList() })
        val date = holeFmt(Calendar.getInstance())

        repo.rememberDayCompleteness(date, parsedGroups = 1, catalogSize = catalogSize)
        assertTrue(repo.isKnownIncompleteDay(date))

        client.clock += INCOMPLETE_DAY_TTL_MS - 60_000L
        assertTrue("в пределах TTL ещё помним", repo.isKnownIncompleteDay(date))

        client.clock += 120_000L
        assertFalse("после TTL обязаны спросить сеть снова", repo.isKnownIncompleteDay(date))
    }

    @Test
    fun `полный разбор дня снимает метку неполноты`() = runBlocking {
        val repo = ScheduleRepository(HoleDao(), HoleClient(), catalogProvider = { emptyList() })
        val date = holeFmt(Calendar.getInstance())

        repo.rememberDayCompleteness(date, parsedGroups = 1, catalogSize = catalogSize)
        assertTrue(repo.isKnownIncompleteDay(date))

        repo.rememberDayCompleteness(date, parsedGroups = catalogSize, catalogSize = catalogSize)
        assertFalse(repo.isKnownIncompleteDay(date))
    }

    @Test
    fun `неполный день не докачивается мгновенно, но докачивается после TTL`() = runBlocking {
        val client = HoleClient()
        val repo = ScheduleRepository(HoleDao(), client, catalogProvider = { workingCatalog })

        val today = Calendar.getInstance()
        val dates = (listOf(today) + nextDayCandidates(today, false) + nextDayCandidates(today, true))
            .map { holeFmt(it) }
            .distinct()
        dates.forEach { repo.rememberDayCompleteness(it, parsedGroups = 1, catalogSize = catalogSize) }

        val first = repo.syncScheduleDetailed(group)
        assertEquals("в пределах TTL документы не перекачиваем", 0, client.documentResolutions)
        assertTrue(first.isSuccess)

        client.clock += INCOMPLETE_DAY_TTL_MS + 1_000L
        repo.syncScheduleDetailed(group)
        assertTrue("после TTL документ обязан быть запрошен заново", client.documentResolutions > 0)
    }

    @Test
    fun `force игнорирует метку неполного дня`() = runBlocking {
        val client = HoleClient()
        val repo = ScheduleRepository(HoleDao(), client, catalogProvider = { workingCatalog })

        val today = Calendar.getInstance()
        val dates = (listOf(today) + nextDayCandidates(today, false) + nextDayCandidates(today, true))
            .map { holeFmt(it) }
            .distinct()
        dates.forEach { repo.rememberDayCompleteness(it, parsedGroups = 1, catalogSize = catalogSize) }

        repo.syncScheduleDetailed(group, force = true)
        assertTrue("ручное обновление обязано идти в сеть", client.documentResolutions > 0)
    }
}