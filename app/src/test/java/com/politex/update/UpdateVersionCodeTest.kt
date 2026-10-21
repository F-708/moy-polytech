package com.politex.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionCodeTest {
    @Test
    fun `формула из ТЗ — 2_0_0 даёт 20000999`() {
        assertEquals(20000999, versionCodeOf("2.0.0"))
        assertEquals(20000999, versionCodeOf("v2.0.0"))
    }

    @Test
    fun `патч и минор попадают в свои разряды`() {
        assertEquals(20001999, versionCodeOf("v2.0.1"))
        assertEquals(20100999, versionCodeOf("2.1.0"))
        assertEquals(20200999, versionCodeOf("2.2"))
        assertEquals(30000999, versionCodeOf("3.0.0"))
    }

    @Test
    fun `превью-суффикс даёт номер сборки вместо 999`() {
        assertEquals(20100003, versionCodeOf("2.1.0-preview.3"))
        assertEquals(20100000, versionCodeOf("2.1.0-preview"))
        assertEquals(20100012, versionCodeOf("v2.1.0-rc.12"))
        assertEquals(20200000, versionCodeOf("2.2-admin"))
    }

    @Test
    fun `build-метаданные после плюса игнорируются`() {
        assertEquals(20001999, versionCodeOf("2.0.1+fdroid"))
        assertEquals(20100001, versionCodeOf("2.1.0-preview.1+fdroid"))
    }

    @Test
    fun `стабильный код всегда больше превью той же версии`() {
        val stable = versionCodeOf("2.1.0")!!
        val preview = versionCodeOf("2.1.0-preview.99")!!
        assertTrue(stable > preview)
        assertEquals(20100999, stable)
        assertEquals(20100099, preview)
    }

    @Test
    fun `порядок версий монотонен`() {
        val codes =
            listOf("2.0.0", "2.0.1-preview.1", "2.0.1", "2.1.0-preview.1", "2.1.0", "3.0.0")
                .map { versionCodeOf(it)!! }
        assertEquals(codes.sorted(), codes)
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `номер превью больше 998 прижимается к 998 и остаётся ниже стабильного`() {
        assertEquals(20100998, versionCodeOf("2.1.0-preview.1500"))
        assertTrue(versionCodeOf("2.1.0-preview.1500")!! < versionCodeOf("2.1.0")!!)
    }

    @Test
    fun `мусор и выход за границы разрядов отклоняются`() {
        assertNull(versionCodeOf(null))
        assertNull(versionCodeOf(""))
        assertNull(versionCodeOf("   "))
        assertNull(versionCodeOf("abc"))
        assertNull(versionCodeOf("1.2.3.4"))
        assertNull(versionCodeOf("2.100.0"))
        assertNull(versionCodeOf("2.0.100"))
        assertNull(versionCodeOf("214.0.0"))
        assertNull(versionCodeOf("2.0.-1"))
        assertNull(versionCodeOf("v"))
    }

    @Test
    fun `границы разрядов принимаются без переполнения`() {
        assertEquals(213 * 10_000_000 + 99 * 100_000 + 99 * 1_000 + 999, versionCodeOf("213.99.99"))
        assertTrue(versionCodeOf("213.99.99")!! > 0)
    }

    @Test
    fun `превью-тег распознаётся по суффиксу`() {
        assertFalse(isPreviewTag("v2.0.1"))
        assertFalse(isPreviewTag("2.0.1+build-5"))
        assertTrue(isPreviewTag("2.1.0-preview.1"))
        assertTrue(isPreviewTag("2.2-admin"))
        assertFalse(isPreviewTag(null))
        assertFalse(isPreviewTag(""))
    }
}
