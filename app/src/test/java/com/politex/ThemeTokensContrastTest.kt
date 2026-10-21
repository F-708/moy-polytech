package com.politex

import androidx.compose.ui.graphics.Color
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.MpkPalette
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensContrastTest {
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun assertPair(
        label: String,
        fg: Color,
        bg: Color,
        min: Double,
    ) {
        val cr = contrast(fg, bg)
        assertTrue("$label: контраст ${"%.2f".format(cr)} < $min", cr >= min)
    }

    private fun assertPaletteContrast(theme: AppTheme, p: MpkPalette, dark: Boolean) {
        val t = "${theme.title} ${if (dark) "dark" else "light"}"
        assertPair("$t mapDeadText/mapDeadBg", p.mapDeadText, p.mapDeadBg, 4.5)
        assertPair("$t onAccentGold/accentGold", p.onAccentGold, p.accentGold, 4.5)
        assertPair("$t accentGoldBright/accentGoldBg", p.accentGoldBright, p.accentGoldBg, 4.5)
        assertPair("$t accentGoldSoft/accentGoldBg", p.accentGoldSoft, p.accentGoldBg, 4.5)
        assertPair("$t accentGoldMuted/accentGoldBg", p.accentGoldMuted, p.accentGoldBg, 4.5)
        assertPair("$t accentGold/bgMain", p.accentGold, p.bgMain, 4.5)
        assertPair("$t mapRoomBorder виден на фоне", p.mapRoomBorder, p.bgMain, 2.0)
        assertPair("$t mapDead виден на фоне", p.mapDead, p.bgMain, 2.0)
        assertPair("$t mapRouteCore/brandBlue", p.mapRouteCore, p.brandBlue, 2.0)
        assertPair("$t annotationDanger виден на фоне", p.annotationDanger, p.bgMain, 2.0)
        assertPair("$t navSelected/surfaceHighlight", p.navSelected, p.surfaceHighlight, 4.5)
    }

    @Test
    fun `новые токены карты и admin проходят WCAG во всех темах и режимах`() {
        AppTheme.entries.forEach { theme ->
            assertPaletteContrast(theme, theme.palette, dark = false)
            assertPaletteContrast(theme, theme.darkPalette, dark = true)
        }
    }

    @Test
    fun `тёмные варианты токенов светлее светлых`() {
        AppTheme.entries.forEach { theme ->
            assertTrue(
                "${theme.title}: accentGold в тёмной теме обязан быть светлее",
                luminance(theme.darkPalette.accentGold) > luminance(theme.palette.accentGold),
            )
            assertTrue(
                "${theme.title}: mapRoomBorder в тёмной теме обязан быть светлее",
                luminance(theme.darkPalette.mapRoomBorder) > luminance(theme.palette.mapRoomBorder),
            )
            assertTrue(
                "${theme.title}: mapDead в тёмной теме обязан быть светлее",
                luminance(theme.darkPalette.mapDead) > luminance(theme.palette.mapDead),
            )
            assertTrue(
                "${theme.title}: annotationDanger в тёмной теме обязан быть светлее",
                luminance(theme.darkPalette.annotationDanger) > luminance(theme.palette.annotationDanger),
            )
        }
    }
}
