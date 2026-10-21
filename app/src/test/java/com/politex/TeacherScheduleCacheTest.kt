package com.politex

import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.data.network.TeacherScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherScheduleCacheTest {
    private fun fixture(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    @Test
    fun `реальный документ преподавателей разбирается по фамилиям`() {
        val bytes = fixture("16.09.2026-raspisanie-prepodavatelej.doc")
        val parsed = TeacherScheduleParser.parse(bytes)
        assertTrue("документ должен разобраться", parsed.isNotEmpty())

        val surnames =
            parsed.keys
                .map {
                    com.politex.data.repository.TeacherInsights
                        .surnameOf(it)
                }.filter { it.isNotBlank() }
        assertTrue("должны опознаваться фамилии", surnames.isNotEmpty())
        assertEquals(
            "фамилий без повторов должно быть столько же, сколько преподавателей",
            parsed.size,
            surnames.toSet().size,
        )
    }

    @Test
    fun `ключ строки базы совпадает с ключом поиска`() {
        val full = "Ламкина Ирина Петровна"
        val rawInDocument = "Ламкина И.П."

        val keyFromCard =
            com.politex.data.repository.TeacherInsights
                .surnameOf(full)
        val keyFromDocument =
            com.politex.data.repository.TeacherInsights
                .surnameOf(rawInDocument)
        assertEquals(keyFromCard, keyFromDocument)

        val row =
            TeacherLessonEntity(
                teacherName = rawInDocument,
                surnameKey = keyFromDocument,
                dateString = "16.09.2026",
                dayOfWeek = 3,
                lessonNumber = 3,
                groups = "31П, 32П",
                subject = "ТОЭ",
                room = "301",
            )
        assertEquals("ламкина", row.surnameKey)
        assertEquals("301", row.room)
        assertEquals(listOf("31П", "32П"), row.groups.split(", "))
    }

    @Test
    fun `документ даёт распознанный день недели для запрошенной даты`() {
        val bytes = fixture("16.09.2026-raspisanie-prepodavatelej.doc")
        val slots = TeacherScheduleParser.parse(bytes).values.flatten()
        assertTrue("слоты должны разобраться", slots.isNotEmpty())

        val recognized = slots.map { it.dayOfWeek }.filter { it != 0 }
        assertTrue("день недели должен распознаваться", recognized.isNotEmpty())
        assertTrue(
            "все распознанные слоты — среда (16.09.2026)",
            recognized.all { it == 3 },
        )
    }
}
