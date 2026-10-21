package com.politex.util

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

data class DocxParagraph(
    val text: String,
    val bold: Boolean = false,
    val heading: Boolean = false,
)

object DocxTextExtractor {

    private const val DOCUMENT_ENTRY = "word/document.xml"

    private val HEADING_STYLE = Regex("^(heading|заголовок|title|название)\\s*\\d*$", RegexOption.IGNORE_CASE)

    private val NOT_HEADING_STYLE = Regex("^(heading\\s*[1-9]\\s*char|заголовок\\s*\\d+\\s*знак)", RegexOption.IGNORE_CASE)

    fun extract(source: InputStream): List<DocxParagraph> {
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return emptyList()
                if (entry.name == DOCUMENT_ENTRY) return parseDocumentXml(zip)
                zip.closeEntry()
            }
        }
    }

    fun toPlainText(paragraphs: List<DocxParagraph>): String = paragraphs.joinToString("\n") { it.text }

    private fun parseDocumentXml(stream: InputStream): List<DocxParagraph> {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        parser.setInput(stream, "UTF-8")

        val out = ArrayList<DocxParagraph>()
        val buffer = StringBuilder()
        var inParagraph = false
        var bold = false
        var heading = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (localName(parser)) {
                        "p" -> {
                            inParagraph = true
                            buffer.setLength(0)
                            bold = false
                            heading = false
                        }
                        "t" -> if (inParagraph) buffer.append(parser.nextText())
                        "tab" -> if (inParagraph) buffer.append('\t')
                        "br" -> if (inParagraph) buffer.append('\n')
                        "b" -> if (inParagraph && !isSwitchedOff(parser.attr("val"))) bold = true
                        "pStyle" -> {
                            val style = parser.attr("val") ?: ""
                            if (HEADING_STYLE.matches(style.trim()) && !NOT_HEADING_STYLE.matches(style.trim())) {
                                heading = true
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (inParagraph && localName(parser) == "p") {
                        inParagraph = false
                        val text = buffer.toString().trim()
                        if (text.isNotEmpty()) out.add(DocxParagraph(text, bold, heading))
                    }
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun isSwitchedOff(value: String?): Boolean = value.equals("false", ignoreCase = true) || value == "0" || value.equals("none", ignoreCase = true)

    private fun localName(parser: XmlPullParser): String = parser.name.substringAfterLast(':')

    private fun XmlPullParser.attr(local: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i).substringAfterLast(':') == local) return getAttributeValue(i)
        }
        return null
    }
}
