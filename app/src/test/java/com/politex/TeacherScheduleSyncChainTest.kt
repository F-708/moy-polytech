package com.politex

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.TeacherSlot
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherDay
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.TeacherRowMatch
import com.politex.data.repository.freshTeacherLessons
import com.politex.data.repository.loadTeacherDay
import com.politex.data.repository.matchRowsForTeacher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Calendar
import java.util.GregorianCalendar

private class StubLessonDao : LessonDao {
    override fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForRoomOnDate(room: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override suspend fun getAllLessonsSync(): List<LessonEntity> = emptyList()
    override suspend fun getLessonsForDaySync(groupName: String, dayOfWeek: Int): List<LessonEntity> = emptyList()
    override suspend fun getDistinctGroupsForDateOrDay(dateString: String, dayOfWeek: Int): List<String> = emptyList()
    override suspend fun insertLessons(lessons: List<LessonEntity>) = Unit
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
}

private class InMemoryTeacherDao : TeacherLessonDao {
    val rows = mutableListOf<TeacherLessonEntity>()

    override suspend fun getForSurnameOnDate(surnameKey: String, dateString: String): List<TeacherLessonEntity> = rows.filter { it.surnameKey == surnameKey && it.dateString == dateString }.sortedBy { it.lessonNumber }

    override suspend fun getTeachersOnDate(dateString: String): List<String> = rows.filter { it.dateString == dateString }.map { it.teacherName }.distinct()

    override suspend fun insertLessons(lessons: List<TeacherLessonEntity>) {
        rows += lessons
    }

    override suspend fun deleteForDate(dateString: String) {
        rows.removeAll { it.dateString == dateString }
    }

    override suspend fun deleteForDates(dates: List<String>) {
        rows.removeAll { it.dateString in dates }
    }

