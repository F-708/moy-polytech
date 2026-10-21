package com.politex

import com.politex.data.network.MpkNetworkClient
import com.politex.data.network.MpkScheduleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleUrlResolverTest {
    private val client = MpkNetworkClient()

    private fun cal(
        y: Int,
        m: Int,
        d: Int,
    ): Calendar = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d)
    }

    @Test
    fun `прямые кандидаты на 01-10-2026 покрывают сентябрь и октябрь с суффиксами`() {
        val urls = client.generateCandidateDirectUrls(cal(2026, 10, 1)).map { it.first }

        assertTrue(
            "должен быть сентябрьский URL с суффиксом -1",
            urls.contains("https://guo-mpk.by/wp-content/uploads/2026/09/01.10.2026-raspisanie-uchashhihsya-1.doc"),
        )

        assertTrue(urls.any { it.contains("/2026/09/") })
        assertTrue(urls.any { it.contains("/2026/10/") })

        assertTrue(urls.any { it.endsWith("uchashhihsya-1.doc") })
        assertTrue(urls.any { it.endsWith("uchashhihsya-2.docx") })

        assertEquals("https://guo-mpk.by/wp-content/uploads/2026/10/01.10.2026-raspisanie-uchashhihsya.doc", urls.first())
    }

    @Test
    fun `преподавательские кандидаты используют имя raspisanie-prepodavatelej`() {
        val urls = client.generateCandidateDirectUrls(cal(2026, 10, 1), "raspisanie-prepodavatelej").map { it.first }
        assertTrue(urls.all { it.contains("raspisanie-prepodavatelej") })
        assertTrue(
            urls.contains("https://guo-mpk.by/wp-content/uploads/2026/09/01.10.2026-raspisanie-prepodavatelej-1.doc"),
        )
    }

    @Test
    fun `event-страница преподавателей может иметь суффикс -2`() {
        val urls = client.generateCandidateEventUrls(cal(2026, 10, 1), "raspisanie-prepodavatelej").map { it.first }
        assertTrue(urls.any { it.contains("01-10-2026-raspisanie-prepodavatelej-2") })
        assertTrue(urls.any { it.contains("occurrence=2026-10-01") })
    }

    @Test
    fun `extractDocumentUrls достаёт и декодирует doc из officeapps-iframe`() {

        val eventHtml =
            """
            <div class="entry-content">
                <iframe src="//view.officeapps.live.com/op/embed.aspx?src=https%3A%2F%2Fguo-mpk.by%2Fwp-content%2Fuploads%2F2026%2F09%2F01.10.2026-raspisanie-uchashhihsya-1.doc"></iframe>
            </div>
            """.trimIndent()

        val docUrls =
            MpkScheduleParser.extractDocumentUrls(
                eventHtml,
                "https://guo-mpk.by/raspisanie/01-10-2026-raspisanie-uchashhihsya/",
            )
        assertEquals(1, docUrls.size)
        assertEquals(
            "https://guo-mpk.by/wp-content/uploads/2026/09/01.10.2026-raspisanie-uchashhihsya-1.doc",
            docUrls[0],
        )
    }

    @Test
    fun `extractEventPageUrls находит event-страницы нужной даты на портале`() {
        val portalHtml =
            """
            <a href="https://guo-mpk.by/raspisanie/01-10-2026-raspisanie-uchashhihsya/?occurrence=2026-10-01">01.10</a>
            <a href="https://guo-mpk.by/raspisanie/01-10-2026-raspisanie-prepodavatelej-2/">преподаватели</a>
            <a href="https://guo-mpk.by/raspisanie/02-10-2026-raspisanie-uchashhihsya/">чужая дата</a>
            """.trimIndent()

        val pages = client.extractEventPageUrls(portalHtml, "01-10-2026")
        assertTrue(pages.any { it.contains("01-10-2026-raspisanie-uchashhihsya") })
        assertTrue(pages.any { it.contains("01-10-2026-raspisanie-prepodavatelej-2") })
        assertTrue("чужая дата не должна попасть", pages.none { it.contains("02-10-2026") })
    }

    @Test
    fun `W1a резолвер пробует URL от probe ПЕРВЫМ, без перебора кандидатов`() {

        val probeUrl = "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya.doc"
        val visited = mutableListOf<String>()
        kotlinx.coroutines.runBlocking {
            client.forEachDocumentUrl(cal(2026, 10, 5), preferredUrl = probeUrl) { url ->
                visited.add(url)
                true
            }
        }
        assertEquals(listOf(probeUrl), visited)
    }

    @Test
    fun `W1a URL от probe отвергнут - резолвер идёт по обычным кандидатам`() {

        val probeUrl = "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya.doc"
        val visited = mutableListOf<String>()
        kotlinx.coroutines.runBlocking {
            client.forEachDocumentUrl(cal(2026, 10, 5), preferredUrl = probeUrl) { url ->
                visited.add(url)
                visited.size == 2
            }
        }
        assertEquals(listOf(probeUrl, "https://guo-mpk.by/wp-content/uploads/2026/10/05.10.2026-raspisanie-uchashhihsya.docx"), visited)
    }
}
