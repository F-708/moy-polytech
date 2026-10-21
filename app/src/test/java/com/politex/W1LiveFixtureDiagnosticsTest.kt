package com.politex

import com.politex.data.network.MpkScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class W1LiveFixtureDiagnosticsTest {
    private fun liveDocBytes(): ByteArray =
        javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    @Test
    fun `W1 фикстура понедельника - уроки реальных групп больше нуля`() {
        val expected = mapOf("41О" to 8, "41Г" to 9, "21Д" to 8, "11О" to 6)
        for ((group, count) in expected) {
            val lessons = MpkScheduleParser.parseFile(liveDocBytes(), targetGroup = group, targetDate = "05.10.2026")
            assertEquals("group=$group stats=${MpkScheduleParser.lastParseStats}", count, lessons.size)

            assertTrue(lessons.all { it.dateString == "05.10.2026" })
            assertTrue(lessons.all { it.dayOfWeek == 1 })
            assertTrue(lessons.all { it.timeStart.isNotBlank() })
        }

        assertEquals("grid", MpkScheduleParser.lastParseStats.strategy)
        assertEquals("doc-utf16", MpkScheduleParser.lastParseStats.format)
    }

    @Test
    fun `W1 parseFileAllGroups на живой фикстуре разбирает все группы каталога`() {
        val all = MpkScheduleParser.parseFileAllGroups(
            liveDocBytes(),
            targetDate = "05.10.2026",
            candidateGroups = listOf("41О", "41Г", "21Д", "11О", "21М"),
        )
        val groups = all.map { it.groupName }.toSet()
        assertTrue(groups.containsAll(listOf("41О", "41Г", "21Д", "11О", "21М")))
        assertTrue(all.isNotEmpty())
        assertTrue(groups.size > 5)
        assertTrue("группы документа вне каталога должны разбираться", "23М" in groups)
        assertTrue(groups.all { com.politex.util.GroupParser.isValid(it) })
    }
}
