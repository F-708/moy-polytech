package com.politex.update

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateDownloadProgressTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `прогресс сообщается по мере записи байтов`() {
        val target = File(tempFolder.root, "a.apk")
        val seen = mutableListOf<Pair<Long, Long>>()
        val body = ByteArray(20480) { 1 }.toResponseBody(null)
        val ok =
            UpdateDownloader.writeBody(body, target, 20480L, { read, total -> seen.add(read to total) }) { false }
        assertTrue(ok)
        assertEquals(20480L, target.length())
        assertEquals(0L, seen.first().first)
        assertEquals(20480L, seen.last().first)
        assertEquals(20480L, seen.last().second)
        assertTrue(seen.size >= 3)
    }

    @Test
    fun `отмена прерывает запись`() {
        val target = File(tempFolder.root, "b.apk")
        var checks = 0
        val body = ByteArray(65536) { 1 }.toResponseBody(null)
        val ok = UpdateDownloader.writeBody(body, target, 65536L, null) { checks++ >= 2 }
        assertFalse(ok)
        assertTrue(target.length() < 65536L)
    }

    @Test
    fun `нулевой total не ломает прогресс`() {
        val target = File(tempFolder.root, "c.apk")
        val seen = mutableListOf<Long>()
        val body = ByteArray(16) { 1 }.toResponseBody(null)
        val ok = UpdateDownloader.writeBody(body, target, -1L, { read, _ -> seen.add(read) }) { false }
        assertTrue(ok)
        assertEquals(16L, target.length())
        assertEquals(0L, seen.first())
        assertEquals(16L, seen.last())
    }
}
