package com.politex.update

import com.politex.util.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UpdateMetaEtagTest {
    private val stableMeta =
        """
        {
          "channel": "stable",
          "version": "2.0.1",
          "version_code": 20001999,
          "apk": "https://example.com/app.apk",
          "sha256": "aa",
          "published_at": "2026-10-22T12:00:00Z",
          "min_version_code": 0
        }
        """.trimIndent()

    private val previewMeta =
        """
        {
          "channel": "preview",
          "version": "2.1.0-preview.4",
          "version_code": 20100004,
          "apk": "https://example.com/preview.apk"
        }
        """.trimIndent()

    private fun evaluate(
        status: Int,
        body: String?,
        etag: String? = null,
        cached: String? = null,
        channel: UpdateChannel = UpdateChannel.STABLE,
        current: Int = 20000999,
    ): MetaEvaluation = UpdateCheckController.evaluateMeta(status, body, etag, cached, channel, current)

    @Test
    fun `304 с кэшем и свежей версией предлагает обновление без сети`() {
        val evaluation = evaluate(304, null, cached = stableMeta)
        assertFalse(evaluation.fallbackToApi)
        assertEquals("2.0.1", evaluation.info?.versionName)
        assertNull(evaluation.storeEtag)
        assertNull(evaluation.storeBody)
    }

    @Test
    fun `304 с кэшем без более новой версии — уже свежее`() {
        val evaluation = evaluate(304, null, cached = stableMeta, current = 20001999)
        assertFalse(evaluation.fallbackToApi)
        assertNull(evaluation.info)
    }

    @Test
    fun `304 учитывает канал — превью-мета не предлагается стабильному`() {
        val evaluation = evaluate(304, null, cached = previewMeta, channel = UpdateChannel.STABLE)
        assertFalse(evaluation.fallbackToApi)
        assertNull(evaluation.info)
        val previewEvaluation = evaluate(304, null, cached = previewMeta, channel = UpdateChannel.PREVIEW)
        assertEquals("2.1.0-preview.4", previewEvaluation.info?.versionName)
    }

    @Test
    fun `304 без кэша уходит в запасной api-путь`() {
        val evaluation = evaluate(304, null, cached = null)
        assertTrue(evaluation.fallbackToApi)
        assertNull(evaluation.info)
    }

    @Test
    fun `200 с валидной метой сохраняет etag и тело`() {
        val evaluation = evaluate(200, stableMeta, etag = "W/\"abc123\"")
        assertFalse(evaluation.fallbackToApi)
        assertEquals("2.0.1", evaluation.info?.versionName)
        assertEquals("W/\"abc123\"", evaluation.storeEtag)
        assertEquals(stableMeta, evaluation.storeBody)
    }

    @Test
    fun `200 без более новой версии всё равно кэшируется`() {
        val evaluation = evaluate(200, stableMeta, etag = "W/\"abc123\"", current = 20001999)
        assertNull(evaluation.info)
        assertEquals("W/\"abc123\"", evaluation.storeEtag)
        assertEquals(stableMeta, evaluation.storeBody)
    }

    @Test
    fun `200 без etag-заголовка не кэшируется`() {
        val evaluation = evaluate(200, stableMeta, etag = null)
        assertNotNull(evaluation.info)
        assertNull(evaluation.storeEtag)
        assertNull(evaluation.storeBody)
    }

    @Test
    fun `200 с невалидным телом уходит в запасной путь без кэша`() {
        val evaluation = evaluate(200, "{сломанный json", etag = "W/\"x\"")
        assertTrue(evaluation.fallbackToApi)
        assertNull(evaluation.storeEtag)
        assertNull(evaluation.storeBody)
    }

    @Test
    fun `404 и прочие статусы уходят в запасной путь`() {
        assertTrue(evaluate(404, null).fallbackToApi)
        assertTrue(evaluate(500, "ошибка").fallbackToApi)
        assertTrue(evaluate(429, null).fallbackToApi)
    }

    @Test
    fun `смена канала требует немедленной проверки`() {
        assertTrue(channelChangeRequiresCheck(UpdateChannel.STABLE, UpdateChannel.PREVIEW))
        assertTrue(channelChangeRequiresCheck(UpdateChannel.PREVIEW, UpdateChannel.STABLE))
        assertFalse(channelChangeRequiresCheck(UpdateChannel.STABLE, UpdateChannel.STABLE))
        assertFalse(channelChangeRequiresCheck(UpdateChannel.PREVIEW, UpdateChannel.PREVIEW))
    }

    @Test
    fun `статусные строки для ui`() {
        assertEquals("Установлена 2.2 · канал стабильный", defaultUpdateStatus(UpdateChannel.STABLE, "2.2"))
        assertEquals("Установлена 2.2 · канал превью", defaultUpdateStatus(UpdateChannel.PREVIEW, "2.2"))
        val found =
            ManualCheckResult.UpdateFound(
                UpdateInfo(
                    tagName = "v2.0.1",
                    versionName = "2.0.1",
                    versionCode = 20001999,
                    notes = "",
                    asset = UpdateAsset("a.apk", "https://example.com/a.apk", 0L),
                ),
            )
        assertEquals("Найдено обновление 2.0.1", manualResultText(found, UpdateChannel.STABLE, "2.2"))
        assertEquals(
            "Вы уже используете последнюю версию",
            manualResultText(ManualCheckResult.Latest, UpdateChannel.STABLE, "2.2"),
        )
        assertEquals("Нет соединения", manualResultText(ManualCheckResult.NetworkError, UpdateChannel.STABLE, "2.2"))
        assertEquals(
            "Установлена 2.2 · канал стабильный",
            manualResultText(ManualCheckResult.Disabled, UpdateChannel.STABLE, "2.2"),
        )
        assertTrue(manualResultIsError(ManualCheckResult.NetworkError))
        assertFalse(manualResultIsError(ManualCheckResult.Latest))
        assertTrue(manualResultIsFound(found))
        assertFalse(manualResultIsFound(ManualCheckResult.Latest))
    }

    @Test
    fun `sha256 из меты сверяется с файлом`() {
        val file = File.createTempFile("update-check", ".apk")
        try {
            file.writeBytes("содержимое apk".toByteArray())
            val expected = UpdateDownloader.sha256Hex("содержимое apk".toByteArray())
            assertTrue(UpdateDownloader.matchesChecksum(file, expected))
            assertTrue(UpdateDownloader.matchesChecksum(file, expected.uppercase()))
            assertFalse(UpdateDownloader.matchesChecksum(file, "00".repeat(32)))
            assertTrue(UpdateDownloader.matchesChecksum(file, null))
            assertTrue(UpdateDownloader.matchesChecksum(file, "  "))
            assertFalse(UpdateDownloader.matchesChecksum(File(file.absolutePath + ".missing"), expected))
        } finally {
            file.delete()
        }
    }
}
