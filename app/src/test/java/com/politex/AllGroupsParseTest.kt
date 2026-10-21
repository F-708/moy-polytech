package com.politex

import com.politex.data.network.MpkScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllGroupsParseTest {
    private fun fixture(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    private val groups = listOf("41О", "21М", "31П", "11А", "42Э")

    @Test
    fun `перебор групп не возвращает ничего лишнего при пустом списке групп`() {
        val bytes =
            runCatching { fixture("16.09.2026-raspisanie-prepodavatelej.doc") }.getOrNull()
                ?: return
        assertTrue(MpkScheduleParser.parseFileAllGroups(bytes, "16.09.2026", emptyList()).isEmpty())
    }

    @Test
    fun `пустые байты дают пустой результат`() {
        assertTrue(MpkScheduleParser.parseFileAllGroups(ByteArray(0), "16.09.2026", groups).isEmpty())
    }

    @Test
    fun `результат по всем группам не меньше чем по одной`() {
        val bytes = runCatching { fixture("raspisanie-16.09.2026.doc") }.getOrNull() ?: return
        val one = MpkScheduleParser.parseFile(bytes, "41О", "16.09.2026")
        if (one.isEmpty()) return
        val all = MpkScheduleParser.parseFileAllGroups(bytes, "16.09.2026", groups)
        assertTrue(
            "по всем группам (${all.size}) должно быть не меньше, чем по одной (${one.size})",
            all.size >= one.size,
        )
    }

    @Test
    fun `одиночный разбор не сломан перебором`() {

        val bytes = runCatching { fixture("raspisanie-16.09.2026.doc") }.getOrNull() ?: return
        val before = MpkScheduleParser.parseFile(bytes, "41О", "16.09.2026")
        MpkScheduleParser.parseFileAllGroups(bytes, "16.09.2026", groups)
        val after = MpkScheduleParser.parseFile(bytes, "41О", "16.09.2026")
        assertEquals(before.map { it.lessonNumber }, after.map { it.lessonNumber })
    }

    @Test
    fun `строки разных групп различимы по groupName`() {
        val bytes = runCatching { fixture("raspisanie-16.09.2026.doc") }.getOrNull() ?: return
        val all = MpkScheduleParser.parseFileAllGroups(bytes, "16.09.2026", groups)
        assertTrue(all.all { it.groupName.isNotBlank() })
    }
}
