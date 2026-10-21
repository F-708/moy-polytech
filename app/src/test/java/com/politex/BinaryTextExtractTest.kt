package com.politex

import com.politex.util.BinaryTextExtract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryTextExtractTest {
    private fun utf16(s: String): ByteArray {
        val out = mutableListOf<Byte>()
        for (ch in s) {
            out.add((ch.code and 0xFF).toByte())
            out.add(((ch.code shr 8) and 0xFF).toByte())
        }
        return out.toByteArray()
    }

    @Test
    fun `хвостовой utf16-ран тоже проходит accept`() {
        val bytes = utf16("МОЛОКО")
        val rejecting = BinaryTextExtract.utf16LeRuns(bytes, bellAsTab = false, minRunBytes = 2) { false }
        assertTrue("хвост без завершающего байта должен отбраковываться accept", rejecting.isEmpty())
    }

    @Test
    fun `хвостовой cp1251-ран тоже проходит accept`() {
        val bytes = "МОЛОКО".toByteArray(BinaryTextExtract.CP1251)
        val rejecting = BinaryTextExtract.cp1251Runs(bytes, bellAsTab = false, minRunBytes = 2) { false }
        assertTrue("хвост cp1251 без завершающего байта должен отбраковываться accept", rejecting.isEmpty())
    }

    @Test
    fun `accept пропускающий сохраняет ран`() {
        val bytes = "МОЛОКО".toByteArray(BinaryTextExtract.CP1251)
        val kept = BinaryTextExtract.cp1251Runs(bytes, bellAsTab = false, minRunBytes = 2) { it.contains("МОЛОКО") }
        assertEquals(1, kept.size)
        assertTrue(kept.single().contains("МОЛОКО"))
    }
}
