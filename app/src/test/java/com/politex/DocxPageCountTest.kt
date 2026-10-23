package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.MaterialImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class DocxPageCountTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun docxBytes(
        documentXml: String,
        withDocumentEntry: Boolean = true,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>".toByteArray())
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

    private fun pageOfText(
        page: Int,
        paragraphs: Int = 20,
    ): String = (1..paragraphs).joinToString("") { "<w:p><w:r><w:t>Страница $page абзац $it</w:t></w:r></w:p>" }

    private fun countOf(body: String): Int = MaterialImporter.docxPageCount(ByteArrayInputStream(docxBytes(wrap(body))))

    @Test
    fun `многостраничный документ из Word - счётчик равен числу страниц`() {
        val pages = 12
        val body =
            (1..pages).joinToString("") { p ->
                pageOfText(p) + if (p < pages) "<w:p><w:r><w:lastRenderedPageBreak/></w:r></w:p>" else ""
            }
        assertEquals(
            "11 маркеров lastRenderedPageBreak делят текст на 12 страниц — как во вьювере",
            pages,
            countOf(body),
        )
    }

    @Test
    fun `документ без маркеров разрыва - одна страница`() {
        assertEquals(1, countOf(pageOfText(1)))
    }

    @Test
    fun `явные разрывы страниц тоже считаются`() {
        val body =
            pageOfText(1) +
                "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>" +
                pageOfText(2) +
                "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>" +
                pageOfText(3) +
                "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>" +
                pageOfText(4)
        assertEquals(4, countOf(body))
    }

    @Test
    fun `lastRendered и явные разрывы суммируются без дублей`() {
        val body =
            (1..6).joinToString("") { p ->
                pageOfText(p) +
                    "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>" +
                    if (p < 6) "<w:p><w:r><w:lastRenderedPageBreak/></w:r></w:p>" else ""
            }
        assertEquals("6 явных + 5 lastRendered = 11 разрывов = 12 страниц", 12, countOf(body))
    }

    @Test
    fun `битый архив и не-docx дают 0 - счётчик скрывается`() {
        runBlocking {
            assertEquals(0, MaterialImporter.docxPageCount(ByteArrayInputStream(docxBytes("<w:document/>", withDocumentEntry = false))))
            val garbage = File(context.cacheDir, "broken.docx").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
            assertEquals(0, MaterialImporter.docxPageCount(garbage))
            garbage.delete()
        }
    }

    @Test
    fun `вьювер честно разбивает страницы Word - контракт ассетов`() {
        fun assetText(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

        val docxJs = assetText("viewer/docx.js")
        assertTrue(
            "docx.js обязан чтить lastRenderedPageBreak, иначе многостраничник схлопывается в одну секцию",
            docxJs.contains("ignoreLastRenderedPageBreak: false"),
        )
        assertTrue("разрывы страниц включены", docxJs.contains("breakPages: true"))

        val pageJs = assetText("viewer/mpk-page.js")
        assertTrue(
            "счётчик N/M считает отрендеренные section.docx",
            pageJs.contains("section.docx"),
        )
        assertTrue(assetText("viewer/docx.html").contains("mpk-page.js"))
    }
}
