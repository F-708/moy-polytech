package com.politex

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.politex.ui.screens.materials.parseInlineMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParseInlineMarksTest {
    @Test
    fun `токены становятся спанами а текст теряет маркеры`() {
        val s = parseInlineMarks("**жирный** и *курсив* и __подчёркнутый__")
        assertEquals("жирный и курсив и подчёркнутый", s.text)
        val spans = s.spanStyles.map { Triple(it.item, s.subSequence(it.start, it.end).toString(), it.start) }
        assertTrue(spans.any { it.first.fontWeight == FontWeight.Bold && it.second == "жирный" })
        assertTrue(spans.any { it.first.fontStyle == FontStyle.Italic && it.second == "курсив" })
        assertTrue(spans.any { it.first.textDecoration == TextDecoration.Underline && it.second == "подчёркнутый" })
    }

    @Test
    fun `маркер списка заменяется на буллит`() {
        assertEquals("• пункт", parseInlineMarks("- пункт").text)
        assertEquals("минус в середине - остаётся", parseInlineMarks("минус в середине - остаётся").text)
    }

    @Test
    fun `текст без токенов не меняется`() {
        assertEquals("обычная строка 2 + 2 = 4", parseInlineMarks("обычная строка 2 + 2 = 4").text)
        assertEquals("", parseInlineMarks("").text)
    }

    @Test
    fun `незакрытый токен остаётся литералом`() {
        assertEquals("**текст", parseInlineMarks("**текст").text)
        assertEquals("звёздочка * одна", parseInlineMarks("звёздочка * одна").text)
        assertEquals("__подвал", parseInlineMarks("__подвал").text)
    }
}
