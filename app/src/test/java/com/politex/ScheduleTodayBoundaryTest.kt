package com.politex

import com.politex.data.local.dao.LessonDao
import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.ProbeResult
import com.politex.data.network.TeacherSlot
import com.politex.data.repository.ScheduleRepository
import com.politex.ui.viewmodel.currentDayKey
import com.politex.worker.scheduleDayCheckedAllowed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Calendar

/**
 * P1-границы «сегодня»:
 *  - смена суток даёт новый ключ дня (это триггер автосинка в AppViewModel);
 *  - legacy-строки без даты больше не пишутся и не дублируются;
 *  - воркер не помечает день «проверено», если документ не подтверждён.
 */

private class BoundaryDao : LessonDao {
    val rows = mutableListOf<LessonEntity>()
    val replacedDates = mutableListOf<String>()
    val inserted = mutableListOf<LessonEntity>()
    val deletedDateGroups = mutableListOf<List<String>>()

    override fun getLessonsForDay(groupName: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDate(groupName: String, dateString: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForDateOrDay(groupName: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getLessonsForRoomOnDate(room: String, dateString: String, dayOfWeek: Int): Flow<List<LessonEntity>> = flowOf(emptyList())
    override fun getAllLessonsForGroup(groupName: String): Flow<List<LessonEntity>> = flowOf(emptyList())
    override suspend fun getAllLessonsSync(): List<LessonEntity> = rows
    override suspend fun getLessonsForDaySync(groupName: String, dayOfWeek: Int): List<LessonEntity> = emptyList()
    override suspend fun getDistinctGroupsForDateOrDay(dateString: String, dayOfWeek: Int): List<String> = emptyList()
    override suspend fun insertLessons(lessons: List<LessonEntity>) {
        inserted += lessons
        rows += lessons
    }

    override suspend fun insertLesson(lesson: LessonEntity): Long = 0L
    override suspend fun clearLessonsForGroup(groupName: String) = Unit
    override suspend fun deleteLessonsForDate(groupName: String, dateString: String) = Unit
    override suspend fun deleteLessonsForDay(groupName: String, dayOfWeek: Int) = Unit
    override fun getDistinctDatesForGroup(groupName: String): Flow<List<String>> = flowOf(emptyList())
    override suspend fun getLessonCountForGroup(groupName: String): Int = 0
    override suspend fun getAllDistinctDatesSync(): List<String> = emptyList()
    override suspend fun getLessonCountForDate(groupName: String, dateString: String): Int = 0

    override suspend fun deleteLessonsByDate(dateString: String) {
        deletedDateGroups += listOf(dateString)
        rows.removeAll { it.dateString == dateString }
    }

    override suspend fun deleteLessonsByDates(dates: List<String>) {
        deletedDateGroups += dates
        rows.removeAll { it.dateString in dates }
    }

    override suspend fun replaceForDate(groupName: String, dateString: String, lessons: List<LessonEntity>) {
        replacedDates += dateString
        inserted += lessons
        rows += lessons
    }
}

private class BoundaryClient : MpkNetworkClient() {
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
    ): Result<List<LessonEntity>> = Result.failure(IOException("offline test"))

    override suspend fun fetchTeacherSchedule(targetCalendar: Calendar): Result<Map<String, List<TeacherSlot>>> =
        Result.failure(IOException("offline test"))
}

class ScheduleTodayBoundaryTest {
    private val group = "41О"

    private fun lesson(
        date: String,
        number: Int,
        dow: Int = 1,
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = dow,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Тест",
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = date,
    )

    private fun repo(dao: LessonDao) = ScheduleRepository(dao, BoundaryClient(), catalogProvider = { emptyList() })

    @Test
    fun `ключ суток меняется в полночь и стабилен внутри дня`() {
        val morning = Calendar.getInstance().apply {
            clear()
            set(2026, 10, 6, 8, 15)
        }
        val evening = Calendar.getInstance().apply {
            clear()
            set(2026, 10, 6, 23, 59)
        }
        val nextDay = Calendar.getInstance().apply {
            clear()
            set(2026, 10, 7, 0, 5)
        }

        assertEquals(currentDayKey(morning), currentDayKey(evening))
        assertNotEquals("00:05 — это уже другие сутки", currentDayKey(evening), currentDayKey(nextDay))
    }

    @Test
    fun `ключ суток меняется и при смене часового пояса в полночь`() {
        val before = Calendar.getInstance().apply {
            clear()
            set(2026, 10, 6)
        }
        val after = Calendar.getInstance().apply {
            clear()
            set(2026, 10, 6)
        }
        after.add(Calendar.HOUR_OF_DAY, 2)

        assertEquals(currentDayKey(before), currentDayKey(after))

        after.add(Calendar.HOUR_OF_DAY, 22)
        assertNotEquals(currentDayKey(before), currentDayKey(after))
    }

    @Test
    fun `строки без даты не пишутся и не дублируются`() = runBlocking {
        val dao = BoundaryDao()
        repo(dao).replaceSyncedLessons(group, listOf(lesson("", 1), lesson("05.10.2026", 2)))

        assertEquals(listOf("05.10.2026"), dao.replacedDates)
        assertTrue("путь «просто insert» больше не используется", dao.inserted.none { it.dateString.isBlank() })
    }

    @Test
    fun `повторный синк даты не плодит дубли`() = runBlocking {
        val dao = BoundaryDao()
        val repo = repo(dao)

        repo.replaceSyncedLessons(group, listOf(lesson("05.10.2026", 1), lesson("05.10.2026", 2)))
        repo.replaceSyncedLessons(group, listOf(lesson("05.10.2026", 1), lesson("05.10.2026", 2)))

        assertEquals("каждый синк — это replace, а не накопление", 2, dao.replacedDates.size)
        assertTrue(dao.replacedDates.all { it == "05.10.2026" })
    }

    @Test
    fun `pruneOldLessons вычищает legacy-строки без даты`() = runBlocking {
        val dao = BoundaryDao()
        dao.rows += lesson("", 1, dow = 1)
        dao.rows += lesson("05.10.2026", 2)

        repo(dao).pruneOldLessons()

        assertTrue(
            "удаление по пустой дате должно быть запрошено: ${dao.deletedDateGroups}",
            dao.deletedDateGroups.any { "" in it },
        )
        assertTrue("недатированная строка удалена", dao.rows.none { it.dateString.isBlank() })
    }

    @Test
    fun `воркер помечает день проверенным только при подтверждённом документе`() {
        assertFalse("пусто и без документа — повторим позже", scheduleDayCheckedAllowed(lessonsFound = 0, documentPresent = false))
        assertTrue("документ есть, группы нет", scheduleDayCheckedAllowed(lessonsFound = 0, documentPresent = true))
        assertTrue("уроки есть", scheduleDayCheckedAllowed(lessonsFound = 3, documentPresent = false))
    }
}