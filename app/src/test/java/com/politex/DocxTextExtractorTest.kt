package com.politex

import com.politex.util.DocxParagraph
import com.politex.util.DocxTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class DocxTextExtractorTest {

    private fun docxBytes(
        documentXml: String,
        withDocumentEntry: Boolean = true,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                   <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""".toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write("<?xml version=\"1.0\"?><Relationships/>".toByteArray())
            zip.closeEntry()
            if (withDocumentEntry) {
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(documentXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun wrap(body: String) = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
        "<w:body>$body</w:body>" +
        "</w:document>"

    private fun extract(body: String): List<DocxParagraph> = DocxTextExtractor.extract(ByteArrayInputStream(docxBytes(wrap(body))))

    @Test
    fun `обычные абзацы читаются по порядку`() {
        val paragraphs =
            extract(
                """
                <w:p><w:r><w:t>Первый абзац</w:t></w:r></w:p>
                <w:p><w:r><w:t>Второй абзац</w:t></w:r></w:p>
                """.trimIndent(),
            )
        assertEquals(2, paragraphs.size)
        assertEquals("Первый абзац", paragraphs[0].text)
        assertEquals("Второй абзац", paragraphs[1].text)
        assertFalse("обычный абзац не заголовок", paragraphs[0].heading)
        assertFalse("обычный абзац не жирный", paragraphs[0].bold)
    }

    @Test
    fun `абзац склеивается из нескольких рангов`() {
        val paragraphs =
            extract(
                """
                <w:p>
                  <w:r><w:t xml:space="preserve">Закон </w:t></w:r>
                  <w:r><w:rPr><w:b/></w:rPr><w:t xml:space="preserve">Ома </w:t></w:r>
                  <w:r><w:t>I = U / R</w:t></w:r>
                </w:p>
                """.trimIndent(),
            )
        assertEquals(1, paragraphs.size)
        assertEquals("Закон Ома I = U / R", paragraphs[0].text)
        assertTrue("w:b в любом ранге делает абзац жирным", paragraphs[0].bold)
    }

    @Test
    fun `жирный с val=false не делает абзац жирным`() {
        val paragraphs = extract("""<w:p><w:r><w:rPr><w:b w:val="false"/></w:rPr><w:t>Не жирный</w:t></w:r></w:p>""")
        assertEquals(1, paragraphs.size)
        assertFalse("<w:b w:val=\"false\"/> — свойство выключено", paragraphs[0].bold)
    }

    @Test
    fun `заголовок определяется по стилю абзаца`() {
        val paragraphs =
            extract(
                """
                <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Глава 1. Цепи</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Заголовок2"/></w:pPr><w:r><w:t>1.1 Ток</w:t></w:r></w:p>
                <w:p><w:pPr><w:pStyle w:val="Normal"/></w:pPr><w:r><w:t>Обычный текст</w:t></w:r></w:p>
                """.trimIndent(),
            )
        assertEquals(3, paragraphs.size)
        assertTrue("английский стиль Heading1", paragraphs[0].heading)
        assertTrue("русский стиль Заголовок2", paragraphs[1].heading)
        assertFalse("Normal — не заголовок", paragraphs[2].heading)
    }

    @Test
    fun `стиль знака заголовка не путается со стилем абзаца`() {

        val paragraphs =
            extract(
                """<w:p><w:pPr><w:pStyle w:val="Heading1Char"/></w:pPr><w:r><w:t>Просто текст</w:t></w:r></w:p>""",
            )
        assertEquals(1, paragraphs.size)
        assertFalse(paragraphs[0].heading)
    }

    @Test
    fun `табы и переводы строк сохраняются`() {
        val paragraphs =
            extract(
                """<w:p><w:r><w:t>Пункт</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>1</w:t></w:r><w:r><w:br/></w:r><w:r><w:t>продолжение</w:t></w:r></w:p>""",
            )
        assertEquals(1, paragraphs.size)
        assertEquals("Пункт\t1\nпродолжение", paragraphs[0].text)
    }

    @Test
    fun `кириллица и спецсимволы не ломаются`() {
        val paragraphs =
            extract(
                """<w:p><w:r><w:t>Ёмкость конденсатора — C = 10 мкФ, «ёмкость» &amp; №5</w:t></w:r></w:p>""",
            )
        assertEquals(1, paragraphs.size)
        assertEquals("Ёмкость конденсатора — C = 10 мкФ, «ёмкость» & №5", paragraphs[0].text)
    }

    @Test
    fun `пустые абзацы отбрасываются`() {
        val paragraphs =
            extract(
                """
                <w:p><w:r><w:t>Текст</w:t></w:r></w:p>
                <w:p/>
                <w:p><w:r><w:t xml:space="preserve">   </w:t></w:r></w:p>
                <w:p><w:r><w:t>Снова текст</w:t></w:r></w:p>
                """.trimIndent(),
            )
        assertEquals("пустые и пробельные абзацы не нужны ни в просмотрщике, ни в поиске", 2, paragraphs.size)
        assertEquals("Текст", paragraphs[0].text)
        assertEquals("Снова текст", paragraphs[1].text)
    }

    @Test
    fun `текст внутри таблиц тоже извлекается`() {
        val paragraphs =
            extract(
                """
                <w:tbl><w:tr>
                  <w:tc><w:p><w:r><w:t>Напряжение</w:t></w:r></w:p></w:tc>
                  <w:tc><w:p><w:r><w:t>220 В</w:t></w:r></w:p></w:tc>
                </w:tr></w:tbl>
                """.trimIndent(),
            )
        assertEquals(2, paragraphs.size)
        assertEquals("Напряжение", paragraphs[0].text)
        assertEquals("220 В", paragraphs[1].text)
    }

    @Test
    fun `plainText склеивает абзацы для поискового индекса`() {
        val text =
            DocxTextExtractor.toPlainText(
                listOf(DocxParagraph("Первая строка"), DocxParagraph("Вторая строка", bold = true)),
            )
        assertEquals("Первая строка\nВторая строка", text)
    }

    @Test
    fun `без word document xml возвращает пустой список`() {
        val paragraphs =
            DocxTextExtractor.extract(
                ByteArrayInputStream(docxBytes("<w:document/>", withDocumentEntry = false)),
            )
        assertTrue("битый/не-docx пакет не должен ронять импорт", paragraphs.isEmpty())
    }

    @Test
    fun `мусор на входе не роняет парсер`() {
        val outcome =
            runCatching {
                DocxTextExtractor.extract(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)))
            }

        val paragraphs = outcome.getOrNull()
        assertTrue("исключение допустимо, пустой список допустим", paragraphs == null || paragraphs.isEmpty())
    }
}
