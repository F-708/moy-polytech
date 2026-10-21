package com.politex

import com.politex.data.network.TeacherScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherScheduleParserTest {
    private fun realDocBytes(): ByteArray = javaClass.getResourceAsStream("/16.09.2026-raspisanie-prepodavatelej.doc")!!.readBytes()

    private fun parsed() = TeacherScheduleParser.parse(realDocBytes())

    @Test
    fun `документ распознаётся и содержит преподавателей`() {
        val result = parsed()
        assertTrue("Должны найтись преподаватели", result.isNotEmpty())
        assertTrue("Преподавателей должно быть много (в документе ~80)", result.size >= 50)
    }

    @Test
    fun `Базулина ведёт уроки у 41П с кабинетом 301`() {
        val teacher = parsed().entries.firstOrNull { it.key.startsWith("Базулина") }
        assertNotNull("Базулина Т.Г. должна быть в расписании", teacher)

        val slots = teacher!!.value

        val lessonTo41P = slots.filter { it.groups.contains("41П") }
        assertTrue("Базулина ведёт у 41П", lessonTo41P.isNotEmpty())
        assertEquals("каб. 301", "301", lessonTo41P.first().room)
        assertTrue("Номера уроков в диапазоне 1..10", slots.all { it.lessonNumber in 1..10 })
    }

    @Test
    fun `кабинеты извлекаются как отдельный вид значения`() {
        val result = parsed()

        val withRoom = result.values.flatten().filter { it.room.isNotBlank() }
        assertTrue("Должны быть занятия с кабинетами", withRoom.isNotEmpty())
        withRoom.forEach { slot ->
            assertTrue(
                "Кабинет «${slot.room}» не должен быть группой или дисциплиной",
                !slot.room.matches(Regex("[1-4][1-9][А-Я]")),
            )
        }
    }

    @Test
    fun `группы в записях выглядят как коды групп`() {
        val result = parsed()
        val allGroups =
            result.values
                .flatten()
                .flatMap { it.groups }
                .distinct()
        assertTrue("Группы должны быть найдены", allGroups.isNotEmpty())
        allGroups.forEach { g ->
            assertTrue("«$g» не похоже на код группы", g.matches(Regex("[1-4][1-9][А-Я]")))
        }

        assertTrue("41П должна встречаться", allGroups.contains("41П"))
        assertTrue("31П должна встречаться", allGroups.contains("31П"))
    }

    @Test
    fun `дисциплины извлекаются как текст`() {
        val result = parsed()
        val withSubject = result.values.flatten().filter { it.subject.isNotBlank() }
        assertTrue("Дисциплины должны быть найдены", withSubject.isNotEmpty())

        withSubject.take(20).forEach { slot ->
            assertTrue(
                "«${slot.subject}» похоже на кабинет, а не дисциплину",
                !slot.subject.matches(Regex("[0-9]{1,3}")),
            )
        }
    }

    @Test
    fun `расписание отсортировано по номеру урока`() {
        val result = parsed()
        result.values.forEach { slots ->
            val numbers = slots.map { it.lessonNumber }
            assertEquals("Уроки должны идти по возрастанию", numbers.sorted(), numbers)
            assertTrue("Нет дублей уроков", numbers.distinct().size == numbers.size)
        }
    }

    @Test
    fun `конкретный преподаватель имеет ожидаемое число занятий`() {
        val teacher = parsed().entries.firstOrNull { it.key.startsWith("Бирюк") }
        assertNotNull(teacher)

        val slots = teacher!!.value
        assertTrue("У Бирюка должно быть не меньше 6 занятий", slots.size >= 6)
        val groups = slots.flatMap { it.groups }.distinct()
        assertTrue("Бирюк ведёт у 41О", groups.contains("41О"))
        assertTrue("Бирюк ведёт у 31П", groups.contains("31П"))
    }

    @Test
    fun `слоты размечены днём недели документа`() {
        val slots = parsed().values.flatten()
        assertTrue(slots.isNotEmpty())
        assertTrue("День недели должен быть взят из заголовка (СРЕДА)", slots.all { it.dayOfWeek == 3 })
    }

    @Test
    fun `ПЯТНИЦА в заголовке распознаётся как 5`() {
        assertEquals(
            5,
            TeacherScheduleParser.dowFromHeaderText(
                "│ 1 НЕДЕЛЯ     ║ ПЯТНИЦА                 18.09.2026                        ║",
            ),
        )
    }

    @Test
    fun `заголовок без слова НЕДЕЛЯ всё равно находится`() {
        val text =
            "│              ║ ЧЕТВЕРГ                                                    ║\r\n" +
                "│Автушко Н.А.  ║    │    │41П │\r\n"
        assertEquals(4, TeacherScheduleParser.dowFromHeaderText(TeacherScheduleParser.findHeaderLine(text)!!))
    }

    @Test
    fun `документ проверяется на соответствие запрошенной дате`() {
        val bytes = realDocBytes()
        val text = TeacherScheduleParser.extractText(bytes)
        assertTrue("Текст документа должен извлекаться", text.isNotBlank())

        val match =
            java.util.Calendar.getInstance().apply {
                clear()
                set(2026, java.util.Calendar.SEPTEMBER, 16)
            }
        val mismatch =
            java.util.Calendar.getInstance().apply {
                clear()
                set(2026, java.util.Calendar.SEPTEMBER, 17)
            }
        assertTrue(
            "17.09.2026 не совпадает с запрошенным 16.09.2026",
            !TeacherScheduleParser.matchesRequestedDate("ЧЕТВЕРГ 17.09.2026", match),
        )
        assertTrue(
            "16.09.2026 совпадает с запрошенным 16.09.2026",
            TeacherScheduleParser.matchesRequestedDate(text, match),
        )
        assertTrue(
            "16.09.2026 не совпадает с запрошенным 17.09.2026",
            !TeacherScheduleParser.matchesRequestedDate(text, mismatch),
        )
    }
}
