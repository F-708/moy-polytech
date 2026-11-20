package com.politex

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.ProbeResult
import com.politex.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Calendar
import java.util.Locale

private class MutableLessonDao(initial: List<LessonEntity> = emptyList()) : LessonDao {
    private val rows = initial.toMutableList()

    fun snapshot(group: String, date: String): List<LessonEntity> =
        rows.filter { it.groupName == group && it.dateString == date }.sortedBy { it.lessonNumber }

    override suspend fun replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>) {
        rows.removeAll { it.groupName == groupName && it.dateString == dateString }
        rows += lessons
    }

    override suspend fun getLessonCountForDate(groupName: String, dateString: String): Int =
        rows.count { it.groupName == groupName && it.dateString == dateString }

    override suspend fun getDistinctGroupsForDateOrDay(dateString: String, dayOfWeek: Int): List<String> =
        rows.filter { it.dateString == dateString || (it.dateString.isBlank() && it.dayOfWeek == dayOfWeek) }
            .map { it.groupName }.distinct().sorted()

    override fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName && it.dayOfWeek == dayOfWeek })

    override fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName && it.dateString == dateString })

    override fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName && (it.dateString == dateString || (it.dateString.isBlank() && it.dayOfWeek == dayOfWeek)) })

    override fun getLessonsForRoomOnDate(room: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> =
        flowOf(emptyList())

    override fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> =
        flowOf(rows.filter { it.groupName == groupName })

    override suspend fun getAllLessonsSync(): List<LessonEntity> = rows.toList()

    override suspend fun getLessonsForDaySync(groupName: String, dayOfWeek: Int): List<LessonEntity> =
        rows.filter { it.groupName == groupName && it.dayOfWeek == dayOfWeek }

    override suspend fun insertLessons(lessons: List<LessonEntity>) = Unit
    override suspend fun insertLesson(lesson: LessonEntity): Long = 0L
    override suspend fun clearLessonsForGroup(groupName: String) = Unit
    override suspend fun deleteLessonsForDate(groupName: String, dateString: String) = Unit
    override suspend fun deleteLessonsForDay(groupName: String, dayOfWeek: Int) = Unit

    override fun getDistinctDatesForGroup(groupName: String): Flow<List<String>> =
        flowOf(rows.filter { it.groupName == groupName && it.dateString.isNotBlank() }.map { it.dateString }.distinct())

    override suspend fun getLessonCountForGroup(groupName: String): Int =
        rows.count { it.groupName == groupName }

    override suspend fun getAllDistinctDatesSync(): List<String> =
        rows.map { it.dateString }.filter { it.isNotBlank() }.distinct()

    override suspend fun deleteLessonsByDate(dateString: String) = Unit
    override suspend fun deleteLessonsByDates(dates: List<String>) = Unit
}

private class TwoDocsClient(
    private val cleanDoc: ByteArray,
    private val corruptDoc: ByteArray,
) : MpkNetworkClient() {
    @Volatile
    var corrupted = false

    @Volatile
    var clock = 0L

    override fun nowMillis(): Long = clock

    override suspend fun probeDocument(calendar: Calendar, nameBase: String): ProbeResult =
        ProbeResult.Found("https://example.test/raspisanie.doc", 200)

    override suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String,
        preferredUrl: String?,
        visit: suspend (String) -> Boolean,
    ) {
        visit("https://example.test/raspisanie.doc")
    }

    override suspend fun downloadFileBytes(url: String): ByteArray {
        return if (corrupted) corruptDoc else cleanDoc
    }

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<com.politex.data.network.TeacherSlot>>> =
        Result.failure(IOException("no teacher in this test"))
}

private fun fixture(name: String): ByteArray =
    java.io.File("src/test/resources/$name").takeIf { it.isFile }?.readBytes()
        ?: java.io.File("src/test/resources/fixtures/$name").readBytes()

private fun fmt(cal: Calendar): String = String.format(
    Locale.ROOT,
    "%02d.%02d.%04d",
    cal.get(Calendar.DAY_OF_MONTH),
    cal.get(Calendar.MONTH) + 1,
    cal.get(Calendar.YEAR),
)

class ScheduleCorruptionGateTest {
    private val catalog = listOf("11А", "11Д", "11Т", "21М", "21П", "21Т", "22О", "22Т", "23Д", "23О", "23Э", "31Г", "31Д", "31М", "31Н", "31О", "31П", "31Т", "31Э", "32Д", "32М", "32Н", "32О", "32Т", "32Э", "41Г", "41Н", "41П", "42Н", "43О")

    @Test
    fun `коррумпированный документ не затирает уже существующий день`() = runBlocking {
        val target = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 15)
        }
        val date = fmt(target)
        val cleanDoc = fixture("raspisanie-15.09.2026.doc")
        val corruptDoc = fixture("raspisanie-16.09.2026.doc")
        val dao = MutableLessonDao()
        val client = TwoDocsClient(cleanDoc, corruptDoc)
        val repo = ScheduleRepository(
            lessonDao = dao,
            networkClient = client,
            catalogProvider = { catalog },
            knownRoomsProvider = { setOf("101", "102", "201", "204", "301", "401") },
        )

        // Первый граб: кэша нет, документ чистый — пишем как есть.
        val first = repo.syncScheduleForDate("41О", target)
        assertTrue("первый граб должен записать чистый документ", first.isSuccess)
        val before = dao.snapshot("41О", date)
        assertTrue("ожидаем уроки у 41О на 15.09.2026, получили ${before.size}", before.isNotEmpty())

        // Второй граб: сайт вернул битый документ (документ тот же, но «как будто» мусор).
        // Документ должен пройти дату (тот же файл), но sanity-гейт обязан заблокировать
        // перезапись уже существующего дня.
        client.corrupted = true
        val second = repo.syncScheduleForDate("41О", target)
        val after = dao.snapshot("41О", date)

        assertEquals("содержимое дня не должно измениться", before, after)
        // Репозиторий сообщает «не удалось» — вызывающий (воркер/виджет) не трогает уведомления.
        assertTrue("ожидаем failure от sanity-гейта", second.isFailure)
    }

    @Test
    fun `первый граб практической группы проходит без sanity-гейта`() = runBlocking {
        val target = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.SEPTEMBER, 15)
        }
        val date = fmt(target)
        val cleanDoc = fixture("raspisanie-15.09.2026.doc")
        val dao = MutableLessonDao()
        val client = TwoDocsClient(cleanDoc, cleanDoc)
        client.corrupted = true // «мусорный» документ, но кэша ещё нет — гейт не должен мешать
        val repo = ScheduleRepository(
            lessonDao = dao,
            networkClient = client,
            catalogProvider = { catalog },
            knownRoomsProvider = { setOf("101", "102", "201", "204", "301", "401") },
        )

        val first = repo.syncScheduleForDate("41О", target)
        assertTrue("первый граб обязан пройти (кэша нет)", first.isSuccess)
        assertTrue("уроки записаны", dao.snapshot("41О", date).isNotEmpty())
    }
}
