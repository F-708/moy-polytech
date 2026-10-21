package com.politex.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionCodeFormulaTest {
    private fun versionCode(
        major: Int,
        minor: Int,
        patch: Int,
        preview: Int? = null,
    ): Int? {
        val tail = preview ?: 999
        if (major > 213 || minor > 99 || patch > 99 || tail < 1) return null
        if (preview != null && preview > 998) return null
        val code = major.toLong() * 10_000_000L + minor * 100_000L + patch * 1_000L + tail
        if (code > Int.MAX_VALUE) return null
        return code.toInt()
    }

    @Test
    fun `формула релиза - M 10M плюс m 100K плюс p 1K плюс 999`() {
        assertEquals(20000999, versionCode(2, 0, 0)!!)
        assertEquals(20001999, versionCode(2, 0, 1)!!)
        assertEquals(20100999, versionCode(2, 1, 0)!!)
        assertEquals(30000999, versionCode(3, 0, 0)!!)
    }

    @Test
    fun `формула превью - вместо 999 подставляется номер N от 1 до 998`() {
        assertEquals(20001001, versionCode(2, 0, 1, 1)!!)
        assertEquals(20001002, versionCode(2, 0, 1, 2)!!)
        assertEquals(20001998, versionCode(2, 0, 1, 998)!!)
    }

    @Test
    fun `коды растут монотонно - превью ниже релиза той же версии`() {
        assertTrue(versionCode(2, 0, 0)!! < versionCode(2, 0, 1, 1)!!)
        assertTrue(versionCode(2, 0, 1, 1)!! < versionCode(2, 0, 1, 2)!!)
        assertTrue(versionCode(2, 0, 1, 998)!! < versionCode(2, 0, 1)!!)
        assertTrue(versionCode(2, 0, 1)!! < versionCode(2, 1, 0)!!)
        assertTrue(versionCode(2, 99, 99)!! < versionCode(3, 0, 0)!!)
    }

    @Test
    fun `выход за границы сегментов отклоняется`() {
        assertNull(versionCode(2, 100, 0))
        assertNull(versionCode(2, 0, 100))
        assertNull(versionCode(2, 0, 1, 0))
        assertNull(versionCode(2, 0, 1, 999))
    }

    @Test
    fun `переполнение Int отклоняется - границы как в клиентском versionCodeOf`() {
        assertEquals(2_139_999_999, versionCode(213, 99, 99)!!)
        assertNull(versionCode(214, 0, 0))
        assertNull(versionCode(215, 0, 0))
        assertTrue(versionCode(213, 99, 99)!! <= Int.MAX_VALUE)
    }

    @Test
    fun `формула совпадает с клиентской versionCodeOf на канонических тегах`() {
        assertEquals(versionCodeOf("v2.0.0"), versionCode(2, 0, 0))
        assertEquals(versionCodeOf("v2.0.1-preview.2"), versionCode(2, 0, 1, 2))
        assertEquals(versionCodeOf("v2.1.0"), versionCode(2, 1, 0))
        assertEquals(versionCodeOf("v3.0.0"), versionCode(3, 0, 0))
        assertEquals(versionCodeOf("v213.99.99"), versionCode(213, 99, 99))
        assertEquals(versionCodeOf("v214.0.0"), versionCode(214, 0, 0))
    }
}