    override suspend fun getPresentDates(dates: List<String>): List<String> = rows.map { it.dateString }.toSet().filter { it in dates }
}

private class StubTeacherClient(
    private val result: () -> Result<Map<String, List<TeacherSlot>>>,
) : MpkNetworkClient() {
    var fetchCalls = 0

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<TeacherSlot>>> {
        fetchCalls++
        return result()
    }
}

private fun monday(): Calendar = GregorianCalendar(2026, Calendar.OCTOBER, 5, 12, 0, 0)

private const val MONDAY = "05.10.2026"

private val goodDoc =
    mapOf(
        "Иванов А.П." to
            listOf(
                TeacherSlot(1, listOf("41О"), "Математика", "301", 0),
                TeacherSlot(2, listOf("41О"), "Физика", "202", 0),
            ),
        "Петров В.С." to listOf(TeacherSlot(1, listOf("22П"), "Химия", "101", 0)),
    )

private fun repositoryWith(
    dao: InMemoryTeacherDao,
    client: MpkNetworkClient,
): ScheduleRepository = ScheduleRepository(
    lessonDao = StubLessonDao(),
    networkClient = client,
    catalogProvider = { emptyList() },
    teacherLessonDao = dao,
)

class TeacherScheduleSyncChainTest {
    @Test
    fun `повторный синк идемпотентен — те же строки без дублей`() = runBlocking {
        val dao = InMemoryTeacherDao()
        val client = StubTeacherClient { Result.success(goodDoc) }
        val repo = repositoryWith(dao, client)

        val first = repo.syncTeacherScheduleForDate(monday(), force = true)
        val afterFirst = dao.rows.toList()
        val second = repo.syncTeacherScheduleForDate(monday(), force = true)
        val afterSecond = dao.rows.toList()

        assertTrue(first.isSuccess && second.isSuccess)
        assertEquals(3, first.getOrNull())
        assertEquals(afterFirst.size, afterSecond.size)
        assertEquals(
            afterFirst.map { it.teacherName to it.lessonNumber to it.subject }.sortedBy { it.toString() },
            afterSecond.map { it.teacherName to it.lessonNumber to it.subject }.sortedBy { it.toString() },
        )
        val keys = afterSecond.map { "${it.surnameKey}_${it.dateString}_${it.lessonNumber}_${it.groups}_${it.subject}_${it.room}" }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `синк без force при кэше даты не идёт в сеть`() = runBlocking {
        val dao = InMemoryTeacherDao()
        val client = StubTeacherClient { Result.success(goodDoc) }
        val repo = repositoryWith(dao, client)

        repo.syncTeacherScheduleForDate(monday(), force = true)
        assertEquals(1, client.fetchCalls)

        val cached = repo.syncTeacherScheduleForDate(monday(), force = false)
        assertTrue(cached.isSuccess)
        assertEquals("кэш-хит не запускает сеть", 1, client.fetchCalls)
    }

    @Test
    fun `документ не опубликован — failure, ничего не записывается`() = runBlocking {
        val dao = InMemoryTeacherDao()
        val client = StubTeacherClient { Result.failure(IOException("Документ не найден: $MONDAY")) }
        val repo = repositoryWith(dao, client)

        val result = repo.syncTeacherScheduleForDate(monday(), force = true)

        assertTrue(result.isFailure)
        assertTrue(dao.rows.isEmpty())
        assertFalse(repo.hasTeacherScheduleForDate(MONDAY))
    }

    @Test
    fun `пустой ответ не стирает кэш предыдущей удачной загрузки`() = runBlocking {
        val dao = InMemoryTeacherDao()
        var published = true
        val client = StubTeacherClient { if (published) Result.success(goodDoc) else Result.success(emptyMap()) }
        val repo = repositoryWith(dao, client)

        repo.syncTeacherScheduleForDate(monday(), force = true)
        val cached = dao.rows.toList()
        assertTrue(cached.isNotEmpty())

        published = false
        val empty = repo.syncTeacherScheduleForDate(monday(), force = true)
        assertTrue(empty.isSuccess)
        assertEquals("пустой документ не трогает кэш", cached, dao.rows)
        assertTrue(repo.hasTeacherScheduleForDate(MONDAY))
    }

    @Test
    fun `мусорный разбор без фамилий не стирает кэш и даёт failure`() = runBlocking {
        val dao = InMemoryTeacherDao()
        var published = true
        val garbage = mapOf("12345" to listOf(TeacherSlot(1, listOf("41О"), "Математика", "301", 0)))
        val client = StubTeacherClient { if (published) Result.success(goodDoc) else Result.success(garbage) }
        val repo = repositoryWith(dao, client)

        repo.syncTeacherScheduleForDate(monday(), force = true)
        val cached = dao.rows.toList()

        published = false
        val result = repo.syncTeacherScheduleForDate(monday(), force = true)

        assertTrue("синк без пригодных строк обязан падать, а не затирать кэш", result.isFailure)
        assertEquals(cached, dao.rows)
    }

    @Test
    fun `документ за другой день — failure, кэш другой даты не тронут`() = runBlocking {
        val dao = InMemoryTeacherDao()
        val client = StubTeacherClient { Result.success(goodDoc) }
        val repo = repositoryWith(dao, client)
        repo.syncTeacherScheduleForDate(monday(), force = true)
        val cached = dao.rows.toList()

        val otherDayDoc = mapOf("Иванов А.П." to listOf(TeacherSlot(1, listOf("41О"), "Математика", "301", 3)))
        val otherClient = StubTeacherClient { Result.success(otherDayDoc) }
        val otherRepo = repositoryWith(dao, otherClient)
        val result = otherRepo.syncTeacherScheduleForDate(monday(), force = true)

        assertTrue(result.isFailure)
        assertEquals(cached, dao.rows)
    }

    @Test
    fun `завтра без документа — пустой список и честный статус`() = runBlocking {
        val load =
            loadTeacherDay(
                dateKey = "06.10.2026",
                fromDatabase = { emptyList() },
                sync = { Result.failure(IOException("Документ не найден: 06.10.2026")) },
            )
        assertTrue(load.slots.isEmpty())
        assertEquals("Расписание преподавателя на 06.10.2026 ещё не опубликовано", load.error)
        assertEquals("06.10.2026", load.dateKey)
    }

    @Test
    fun `устаревший день не подменяет запрошенную дату`() {
        val lesson = TeacherDayLesson(1, "Математика", listOf("41О"), "301", "08:15", "09:00", 495, 540)
        val today = TeacherDay(listOf(lesson), ambiguous = false, error = "", isSynced = true, lastSyncEpoch = 0L, date = MONDAY)

        assertEquals(emptyList<TeacherDayLesson>(), freshTeacherLessons(today, "06.10.2026"))
        assertEquals(emptyList<TeacherDayLesson>(), freshTeacherLessons(null, MONDAY))
        assertEquals(listOf(lesson), freshTeacherLessons(today, MONDAY))
    }

    private val ivanovAp = Teacher("Иванов Алексей Петрович")
    private val ivanovVs = Teacher("Иванов Виктор Сергеевич")

    private fun row(
        teacherName: String,
        lessonNumber: Int,
        subject: String,
    ) = TeacherLessonEntity(
        id = 0,
        teacherName = teacherName,
        surnameKey = TeacherInsights.surnameOf(teacherName),
        dateString = MONDAY,
        dayOfWeek = 1,
        lessonNumber = lessonNumber,
        groups = "41О",
        subject = subject,
        room = "301",
    )

    @Test
    fun `однофамильцы стабильны — чужие строки не примеряются выбранному`() {
        val rows = listOf(row("Иванов А.П.", 1, "Математика"), row("Иванов В.С.", 2, "Физика"))

        val withBoth = listOf(ivanovAp, ivanovVs)
        assertEquals(TeacherRowMatch.YES, TeacherInsights.matchRowToTeacher("Иванов А.П.", ivanovAp, withBoth))
        assertEquals(TeacherRowMatch.NO, TeacherInsights.matchRowToTeacher("Иванов В.С.", ivanovAp, withBoth))

        val withoutSelected = listOf(ivanovVs)
        assertEquals("выбранный не в списке — чужая строка не его", TeacherRowMatch.NO, TeacherInsights.matchRowToTeacher("Иванов В.С.", ivanovAp, withoutSelected))
        assertEquals(TeacherRowMatch.YES, TeacherInsights.matchRowToTeacher("Иванов А.П.", ivanovAp, withoutSelected))

        val noTeachers = emptyList<Teacher>()
        assertEquals("без списка однофамильцев чужие инициалы отсекаются", TeacherRowMatch.NO, TeacherInsights.matchRowToTeacher("Иванов В.С.", ivanovAp, noTeachers))
        assertEquals(TeacherRowMatch.YES, TeacherInsights.matchRowToTeacher("Иванов А.П.", ivanovAp, noTeachers))

        val (matched, ambiguous) = matchRowsForTeacher(rows, ivanovAp, noTeachers, dow = 1)
        assertFalse("никаких дублей чужих уроков", ambiguous)
        assertEquals(listOf(1), matched.map { it.lessonNumber })
    }

    @Test
    fun `дубль той же персоны в списке не рождает ложный ambiguous`() {
        val duplicated = listOf(ivanovAp, ivanovAp.copy(), ivanovAp)
        assertEquals(TeacherRowMatch.YES, TeacherInsights.matchRowToTeacher("Иванов А.П.", ivanovAp, duplicated))
        val (_, ambiguous) = matchRowsForTeacher(listOf(row("Иванов А.П.", 1, "Математика")), ivanovAp, duplicated, dow = 1)
        assertFalse(ambiguous)
    }

    @Test
    fun `голая фамилия при однофамильцах остаётся ambiguous стабильно`() {
        assertEquals(TeacherRowMatch.AMBIGUOUS, TeacherInsights.matchRowToTeacher("Иванов", ivanovAp, listOf(ivanovAp, ivanovVs)))
        assertEquals(TeacherRowMatch.AMBIGUOUS, TeacherInsights.matchRowToTeacher("Иванов", ivanovAp, listOf(ivanovVs)))
        assertEquals(TeacherRowMatch.YES, TeacherInsights.matchRowToTeacher("Иванов", ivanovAp, listOf(ivanovAp)))
        assertEquals(TeacherRowMatch.YES, TeacherInsights.matchRowToTeacher("Иванов", ivanovAp, emptyList()))
    }

    @Test
    fun `профиль коротким именем не теряет свои строки`() {
        val shortProfile = Teacher("Иванов А.П.")
        assertEquals(TeacherRowMatch.AMBIGUOUS, TeacherInsights.matchRowToTeacher("Иванов А.П.", shortProfile, listOf(ivanovAp, ivanovVs)))
        assertEquals(TeacherRowMatch.NO, TeacherInsights.matchRowToTeacher("Иванов В.С.", shortProfile, listOf(ivanovAp, ivanovVs)))

        val (matched, ambiguous) = matchRowsForTeacher(listOf(row("Иванов А.П.", 1, "Математика")), shortProfile, listOf(ivanovAp, ivanovVs), dow = 1)
        assertTrue("своя строка видна даже при коротком профиле", ambiguous)
        assertEquals(listOf(1), matched.map { it.lessonNumber })
    }
}
