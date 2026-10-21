package com.politex

import com.politex.widget.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStyleThemeTest {
    @Test
    fun `JJ6 у виджетов остались только системная тема и как в приложении`() {
        assertEquals(listOf(WidgetStyle.SYSTEM, WidgetStyle.APP), WidgetStyle.entries)
        assertEquals("Системная", WidgetStyle.SYSTEM.title)
        assertEquals("Как в приложении", WidgetStyle.APP.title)
    }

    @Test
    fun `JJ6 fromName мягко мапит удалённые стили в системную`() {
        listOf("LIGHT", "DARK", "BLUE", "SKY").forEach { removedName ->
            assertEquals(
                "старый стиль '$removedName' должен падать в системный",
                WidgetStyle.SYSTEM,
                WidgetStyle.fromName(removedName),
            )
        }
        assertEquals(WidgetStyle.APP, WidgetStyle.fromName("APP"))
        assertEquals(WidgetStyle.SYSTEM, WidgetStyle.fromName(null))
        assertEquals(WidgetStyle.SYSTEM, WidgetStyle.fromName(""))
    }

    @Test
    fun `JJ6 системная палитра переключается по ночному режиму`() {
        assertEquals(WidgetStyle.SYSTEM_LIGHT, WidgetStyle.systemColors(night = false))
        assertEquals(WidgetStyle.SYSTEM_DARK, WidgetStyle.systemColors(night = true))
    }

    @Test
    fun `JJ6 N7-W27 основной текст читаем на фоне в обеих системных палитрах`() {
        listOf(false, true).forEach { night ->
            val c = WidgetStyle.systemColors(night)
            assertTrue(
                "night=$night: mainColor сливается с фоном",
                contrastRatio(c.mainColor, c.backgroundColor) >= 4.5,
            )
            assertTrue(
                "night=$night: subColor сливается с фоном",
                contrastRatio(c.subColor, c.backgroundColor) >= 4.0,
            )
            assertTrue(
                "night=$night: текст чипа сливается с подсветкой",
                contrastRatio(c.mainColor, c.highlightColor) >= 4.5,
            )
            assertTrue(
                "night=$night: pastColor совсем не виден на фоне",
                contrastRatio(c.pastColor, c.backgroundColor) >= 3.0,
            )
        }
    }

    @Test
    fun `JJ6 палитры виджета непрозрачны`() {
        listOf(WidgetStyle.SYSTEM_LIGHT, WidgetStyle.SYSTEM_DARK).forEach { c ->
            listOf(c.backgroundColor, c.mainColor, c.subColor, c.highlightColor, c.pastColor).forEach { color ->
                assertEquals("цвет должен быть непрозрачным", 0xFF, (color ushr 24) and 0xFF)
            }
        }
    }

    private fun contrastRatio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val r = channel((color shr 16) and 0xFF)
        val g = channel((color shr 8) and 0xFF)
        val b = channel(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
