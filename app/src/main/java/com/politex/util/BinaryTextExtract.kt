package com.politex.util

import java.nio.charset.Charset

object BinaryTextExtract {
    val CP1251: Charset = Charset.forName("windows-1251")

    fun utf16LeRuns(
        bytes: ByteArray,
        bellAsTab: Boolean,
        minRunBytes: Int = 6,
        accept: (String) -> Boolean = { true },
    ): List<String> {
        val runs = mutableListOf<String>()
        val buffer = mutableListOf<Byte>()
        var i = 0
        while (i < bytes.size - 1) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val isAscii = b1 == 0 &&
                ((b0 in 0x20..0x7E) || b0 == 0x09 || b0 == 0x0A || b0 == 0x0D || (bellAsTab && b0 == 0x07))
            val isCyrillic = b1 == 0x04 && b0 <= 0x5F
            val isBoxDrawing = b1 == 0x25
            if (isAscii || isCyrillic || isBoxDrawing) {
                if (bellAsTab && b1 == 0 && b0 == 0x07) {
                    buffer.add(0x09.toByte())
                    buffer.add(0x00.toByte())
                } else {
                    buffer.add(bytes[i])
                    buffer.add(bytes[i + 1])
                }
                i += 2
            } else {
                flushUtf16(buffer, minRunBytes, accept)?.let(runs::add)
                buffer.clear()
                i += 2
            }
        }
        flushUtf16(buffer, minRunBytes, accept)?.let(runs::add)
        return runs
    }

    fun cp1251Runs(
        bytes: ByteArray,
        bellAsTab: Boolean,
        minRunBytes: Int,
        accept: (String) -> Boolean = { true },
    ): List<String> {
        val runs = mutableListOf<String>()
        val buffer = mutableListOf<Byte>()
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            val isValid = (unsigned in 0x20..0x7E) ||
                (unsigned in 0xC0..0xFF) ||
                unsigned == 0xA8 ||
                unsigned == 0xB8 ||
                unsigned == 0x09 ||
                unsigned == 0x0A ||
                unsigned == 0x0D ||
                (bellAsTab && unsigned == 0x07)
            if (isValid) {
                buffer.add(if (bellAsTab && unsigned == 0x07) 0x09.toByte() else b)
            } else {
                if (buffer.size >= minRunBytes) {
                    val decoded = String(buffer.toByteArray(), CP1251)
                    if (accept(decoded)) runs.add(decoded)
                }
                buffer.clear()
            }
        }
        if (buffer.size >= minRunBytes) {
            val decoded = String(buffer.toByteArray(), CP1251)
            if (accept(decoded)) runs.add(decoded)
        }
        return runs
    }

    private fun flushUtf16(
        buffer: List<Byte>,
        minRunBytes: Int,
        accept: (String) -> Boolean,
    ): String? {
        if (buffer.size < minRunBytes) return null
        val decoded = String(buffer.toByteArray(), Charsets.UTF_16LE)
        return if (accept(decoded)) decoded else null
    }

    fun hasText(decoded: String): Boolean = decoded.any { it in 'А'..'я' || it in 'A'..'z' || it in '0'..'9' }
}
