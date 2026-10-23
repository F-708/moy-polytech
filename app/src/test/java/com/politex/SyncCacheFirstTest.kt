package com.politex

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.ProbeResult
import com.politex.data.network.TeacherSlot
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.nextDayCandidates
import com.politex.data.repository.saturdayIsSchoolDayFor
import com.politex.data.repository.statusMessageFor
import com.politex.util.Dow
import com.politex.util.GroupParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Calendar
import java.util.Locale

private class CountingNetworkClient : MpkNetworkClient() {
    var networkCalls = 0

    override suspend fun probeDocument(
        calendar: Calendar,
        nameBase: String,
    ): ProbeResult {
        networkCalls++
        return ProbeResult.NotFound(404)
    }

    override suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String,
        preferredUrl: String?,
        visit: suspend (String) -> Boolean,
    ) {
        networkCalls++
    }

    override suspend fun downloadFileBytes(url: String): ByteArray? {
        networkCalls++
        return null
    }

    override suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar,
        maxRetries: Int,
        preferredUrl: String?,
    ): Result<List<LessonEntity>> {
        networkCalls++
        return Result.failure(IOException("offline test"))
    }

    override suspend fun fetchTeacherSchedule(
        targetCalendar: Calendar,
    ): Result<Map<String, List<TeacherSlot>>> {
        networkCalls++
        return Result.failure(IOException("offline test"))
    }
}

