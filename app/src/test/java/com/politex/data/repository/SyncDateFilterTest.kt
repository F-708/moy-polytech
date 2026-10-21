package com.politex.data.repository

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.ProbeResult
import com.politex.data.network.TeacherSlot
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

private fun fmt(cal: Calendar): String =
    String.format(
        Locale.ROOT,
        "%02d.%02d.%04d",
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR),
    )

private fun lesson(
    group: String,
    date: String,
    num: Int,
    dow: Int = 1,
) = LessonEntity(
    groupName = group,
    dayOfWeek = dow,
    lessonNumber = num,
    timeStart = "08:15",
    timeEnd = "09:00",
    subjectRaw = "Тест",
    roomFirst = "204",
    teacherFirst = "",
    dateString = date,
)

private class RecordingLessonDao : LessonDao {
    val replacedDates = mutableListOf<String>()
    val inserted = mutableListOf<LessonEntity>()

    override fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForRoomOnDate(room: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override suspend fun getAllLessonsSync(): List<LessonEntity> = emptyList()
    override suspend fun getLessonsForDaySync(groupName: String, dayOfWeek: Int): List<LessonEntity> = emptyList()
    override suspend fun getDistinctGroupsForDateOrDay(dateString: String, dayOfWeek: Int): List<String> = emptyList()
    override suspend fun insertLessons(lessons: List<LessonEntity>) { inserted += lessons }
    override suspend fun insertLesson(lesson: LessonEntity): Long = 0L
    override suspend fun clearLessonsForGroup(groupName: String) = Unit
    override suspend fun deleteLessonsForDate(groupName: String, dateString: String) = Unit
    override suspend fun deleteLessonsForDay(groupName: String, dayOfWeek: Int) = Unit
    override fun getDistinctDatesForGroup(groupName: String): Flow<List<String>> = flowOf(emptyList())
    override suspend fun getLessonCountForGroup(groupName: String): Int = 0
    override suspend fun getAllDistinctDatesSync(): List<String> = emptyList()
    override suspend fun getLessonCountForDate(groupName: String, dateString: String): Int = 0
    override suspend fun deleteLessonsByDate(dateString: String) = Unit
    override suspend fun deleteLessonsByDates(dates: List<String>) = Unit

    override suspend fun replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>) {
        replacedDates += dateString
        inserted += lessons
    }
}

private class RestampingClient : MpkNetworkClient() {
    override suspend fun probeDocument(calendar: Calendar, nameBase: String): ProbeResult = ProbeResult.NotFound(404)

    override suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String,
        preferredUrl: String?,
        visit: suspend (String) -> Boolean,
    ) = Unit

    override suspend fun downloadFileBytes(url: String): ByteArray? = null

    override suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar,
        maxRetries: Int,
        preferredUrl: String?,
    ): Result<List<LessonEntity>> =
        Result.success(
            listOf(
                lesson(targetGroup, fmt(targetCalendar), 1),
                lesson(targetGroup, "01.09.2020", 2),
            ),
        )

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<TeacherSlot>>> =
        Result.failure(IOException("offline"))
}

private class OfflineClient : MpkNetworkClient() {
    var downloadAttempts = 0

    override suspend fun probeDocument(calendar: Calendar, nameBase: String): ProbeResult = ProbeResult.NetworkError

    override suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String,
        preferredUrl: String?,
        visit: suspend (String) -> Boolean,
    ) {
        downloadAttempts++
    }

    override suspend fun downloadFileBytes(url: String): ByteArray? {
        downloadAttempts++
        return null
    }

    override suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar,
        maxRetries: Int,
        preferredUrl: String?,
    ): Result<List<LessonEntity>> = Result.failure(IOException("offline"))

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<TeacherSlot>>> =
        Result.failure(IOException("offline"))
}

class SyncDateFilterTest {
    private val group = "41О"

    private fun tomorrowStr(today: Calendar = Calendar.getInstance()): String =
        fmt(nextDayCandidates(today, saturdayIsSchoolDayFor(group, false)).last())

    @Test
    fun `lessonsForWrite отсекает чужие даты и оставляет запрошенные и пустые`() {
        val lessons = listOf(
            lesson(group, "05.10.2026", 1),
            lesson(group, "28.09.2026", 2),
            lesson(group, "", 3),
        )
        val out = lessonsForWrite(lessons, setOf("05.10.2026", "06.10.2026"))
        assertEquals(setOf("05.10.2026", ""), out.map { it.dateString }.toSet())
    }

    @Test
    fun `КРИТ1 - writeByGroup не пишет в прошлый месяц при restamp-уроках`() = runBlocking {
        val dao = RecordingLessonDao()
        val repo = ScheduleRepository(dao, RestampingClient(), catalogProvider = { emptyList() })

        val result = repo.syncScheduleDetailed(group)

        assertTrue(result.isSuccess)
        assertTrue("целевая дата записана", dao.replacedDates.contains(tomorrowStr()))
        assertFalse("прошлый месяц НЕ записан: ${dao.replacedDates}", dao.replacedDates.contains("01.09.2020"))
        assertTrue(dao.inserted.none { it.dateString == "01.09.2020" })
    }

    @Test
    fun `КРИТ3 - офлайн даёт failure, а не ещё не опубликовано`() = runBlocking {
        val client = OfflineClient()
        val repo = ScheduleRepository(
            RecordingLessonDao(),
            client,
            catalogProvider = { listOf(group, "11Э", "31П") },
        )

        val result = repo.syncScheduleDetailed(group)

        assertTrue("офлайн обязан давать failure", result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `СРЕД10 - неполный день помечается и не докачивается, force сбрасывает`() = runBlocking {
        val today = Calendar.getInstance()
        val client = OfflineClient()
        val repo = ScheduleRepository(
            RecordingLessonDao(),
            client,
            catalogProvider = { listOf(group, "11Э", "31П") },
        )
        repo.rememberDayCompleteness(fmt(today), 1, 3)
        repo.rememberDayCompleteness(tomorrowStr(today), 1, 3)
        assertTrue(repo.isKnownIncompleteDay(tomorrowStr(today)))

        val result = repo.syncScheduleDetailed(group)

        assertEquals(0, client.downloadAttempts)
        assertEquals(SyncOutcome.FoundNoGroup(tomorrowStr(today)), result.getOrNull())

        repo.syncScheduleDetailed(group, force = true)
        assertTrue("force обязан идти за документом", client.downloadAttempts > 0)
    }

    @Test
    fun `СРЕД10 - incompleteDayMark - полный день снимает метку, неразобранный не трогает`() {
        assertEquals(true, incompleteDayMark(parsedGroups = 2, catalogSize = 64))
        assertEquals(false, incompleteDayMark(parsedGroups = 40, catalogSize = 64))
        assertEquals(null, incompleteDayMark(parsedGroups = 0, catalogSize = 64))
    }
}
