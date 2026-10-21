package com.politex

import com.politex.ui.screens.materials.PortMessage
import com.politex.ui.screens.materials.chromiumMajor
import com.politex.ui.screens.materials.docxRouteWeb
import com.politex.ui.screens.materials.pageFromPort
import com.politex.ui.screens.materials.pageToPort
import com.politex.ui.screens.materials.parsePortMessage
import com.politex.ui.screens.materials.parseRange
import com.politex.ui.screens.materials.pdfRouteWeb
import com.politex.ui.screens.materials.useRanges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocWebBridgeTest {

    @Test
    fun `page N OF разбирается, 1-based`() {
        assertEquals(PortMessage.Page(3, 10), parsePortMessage("page 3 10"))
    }

    @Test
    fun `page вне диапазона дропается`() {
        assertNull(parsePortMessage("page 0 10"))
        assertNull(parsePortMessage("page 11 10"))
        assertNull(parsePortMessage("page 3 0"))
        assertNull(parsePortMessage("page x 10"))
        assertNull(parsePortMessage("page 3"))
    }

    @Test
    fun `search count и locked`() {
        assertEquals(PortMessage.SearchCount(5, 20, true), parsePortMessage("5 20 1"))
        assertEquals(PortMessage.SearchCount(0, 0, false), parsePortMessage("0 0 0"))
        assertNull(parsePortMessage("-1 5 1"))
        assertEquals(PortMessage.Locked, parsePortMessage("locked"))
    }

    @Test
    fun `ink-глаголы старого протокола больше не разбираются`() {
        assertNull(parsePortMessage("ink 0 0.1 0.2"))
        assertNull(parsePortMessage("zap 0 0.5 0.5 1.4"))
        assertNull(parsePortMessage("layout 1080"))
    }

    @Test
    fun `мусор и пустота дропаются молча`() {
        assertNull(parsePortMessage(null))
        assertNull(parsePortMessage(""))
        assertNull(parsePortMessage("garbage"))
        assertNull(parsePortMessage("q произвольный текст поиска"))
    }

    @Test
    fun `useRanges порог 16МБ`() {
        assertTrue(useRanges(16L * 1024 * 1024))
        assertFalse(useRanges(16L * 1024 * 1024 - 1))
    }

    @Test
    fun `parseRange — валидные, кламп и отказы`() {
        assertEquals(0L to 99L, parseRange("bytes=0-99", 1000))
        assertEquals(500L to 999L, parseRange("bytes=500-", 1000))
        assertEquals(0L to 999L, parseRange("bytes=0-5000", 1000))
        assertNull(parseRange("bytes=2000-3000", 1000))
        assertNull(parseRange("bytes=100-50", 1000))
        assertNull(parseRange("garbage", 1000))
        assertNull(parseRange("bytes=0-99", 0))
    }

    @Test
    fun `конвертер страниц round-trip`() {
        assertEquals(1, pageToPort(0))
        assertEquals(0, pageFromPort(1))
        for (p in 0..50) assertEquals(p, pageFromPort(pageToPort(p)))
    }

    @Test
    fun `pdfRouteWeb — WebView только при новом движке, целом файле и не-Huawei`() {
        assertTrue(pdfRouteWeb(fileExists = true, pageCount = 5, major = 125, locked = false))
        assertFalse(pdfRouteWeb(true, 5, 124, false))
        assertFalse(pdfRouteWeb(true, 5, null, false))
        assertFalse(pdfRouteWeb(true, 5, 130, true))
        assertFalse(pdfRouteWeb(true, 0, 130, false))
        assertFalse(pdfRouteWeb(false, 5, 130, false))
    }

    @Test
    fun `docxRouteWeb — правленые, отсутствующие и Huawei не в WebView`() {
        assertTrue(docxRouteWeb(textEdited = false, fileExists = true, major = 80, locked = false))
        assertTrue(docxRouteWeb(false, true, null, false))
        assertFalse(docxRouteWeb(false, true, 79, false))
        assertFalse(docxRouteWeb(false, true, null, true))
        assertTrue(docxRouteWeb(false, true, 100, true))
        assertFalse(docxRouteWeb(true, true, 130, false))
        assertFalse(docxRouteWeb(false, false, 130, false))
    }

    @Test
    fun `chromiumMajor — из UA, из пакета, Huawei-мусор отбрасывается`() {
        assertEquals(138, chromiumMajor("Mozilla/5.0 Chrome/138.0.7204.179 Mobile") { null })
        assertEquals(125, chromiumMajor(null) { "125.0.6422.76" })
        assertEquals(91, chromiumMajor("Mozilla/5.0 (no chrome token)") { "91.0.4472" })
        assertNull(chromiumMajor(null) { "15.0.4.326" })
        assertNull(chromiumMajor(null) { null })
    }
}
