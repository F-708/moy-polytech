package com.politex

import com.politex.util.IntakeDecision
import com.politex.util.MaterialFormatKind
import com.politex.util.MaterialFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Матрица «формат → принимать/отклонять». Чистые unit-тесты: Android API не нужны.
 */
class MaterialFormatsTest {

    private fun head(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun accepted(decision: IntakeDecision): IntakeDecision.Accept {
        assertTrue("ожидали приём, получили $decision", decision is IntakeDecision.Accept)
        return decision as IntakeDecision.Accept
    }

    private fun rejected(decision: IntakeDecision): IntakeDecision.Reject {
        assertTrue("ожидали отказ, получили $decision", decision is IntakeDecision.Reject)
        return decision as IntakeDecision.Reject
    }

    @Test
    fun `поддерживаемые форматы принимаются своими типами`() {
        val cases =
            listOf(
                Triple("лекция.pdf", "application/pdf", MaterialFormatKind.PDF),
                Triple(
                    "лекция.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    MaterialFormatKind.DOCX,
                ),
                Triple("конспект.txt", "text/plain", MaterialFormatKind.TEXT),
                Triple("конспект.md", "text/markdown", MaterialFormatKind.TEXT),
                Triple("оценки.csv", "text/csv", MaterialFormatKind.TEXT),
                Triple("данные.json", "application/json", MaterialFormatKind.TEXT),
                Triple("схема.xml", "application/xml", MaterialFormatKind.TEXT),
                Triple("кадр.png", "image/png", MaterialFormatKind.IMAGE),
                Triple("кадр.heic", "image/heic", MaterialFormatKind.IMAGE),
                Triple("кадр.gif", "image/gif", MaterialFormatKind.IMAGE),
            )
        cases.forEach { (name, mime, kind) ->
            assertEquals("$name должен приниматься как $kind", kind, accepted(MaterialFormats.decide(name, mime)).kind)
        }
    }

    @Test
    fun `markdown не обрезается до шести символов`() {
        val decision = accepted(MaterialFormats.decide("конспект.markdown", null))
        assertEquals("markdown", decision.ext)
        assertEquals(MaterialFormatKind.TEXT, decision.kind)
    }

    @Test
    fun `расширение нормализуется`() {
        assertEquals("txt", MaterialFormats.extOf("файл.TXT"))
        assertEquals("pdf", MaterialFormats.extOf("Скан 2026.PDF"))
        assertEquals("", MaterialFormats.extOf("без-расширения"))
        assertEquals("", MaterialFormats.extOf(null))
    }

    @Test
    fun `чужие форматы отклоняются с понятным текстом`() {
        val cases =
            listOf(
                "лекции.zip" to "application/zip",
                "запись.mp3" to "audio/mpeg",
                "клип.mp4" to "video/mp4",
                "ведомость.xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "старое.doc" to "application/msword",
                "презентация.pptx" to null,
                "установщик.apk" to "application/vnd.android.package-archive",
                "страница.rtf" to "application/rtf",
            )
        cases.forEach { (name, mime) ->
            val decision = rejected(MaterialFormats.decide(name, mime))
            assertTrue("$name должен отклоняться жёстко", decision.hard)
            assertTrue("$name: текст отказа пустой", decision.message.isNotBlank())
        }
    }

    @Test
    fun `apk отклоняется по mime даже без расширения`() {
        val decision = rejected(MaterialFormats.decide("обновление", "application/vnd.android.package-archive"))
        assertEquals(MaterialFormats.APK_MESSAGE, decision.message)
    }

    @Test
    fun `pdf по сигнатуре принимается с octet-stream и без расширения`() {
        val decision = accepted(MaterialFormats.decide("document", "application/octet-stream", "%PDF-1.7\n1 0 obj\n".toByteArray()))
        assertEquals(MaterialFormatKind.PDF, decision.kind)
        assertEquals("pdf", decision.ext)
    }

    @Test
    fun `png по сигнатуре принимается без имени и типа`() {
        val png = head(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val decision = accepted(MaterialFormats.decide("кадр", null, png))
        assertEquals(MaterialFormatKind.IMAGE, decision.kind)
        assertEquals("png", decision.ext)
    }

    @Test
    fun `zip под чужим именем отклоняется как архив`() {
        val zip = head(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        val decision = rejected(MaterialFormats.decide("конспект.txt", "text/plain", zip))
        assertEquals(MaterialFormats.ARCHIVE_MESSAGE, decision.message)
    }

    @Test
    fun `docx остаётся docx при сигнатуре zip`() {
        val zip = head(0x50, 0x4B, 0x03, 0x04)
        assertEquals(MaterialFormatKind.DOCX, accepted(MaterialFormats.decide("лекция.docx", null, zip)).kind)
    }

    @Test
    fun `apk по сигнатуре zip и имени отклоняется`() {
        val apk = head(0x50, 0x4B, 0x03, 0x04)
        val decision = rejected(MaterialFormats.decide("обновление.apk", "application/vnd.android.package-archive", apk))
        assertEquals(MaterialFormats.APK_MESSAGE, decision.message)
    }

    @Test
    fun `OLE2 отклоняется как старый Word и как таблица`() {
        val ole = head(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)
        assertEquals(MaterialFormats.OLD_WORD_MESSAGE, rejected(MaterialFormats.decide("лекция.doc", null, ole)).message)
        assertEquals(MaterialFormats.TABLE_MESSAGE, rejected(MaterialFormats.decide("ведомость.xls", null, ole)).message)
    }

    @Test
    fun `аудио и видео отклоняются по сигнатуре`() {
        assertEquals(
            MaterialFormats.AUDIO_VIDEO_MESSAGE,
            rejected(MaterialFormats.decide("запись", null, head(0x49, 0x44, 0x33, 0x04))).message,
        )
        val mp4 = ByteArray(12)
        "ftypisom".toByteArray(Charsets.US_ASCII).copyInto(mp4, 4)
        assertEquals(
            MaterialFormats.AUDIO_VIDEO_MESSAGE,
            rejected(MaterialFormats.decide("клип", null, mp4)).message,
        )
    }

    @Test
    fun `пустой файл отклоняется отдельным сообщением`() {
        assertEquals(
            MaterialFormats.EMPTY_MESSAGE,
            rejected(MaterialFormats.decide("лекция.pdf", "application/pdf", ByteArray(0))).message,
        )
    }

    @Test
    fun `без метаданных отказ мягкий — файл не опознан`() {
        assertFalse(rejected(MaterialFormats.decide("документ", null)).hard)
    }

    @Test
    fun `текстовый файл принимается как текст`() {
        val text = "Конспект лекции по ТОЭ".toByteArray()
        assertEquals(MaterialFormatKind.TEXT, accepted(MaterialFormats.decide("конспект.txt", null, text)).kind)
    }

    @Test
    fun `текст с BM в начале не превращается в bmp`() {
        val text = "BMW — конспект по устройству автомобиля".toByteArray()
        assertEquals(MaterialFormatKind.TEXT, accepted(MaterialFormats.decide("конспект.txt", null, text)).kind)
    }

    @Test
    fun `пикер не содержит wildcard и octet-stream`() {
        val mimes = MaterialFormats.SUPPORTED_FILE_MIME_TYPES
        assertTrue("пикер пустой", mimes.isNotEmpty())
        assertFalse("*/*" in mimes)
        assertFalse("application/octet-stream" in mimes)
        mimes.forEach { mime ->
            val ext = MaterialFormats.extForMime(mime)
            assertTrue("$mime должен вести к поддерживаемому формату", ext != null && ext in MaterialFormats.SUPPORTED_EXT)
        }
    }

    @Test
    fun `карта MIME согласована с вайтлистом`() {
        MaterialFormats.MIME_TO_EXT.forEach { (mime, ext) ->
            assertTrue("$mime -> $ext вне вайтлиста", ext in MaterialFormats.SUPPORTED_EXT)
            val decision = MaterialFormats.decide("файл", mime)
            assertTrue("$mime должен приниматься без расширения: $decision", decision is IntakeDecision.Accept)
        }
    }
}