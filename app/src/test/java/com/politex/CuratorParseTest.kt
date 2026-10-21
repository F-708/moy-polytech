package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.network.MpkScheduleParser
import com.politex.util.CuratorStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CuratorParseTest {
    @Before
    fun setUp() {
        CuratorStore.resetInMemory()
    }

    @After
    fun tearDown() {
        CuratorStore.resetInMemory()
    }

    private fun fixture(): ByteArray =
        javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    private fun parseFixture() = MpkScheduleParser.parseFileAllGroups(
        fixture(),
        "05.10.2026",
        listOf("41О", "41Г", "21Д", "11О", "21М"),
    )

    private fun lesson(
        group: String,
        subjectRaw: String,
        teacher: String,
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = 1,
        lessonNumber = 5,
        timeStart = "14:00",
        timeEnd = "14:45",
        subjectRaw = subjectRaw,
        roomFirst = "305",
        teacherFirst = teacher,
        dateString = "",
    )

    @Test
    fun `collectCuratorPairs — только кураторский час и непустые учителя`() {
        val pairs = MpkScheduleParser.collectCuratorPairs(
            listOf(
                lesson("41О", "Кураторский час", "Иванова А.Б."),
                lesson("21М", "Математика", "Петров П.П."),
                lesson("11А", "Кураторский час", ""),
                lesson("31П", "КУРЧ", "Сидоров С.С."),
                lesson("11Т", "КУРЧАС", "Кардаш В.Р."),
            ),
        )
        assertEquals(
            mapOf(
                "41О" to "Иванова А.Б.",
                "31П" to "Сидоров С.С.",
                "11Т" to "Кардаш В.Р.",
            ),
            pairs,
        )
    }

    @Test
    fun `синтетический документ с КУРЧ — кураторы собираются по группам`() {
        val text = """
            11А│21М
            1 Математика│1 Физика
            Иванов И.И.│Петров П.П.
            5 КУРЧ 305│5 КУРЧ 201
            Сидоров С.С.│Кузнецов К.К.
        """.trimIndent()
        val lessons = MpkScheduleParser.parseFileAllGroups(
            text.toByteArray(Charsets.UTF_8),
            "05.10.2026",
            listOf("11А", "21М"),
        )
        assertTrue(lessons.isNotEmpty())
        assertEquals("Сидоров С.С.", CuratorStore.curatorFor("11А"))
        assertEquals("Кузнецов К.К.", CuratorStore.curatorFor("21М"))
    }

    @Test
    fun `документ без КУРЧ — store не трогается`() {
        CuratorStore.record(mapOf("11А" to "Старый С.С."), now = 1_000L)
        val text = """
            11А│21М
            1 Математика│1 Физика
            Иванов И.И.│Петров П.П.
        """.trimIndent()
        MpkScheduleParser.parseFileAllGroups(
            text.toByteArray(Charsets.UTF_8),
            "05.10.2026",
            listOf("11А", "21М"),
        )
        assertEquals("Старый С.С.", CuratorStore.curatorFor("11А"))
        assertEquals(1_000L, CuratorStore.entryFor("11А")!!.updatedAt)
    }

    @Test
    fun `реальный студенческий документ — кураторы пишутся в store`() {
        val lessons = parseFixture()
        assertTrue(lessons.isNotEmpty())

        val expected = MpkScheduleParser.collectCuratorPairs(lessons)
        assertTrue("в документе 05.10.2026 должен быть хотя бы один кураторский час", expected.isNotEmpty())
        expected.forEach { (group, teacher) ->
            assertTrue(teacher.isNotBlank())
            assertEquals(teacher, CuratorStore.curatorFor(group))
        }
    }

    @Test
    fun `реальный документ — КУРЧАС узнаётся как кураторский час`() {
        parseFixture()
        val store = CuratorStore.snapshot()
        assertEquals("Кардаш В.Р.", store["11Т"]?.teacher)
        assertEquals("Савицкая Т.В.", store["22М"]?.teacher)
        assertEquals("Бибик Т.С.", store["21О"]?.teacher)
        assertTrue(store.values.all { it.updatedAt > 0L })
    }

    @Test
    fun `реальный документ — куратор есть у каждой группы с кураторским часом`() {
        val lessons = parseFixture()
        val curatorLessons = lessons.filter {
            it.officialSubjectName == MpkScheduleParser.CURATOR_HOUR ||
                it.subjectRaw.contains("КУРЧ", ignoreCase = true)
        }
        assertTrue(curatorLessons.isNotEmpty())
        curatorLessons.forEach { lesson ->
            val stored = CuratorStore.curatorFor(lesson.groupName)
            assertTrue("для группы ${lesson.groupName} куратор должен быть в store", stored != null)
        }
    }
}
