package com.politex

import com.politex.data.local.dao.TeacherLessonDao
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.network.TeacherScheduleParser
import com.politex.data.network.TeacherSlot
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.defaultTeacherDayOffset
import com.politex.data.repository.loadTeacherDay
import com.politex.data.repository.teacherRows
import com.politex.data.repository.teacherSyncErrorMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TeacherScheduleE2ETest {
    private class FakeTeacherLessonDao : TeacherLessonDao {
        val rows = mutableListOf<TeacherLessonEntity>()

        override suspend fun getForSurnameOnDate(
            surnameKey: String,
            dateString: String,
        ): List<TeacherLessonEntity> = rows.filter { it.surnameKey == surnameKey && it.dateString == dateString }
            .sortedBy { it.lessonNumber }

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

    private fun liveBytes(): ByteArray = javaClass.getResourceAsStream("/fixtures/05.10.2026-raspisanie-prepodavatelej.doc")!!.readBytes()

    @Test
    fun `док — строки — dao — чтение по surnameKey и дате даёт слоты`() = runBlocking {
        val parsed = TeacherScheduleParser.parse(liveBytes())
        assertTrue("фикстура должна разбираться", parsed.isNotEmpty())

        val date = "05.10.2026"
        val rows = teacherRows(parsed, date, requestedDow = 1)
        assertTrue("строки кэша строятся", rows.isNotEmpty())

        val dao = FakeTeacherLessonDao()
        dao.replaceForDate(date, rows)

        val cardKey = TeacherInsights.surnameOf("Автушко Наталья Александровна")
        val docKey = parsed.keys.first { it.startsWith("Автушко") }
        assertEquals("запись и чтение идут по одному surnameKey", cardKey, TeacherInsights.surnameOf(docKey))

        val slots = dao.getForSurnameOnDate(cardKey, date)
        assertTrue("слоты из кэша непустые", slots.isNotEmpty())
        assertTrue(slots.all { it.dayOfWeek == 1 && it.dateString == date })
    }

    @Test
    fun `кэш-хит — сеть не вызывается`() = runBlocking {
        var syncCalls = 0
        val cached = listOf(TeacherSlot(2, listOf("41Г"), "Экономика", "248", 1))
        val r =
            loadTeacherDay(
                dateKey = "05.10.2026",
                fromDatabase = { cached },
                sync = {
                    syncCalls++
                    Result.success(1)
                },
            )
        assertEquals(0, syncCalls)
        assertEquals(cached, r.slots)
        assertEquals("", r.error)
    }

    @Test
    fun `ошибка сети — возвращается текст, а не вечная загрузка`() = runBlocking {
        val r =
            loadTeacherDay(
                dateKey = "04.10.2026",
                fromDatabase = { emptyList() },
                sync = { Result.failure(IOException("Документ не найден: 04.10.2026")) },
            )
        assertTrue(r.slots.isEmpty())
        assertEquals("Расписание преподавателя на 04.10.2026 ещё не опубликовано", r.error)
    }

    @Test
    fun `исключение внутри sync — тоже текст, а не краш`() = runBlocking {
        val r =
            loadTeacherDay(
                dateKey = "04.10.2026",
                fromDatabase = { emptyList() },
                sync = { throw IllegalStateException("db down") },
            )
        assertTrue(r.slots.isEmpty())
        assertTrue(r.error.isNotBlank())
    }

    @Test
    fun `документ за другой день — честное ещё не опубликовано`() {
        assertEquals(
            "Расписание преподавателя на 04.10.2026 ещё не опубликовано",
            teacherSyncErrorMessage(IOException("Документ за 05.10.2026 вместо 04.10.2026: url"), "04.10.2026"),
        )
        assertEquals(
            "Расписание преподавателя на 04.10.2026 ещё не опубликовано",
            teacherSyncErrorMessage(IOException("Документ за другой день"), "04.10.2026"),
        )
        assertEquals(
            "Не удалось загрузить расписание (проверьте интернет)",
            teacherSyncErrorMessage(IOException("timeout"), "04.10.2026"),
        )
    }

    @Test
    fun `sync успешен но уроков нет — пустые слоты без ошибки`() = runBlocking {
        val r =
            loadTeacherDay(
                dateKey = "05.10.2026",
                fromDatabase = { emptyList() },
                sync = { Result.success(120) },
            )
        assertTrue(r.slots.isEmpty())
        assertEquals("", r.error)
    }

    @Test
    fun `воскресенье по умолчанию открывает понедельник`() {
        assertEquals(1, defaultTeacherDayOffset(7))
        assertEquals(0, defaultTeacherDayOffset(1))
        assertEquals(0, defaultTeacherDayOffset(6))
    }
}