private class FakeLessonDao(
    private val rows: List<LessonEntity>,
) : LessonDao {
    override fun getLessonsForDay(
        groupName: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = flowOf(rows.filter { it.groupName == groupName && it.dayOfWeek == dayOfWeek })

    override fun getLessonsForDate(
        groupName: String,
        dateString: String,
    ): Flow<List<LessonEntity>> = flowOf(rows.filter { it.groupName == groupName && it.dateString == dateString })

    override fun getLessonsForDateOrDay(
        groupName: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = flowOf(rows.filter { it.groupName == groupName && dateOrDay(it, dateString, dayOfWeek) })

    override fun getLessonsForRoomOnDate(
        room: String,
        dateString: String,
        dayOfWeek: Int,
    ): Flow<List<LessonEntity>> = flowOf(emptyList())

    override fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> = flowOf(rows.filter { it.groupName == groupName })

    override suspend fun getAllLessonsSync(): List<LessonEntity> = rows

    override suspend fun getLessonsForDaySync(
        groupName: String,
        dayOfWeek: Int,
    ): List<LessonEntity> = rows.filter { it.groupName == groupName && it.dayOfWeek == dayOfWeek }

    override suspend fun getDistinctGroupsForDateOrDay(
        dateString: String,
        dayOfWeek: Int,
    ): List<String> = rows.filter { dateOrDay(it, dateString, dayOfWeek) }.map { it.groupName }.distinct().sorted()

    override suspend fun insertLessons(lessons: List<LessonEntity>) = Unit

    override suspend fun insertLesson(lesson: LessonEntity): Long = 0L

    override suspend fun clearLessonsForGroup(groupName: String) = Unit

    override suspend fun deleteLessonsForDate(
        groupName: String,
        dateString: String,
    ) = Unit

    override suspend fun deleteLessonsForDay(
        groupName: String,
        dayOfWeek: Int,
    ) = Unit

    override fun getDistinctDatesForGroup(groupName: String): Flow<List<String>> = flowOf(rows.filter { it.groupName == groupName && it.dateString != "" }.map { it.dateString }.distinct())

    override suspend fun getLessonCountForGroup(groupName: String): Int = rows.count { it.groupName == groupName }

    override suspend fun getAllDistinctDatesSync(): List<String> = rows.map { it.dateString }.filter { it != "" }.distinct()

    override suspend fun getLessonCountForDate(
        groupName: String,
        dateString: String,
    ): Int = rows.count { it.groupName == groupName && it.dateString == dateString }

    override suspend fun deleteLessonsByDate(dateString: String) = Unit
    override suspend fun deleteLessonsByDates(dates: List<String>) = Unit

    private fun dateOrDay(
        l: LessonEntity,
        dateString: String,
        dayOfWeek: Int,
    ): Boolean = (l.dateString != "" && l.dateString == dateString) || (l.dateString == "" && l.dayOfWeek == dayOfWeek)
}

class SyncCacheFirstTest {
    private val group = "41О"
    private val catalog = listOf(group, "11Э", "31П", "21С")

    private fun fmt(cal: Calendar): String = String.format(
        Locale.ROOT,
        "%02d.%02d.%04d",
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR),
    )

    private fun lesson(
        groupName: String,
        cal: Calendar,
        num: Int,
    ) = LessonEntity(
        groupName = groupName,
        dayOfWeek = Dow.of(cal),
        lessonNumber = num,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Тест",
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = fmt(cal),
    )

    private fun repo(
        rows: List<LessonEntity>,
        client: MpkNetworkClient,
    ) = ScheduleRepository(FakeLessonDao(rows), client, catalogProvider = { catalog })

    @Test
    fun `уроки даты уже в БД — Ok из кэша, сеть не тронута`() = runBlocking {
        val today = Calendar.getInstance()

        val candidates = (nextDayCandidates(today, false) + nextDayCandidates(today, true)).distinctBy { fmt(it) }
        val rows = buildList {
            (1..3).forEach { add(lesson(group, today, it)) }
            candidates.forEach { cal -> (1..5).forEach { add(lesson(group, cal, it)) } }
        }
        val sat = saturdayIsSchoolDayFor(group, rows.any { it.dayOfWeek == 6 })

        val expected = SyncOutcome.Ok(5, fmt(nextDayCandidates(today, sat).first()))

        val client = CountingNetworkClient()
        val result = repo(rows, client).syncScheduleDetailed(group)

        assertEquals(0, client.networkCalls)
        assertEquals(expected, result.getOrNull())
        assertEquals(statusMessageFor(expected), client.diagnosticInfo.value.statusMessage)
        assertFalse(client.diagnosticInfo.value.isSyncing)
    }

    @Test
    fun `день разобран по каталогу без нашей группы — FoundNoGroup из кэша, сеть не тронута`() = runBlocking {
        val today = Calendar.getInstance()

        val sat = GroupParser.parse(group)?.hasSaturdayClasses == true
        val candidates = nextDayCandidates(today, sat)
        val dates = listOf(today) + candidates
        val rows = dates.flatMap { cal -> catalog.filter { it != group }.mapIndexed { i, g -> lesson(g, cal, i + 1) } }

        val client = CountingNetworkClient()
        val result = repo(rows, client).syncScheduleDetailed(group)

        assertEquals(0, client.networkCalls)
        assertEquals(SyncOutcome.FoundNoGroup(fmt(candidates.first())), result.getOrNull())
    }

    @Test
    fun `force обходит кэш и идёт в сеть`() = runBlocking {
        val today = Calendar.getInstance()
        val candidates = (nextDayCandidates(today, false) + nextDayCandidates(today, true)).distinctBy { fmt(it) }
        val rows = buildList {
            (1..3).forEach { add(lesson(group, today, it)) }
            candidates.forEach { cal -> (1..5).forEach { add(lesson(group, cal, it)) } }
        }

        val client = CountingNetworkClient()
        repo(rows, client).syncScheduleDetailed(group, force = true)

        assertTrue(client.networkCalls > 0)
    }

    @Test
    fun `пустой кэш — сеть вызывается и статус финальный честный`() = runBlocking {
        val today = Calendar.getInstance()
        val sat = saturdayIsSchoolDayFor(group, false)
        val targetDate = fmt(nextDayCandidates(today, sat).last())

        val client = CountingNetworkClient()
        val result = repo(emptyList(), client).syncScheduleDetailed(group)

        assertTrue(client.networkCalls > 0)
        assertEquals(SyncOutcome.NotFound(targetDate), result.getOrNull())
        assertEquals("Расписание на $targetDate ещё не опубликовано", client.diagnosticInfo.value.statusMessage)
    }
}
