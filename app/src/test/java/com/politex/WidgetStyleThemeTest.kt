package com.politex

import com.politex.ui.theme.AppTheme
import com.politex.widget.WidgetStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Темы виджетов = темы приложения (заказчик 2026-11-13): ровно 8 слотов,
 * без «системной» и «как в приложении».
 */
class WidgetStyleThemeTest {
    @Test
    fun `у виджетов ровно восемь тем приложения`() {
        assertEquals(AppTheme.entries.map { it.name }, WidgetStyle.entries.map { it.name })
        AppTheme.entries.forEach { theme ->
            assertEquals("тема виджета называется как тема приложения", theme.title, WidgetStyle.valueOf(theme.name).title)
        }
    }

    @Test
    fun `старые и неизвестные имена стилей падают в фирменную`() {
        listOf("SYSTEM", "APP", "LIGHT", "DARK", "BLUE", "SKY", "", null).forEach { oldName ->
            assertEquals(
                "старое имя '$oldName' должно падать в фирменную",
                WidgetStyle.COLLEGE,
                WidgetStyle.fromName(oldName),
            )
        }
        assertEquals(WidgetStyle.PLUM, WidgetStyle.fromName("PLUM"))
    }

    @Test
    fun `палитры тем виджета непрозрачны`() {
        AppTheme.entries.forEach { theme ->
            listOf(theme.palette, theme.darkPalette).forEach { p ->
                listOf(p.brandFill, p.onAccent, p.onAccentMuted, p.topBar).forEach { color ->
                    assertEquals(
                        "${theme.name}: цвет должен быть непрозрачным",
                        0xFF,
                        (color.value shr 56).toInt() and 0xFF,
                    )
                }
            }
        }
    }

    @Test
    fun `тем виджета восемь и они различимы по титулам`() {
        assertEquals(8, WidgetStyle.entries.size)
        assertEquals(8, WidgetStyle.entries.map { it.title }.toSet().size)
        assertTrue(WidgetStyle.entries.first().title == "Фирменная")
        assertTrue(WidgetStyle.entries.last().title == "Сливовая")
    }
}
