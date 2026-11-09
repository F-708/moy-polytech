package com.politex

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.ProbeResult
import com.politex.data.network.TeacherSlot
import com.politex.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * P0-3/P0-4: «документ не от той даты» молча принимался и штамповался запрошенной датой
 * (fail-open в documentMatchesDate + `detectedDate = targetDate`), а затем строки выбрасывались
 * при записи — пользователь видел «вашей группы нет» при опубликованном документе.
 */

/** Собирает минимальный .docx с заданным содержимым word/document.xml. */
private fun docxWith(body: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("word/document.xml"))
        zip.write("<w:document><w:body>$body</w:body></w:document>".toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return out.toByteArray()
}

/** Дата лежит в соседнем text-run'е — типичный реальный .docx. */
private fun docxDateSplitRun(date: String): ByteArray =
    docxWith("<w:p><w:t>РАСПИСАНИЕ</w:t></w:p><w:p><w:t>занятий на $date</w:t></w:p>")

/** Даты нет вовсе, только день недели. */
private fun docxOnlyWeekday(weekday: String): ByteArray =
    docxWith("<w:p><w:t>РАСПИСАНИЕ НА $weekday</w:t></w:p>")

private class DateGateDao : LessonDao {
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
    override suspend fun insertLessons(lessons: List<LessonEntity>) {
        inserted += lessons
    }

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

/** Отдаёт заранее заданные файлы по порядку URL и запоминает, какие URL реально посещались. */
private class FixtureFilesClient(
    private val files: Map<String, ByteArray?>,
) : MpkNetworkClient() {
    val visited = mutableListOf<String>()

    override suspend fun probeDocument(calendar: Calendar, nameBase: String): ProbeResult = ProbeResult.NotFound(404)

    override suspend fun forEachDocumentUrl(
        calendar: Calendar,
        nameBase: String,
        preferredUrl: String?,
        visit: suspend (String) -> Boolean,
    ) {
        for (url in files.keys) {
            visited += url
            if (visit(url)) return
        }
    }

    override suspend fun downloadFileBytes(url: String): ByteArray? = files[url]

    override suspend fun fetchScheduleForGroup(
        targetGroup: String,
        targetCalendar: Calendar,
        maxRetries: Int,
        preferredUrl: String?,
    ): Result<List<LessonEntity>> = Result.failure(IOException("offline test"))

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<TeacherSlot>>> =
        Result.failure(IOException("offline test"))
}

class ScheduleDocDateValidationTest {
    private val group = "41О"
    private val catalog = (1..64).map { "G$it" }
    private val badUrl = "https://guo-mpk.by/wp-content/uploads/2026/10/06.10.2026-raspisanie-uchashhihsya.doc"
    private val secondUrl = "https://guo-mpk.by/wp-content/uploads/2026/10/06.10.2026-raspisanie-uchashhihsya-1.doc"

    private fun liveDoc(): ByteArray =
        javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    @Test
    fun `реальный документ 05_10 принимается для своей даты`() {
        assertTrue(MpkScheduleParser.documentMatchesDate(liveDoc(), "05.10.2026"))
    }

    @Test
    fun `реальный документ 05_10 отклоняется для чужой даты`() {
        assertFalse(MpkScheduleParser.documentMatchesDate(liveDoc(), "06.10.2026"))
        assertFalse(MpkScheduleParser.documentMatchesDate(liveDoc(), "12.10.2026"))
        assertFalse(MpkScheduleParser.documentMatchesDate(liveDoc(), "05.10.2027"))
    }

    @Test
    fun `дата в соседнем run-е docx всё равно находится — вчерашний документ не станет сегодняшним`() {
        val docx = docxDateSplitRun("05.10.2026")
        assertTrue(MpkScheduleParser.documentMatchesDate(docx, "05.10.2026"))
        assertFalse("главный кейс P0-4", MpkScheduleParser.documentMatchesDate(docx, "06.10.2026"))
    }

    @Test
    fun `без даты в шапке сверяем день недели — чужой день отклоняется`() {
        val mondayDoc = docxOnlyWeekday("ПОНЕДЕЛЬНИК")
        // 05.10.2026 — понедельник.
        assertTrue(MpkScheduleParser.documentMatchesDate(mondayDoc, "05.10.2026"))
        // 06.10.2026 — вторник.
        assertFalse(MpkScheduleParser.documentMatchesDate(mondayDoc, "06.10.2026"))
    }

    @Test
    fun `ни даты, ни дня недели — документ не принимается (fail-closed)`() {
        assertFalse(MpkScheduleParser.documentMatchesDate(docxWith("<w:p><w:t>РАСПИСАНИЕ</w:t></w:p>"), "06.10.2026"))
        assertFalse(MpkScheduleParser.documentMatchesDate(ByteArray(0), "06.10.2026"))
    }

    @Test
    fun `чужой документ не пишется в базу и не объявляется отсутствием группы`() = runBlocking {
        val dao = DateGateDao()
        val client = FixtureFilesClient(mapOf(badUrl to liveDoc()))
        val repo = ScheduleRepository(dao, client, catalogProvider = { catalog })

        val target = MpkScheduleParser.parseDotDate("06.10.2026")!!
        val result = repo.syncScheduleForDate(group, target)

        assertTrue("документ не найден — это failure, а не «группы нет»", result.isFailure)
        assertTrue("в базу ничего не писали", dao.replacedDates.isEmpty() && dao.inserted.isEmpty())
        assertTrue(
            "кандидат отбракован с причиной: ${client.rejectionSummary()}",
            client.rejectedDocumentUrls.isNotEmpty(),
        )
    }

    @Test
    fun `после отбраковки по дате резолвер идёт к следующему кандидату`() = runBlocking {
        val dao = DateGateDao()
        val client = FixtureFilesClient(mapOf(badUrl to liveDoc(), secondUrl to null))
        val repo = ScheduleRepository(dao, client, catalogProvider = { catalog })

        val target = MpkScheduleParser.parseDotDate("06.10.2026")!!
        repo.syncScheduleForDate(group, target)

        assertEquals(listOf(badUrl, secondUrl), client.visited)
        assertTrue("ничего не записано", dao.replacedDates.isEmpty())
    }

    @Test
    fun `пустые байты кандидата тоже попадают в отчёт отбраковки`() = runBlocking {
        val client = FixtureFilesClient(mapOf(badUrl to null))
        val repo = ScheduleRepository(DateGateDao(), client, catalogProvider = { catalog })

        repo.syncScheduleForDate(group, MpkScheduleParser.parseDotDate("06.10.2026")!!)

        assertTrue(client.rejectedDocumentUrls.isNotEmpty())
        assertTrue(client.rejectionSummary().contains("нет байтов"))
    }

    @Test
    fun `документ за другую дату не подменяет дату запроса`() {
        // Ключевая инвариантность: разбор НЕ должен штамповать запрошенную дату, если документ за
        // другую дату — иначе «вчерашнее» расписание выглядит как сегодняшнее. Мы отсекаем такой
        // документ ещё до разбора (см. проверки выше), поэтому здесь фиксируем только сам контракт
        // валидации для пары дат одной недели.
        val docx = docxDateSplitRun("05.10.2026")
        assertTrue(MpkScheduleParser.documentMatchesDate(docx, "05.10.2026"))
        assertFalse(MpkScheduleParser.documentMatchesDate(docx, "07.10.2026"))
        assertFalse(MpkScheduleParser.documentMatchesDate(docx, "12.10.2026"))
    }
}