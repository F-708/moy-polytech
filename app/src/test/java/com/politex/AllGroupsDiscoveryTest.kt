package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.ui.components.filterRoomLessonsForDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class AllGroupsDiscoveryTest {
    private fun docx(rows: List<List<String>>): ByteArray {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        sb.append("<w:p><w:r><w:t>РАСПИСАНИЕ УЧАЩИХСЯ 21.10.2026 среда</w:t></w:r></w:p>")
        sb.append("<w:tbl>")
        for (row in rows) {
            sb.append("<w:tr>")
            for (cell in row) {
                sb.append("<w:tc><w:p><w:r><w:t>")
                    .append(cell.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
                    .append("</w:t></w:r></w:p></w:tc>")
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl></w:body></w:document>")
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(sb.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bos.toByteArray()
    }

    private val tableRows =
        listOf(
            listOf("№", "11А", "23М", "41Н"),
            listOf("Среда", "", "", ""),
            listOf("1", "Математика 339", "КоммДизайн 323", "История 334"),
            listOf("3", "Физика 142", "Основы маркетинга 206", "ОЭкО 339"),
            listOf("4", "Физика 142", "Основы маркетинга 206", "ОЭкО 339"),
            listOf("7", "Химия 210", "Маркетинговые исследования 339/340", ""),
        )

    @Test
    fun `группа из документа вне каталога всё равно разбирается`() {
        val bytes = docx(tableRows)
        val all = MpkScheduleParser.parseFileAllGroups(bytes, "21.10.2026", listOf("11А", "41Н"))

        val missingGroup = all.filter { it.groupName == "23М" }
        assertTrue("23М отсутствует в результате: ${all.map { it.groupName }.distinct()}", missingGroup.isNotEmpty())
        assertTrue(missingGroup.any { it.lessonNumber == 7 && it.roomFirst == "339" && it.roomSecond == "340" })
        assertTrue(all.none { it.groupName.isBlank() })
        assertTrue(all.map { it.groupName }.distinct().all { it in setOf("11А", "23М", "41Н") })
    }

    @Test
    fun `каталог без группы 23М не теряет её урок в кабинете 339`() {
        val bytes = docx(tableRows)
        val all = MpkScheduleParser.parseFileAllGroups(bytes, "21.10.2026", listOf("11А", "41Н"))

        val room339 = filterRoomLessonsForDate(all, "339", "21.10.2026")
        assertEquals(4, room339.size)
        assertEquals(listOf(1, 3, 4, 7), room339.map { it.lessonNumber })
        assertEquals(listOf("11А", "41Н", "41Н", "23М"), room339.map { it.groupName })
    }

    @Test
    fun `ячейка кабинета 339x340 даёт split с обоими кабинетами`() {
        val bytes = docx(tableRows)
        val all = MpkScheduleParser.parseFileAllGroups(bytes, "21.10.2026", listOf("23М"))
        val split = all.first { it.lessonNumber == 7 }
        assertEquals("339", split.roomFirst)
        assertEquals("340", split.roomSecond)
        assertTrue(split.isSplit)
    }

    @Test
    fun `discoverDocumentGroups на docx находит группы шапки и не выдумывает лишнего`() {
        val bytes = docx(tableRows)
        val texts = MpkScheduleParser.extractDocTexts(bytes)
        val discovered = MpkScheduleParser.discoverDocumentGroups(texts)
        assertEquals(setOf("11А", "23М", "41Н"), discovered.toSet())
    }

    @Test
    fun `пустой каталог по-прежнему даёт пустой результат`() {
        val bytes = docx(tableRows)
        assertTrue(MpkScheduleParser.parseFileAllGroups(bytes, "21.10.2026", emptyList()).isEmpty())
    }
}
