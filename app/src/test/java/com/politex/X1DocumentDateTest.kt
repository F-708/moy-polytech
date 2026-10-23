package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.directUrlHasRequestedDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X1DocumentDateTest {
    private fun liveDocBytes(): ByteArray = javaClass.getResourceAsStream("/fixtures/05-10-2026-raspisanie-uchashhihsya.doc")!!.readBytes()

    @Test
    fun `документ 05_10 принят для своей даты 05_10`() {
        assertTrue(MpkScheduleParser.documentMatchesDate(liveDocBytes(), "05.10.2026"))
    }

    @Test
    fun `документ 05_10 ОТКЛОНЁН для чужой даты 06_10`() {
        assertFalse(MpkScheduleParser.documentMatchesDate(liveDocBytes(), "06.10.2026"))
    }

    @Test
    fun `не-документ (HTML) не опровергается — дата совпала по умолчанию`() {
        val html = "<!DOCTYPE html><html><body>soft 404</body></html>".toByteArray(Charsets.UTF_8)
        assertTrue(MpkScheduleParser.documentMatchesDate(html, "06.10.2026"))
    }

    @Test
    fun `прямой URL с датой в имени (точки и подчёркивания) совпадает с запрошенной`() {
        assertTrue(
            directUrlHasRequestedDate(
                "https://guo-mpk.by/wp-content/uploads/2026/10/06.10.2026-raspisanie-uchashhihsya.doc",
                "06.10.2026",
            ),
        )
        assertTrue(
            directUrlHasRequestedDate(
                "https://guo-mpk.by/wp-content/uploads/2026/10/06_10_2026-raspisanie-uchashhihsya-1.doc",
                "06.10.2026",
            ),
        )
    }

    @Test
    fun `прямой URL чужой даты НЕ совпадает — probe не даст Found`() {
        assertFalse(
            directUrlHasRequestedDate(
                "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya.doc",
                "06.10.2026",
            ),
        )
    }

    @Test
    fun `некорректная запрошенная дата — не совпадает`() {
        assertFalse(directUrlHasRequestedDate("https://guo-mpk.by/06.10.2026.doc", "6.10.26"))
    }
}
