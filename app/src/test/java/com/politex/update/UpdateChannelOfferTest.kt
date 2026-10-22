package com.politex.update

import com.politex.util.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateChannelOfferTest {
    private fun meta(
        channel: String,
        version: String,
        code: Int,
    ) = UpdateMeta(
        channel = channel,
        version = version,
        versionCode = code,
        apk = "https://github.com/o/r/releases/download/v$version/app.apk",
        sha256 = "",
        publishedAt = "",
        minVersionCode = 0,
    )

    @Test
    fun `кейс заказчика - preview-клиент получает более новый stable`() {
        val stableCode = versionCodeOf("v2.0.1")!!
        val previewCode = versionCodeOf("v2.0.1-preview.2")!!
        assertTrue(stableCode > previewCode)
        val info = UpdateMetaSource.selectFromMeta(meta("stable", "2.0.1", stableCode), UpdateChannel.PREVIEW, previewCode)
        assertNotNull(info)
        assertEquals(stableCode, info!!.versionCode)
        assertEquals("2.0.1", info.versionName)
    }

    @Test
    fun `preview-клиент не получает stable старее установленного`() {
        assertNull(
            UpdateMetaSource.selectFromMeta(meta("stable", "2.0.0", 20000999), UpdateChannel.PREVIEW, 20001002),
        )
    }

    @Test
    fun `preview-клиент получает более новый preview`() {
        assertNotNull(
            UpdateMetaSource.selectFromMeta(meta("preview", "2.1.0-preview.1", 20010001), UpdateChannel.PREVIEW, 20001999),
        )
    }

    @Test
    fun `stable-клиент не получает preview-мету`() {
        assertNull(
            UpdateMetaSource.selectFromMeta(meta("preview", "2.0.1-preview.2", 20001002), UpdateChannel.STABLE, 20000999),
        )
    }

    @Test
    fun `stable-клиент получает более новый stable`() {
        assertNotNull(
            UpdateMetaSource.selectFromMeta(meta("stable", "2.0.1", 20001999), UpdateChannel.STABLE, 20000999),
        )
    }

    @Test
    fun `равный код не предлагается ни одному каналу`() {
        assertNull(UpdateMetaSource.selectFromMeta(meta("stable", "2.0.1", 20001999), UpdateChannel.STABLE, 20001999))
        assertNull(UpdateMetaSource.selectFromMeta(meta("stable", "2.0.1", 20001999), UpdateChannel.PREVIEW, 20001999))
        assertNull(
            UpdateMetaSource.selectFromMeta(meta("preview", "2.0.1-preview.2", 20001002), UpdateChannel.PREVIEW, 20001002),
        )
    }

    @Test
    fun `даунгрейд не предлагается никому`() {
        assertNull(UpdateMetaSource.selectFromMeta(meta("stable", "1.9.0", 10090999), UpdateChannel.PREVIEW, 20001002))
        assertNull(UpdateMetaSource.selectFromMeta(meta("stable", "1.9.0", 10090999), UpdateChannel.STABLE, 20001999))
    }

    @Test
    fun `предикат канала - stable виден всем, preview только preview`() {
        assertTrue(UpdateMetaSource.metaMatchesChannel("stable", UpdateChannel.STABLE))
        assertTrue(UpdateMetaSource.metaMatchesChannel("stable", UpdateChannel.PREVIEW))
        assertFalse(UpdateMetaSource.metaMatchesChannel("preview", UpdateChannel.STABLE))
        assertTrue(UpdateMetaSource.metaMatchesChannel("preview", UpdateChannel.PREVIEW))
    }
}
