package com.politex

import androidx.compose.ui.graphics.Color
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.MpkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlumThemeTest {
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

    @Test
    fun `тем ровно восемь и id уникальны`() {
        assertEquals(8, AppTheme.entries.size)
        val ids = AppTheme.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `сливовая тема присутствует со стабильным wire-id`() {
        assertEquals("plum", AppTheme.PLUM.id)
        assertEquals("Сливовая", AppTheme.PLUM.title)
        assertEquals(AppTheme.PLUM, AppTheme.from("plum"))
        assertEquals(AppTheme.COLLEGE, AppTheme.from("plum_typo"))
    }

    @Test
    fun `сливовая отличается от бордовой и индиго по акцентам`() {
        assertTrue(AppTheme.PLUM.palette.brandBlue != AppTheme.BORDEAUX.palette.brandBlue)
        assertTrue(AppTheme.PLUM.palette.brandBlue != AppTheme.INDIGO.palette.brandBlue)
        assertTrue(AppTheme.PLUM.darkPalette.brandFill != AppTheme.BORDEAUX.darkPalette.brandFill)
        assertTrue(AppTheme.PLUM.darkPalette.brandFill != AppTheme.INDIGO.darkPalette.brandFill)
    }

    @Test
    fun `светлая сливовая палитра проходит WCAG AA`() {
        val p = AppTheme.PLUM.palette
        assertPair("textTitle/bgMain", p.textTitle, p.bgMain, 4.5)
        assertPair("textBody/bgMain", p.textBody, p.bgMain, 4.5)
        assertPair("textMuted/surfaceVariant", p.textMuted, p.surfaceVariant, 4.5)
        assertPair("brandBlue/bgMain", p.brandBlue, p.bgMain, 4.5)
        assertPair("activeBlue/bgMain", p.activeBlue, p.bgMain, 4.5)
        assertPair("белый/topBar", Color.White, p.topBar, 4.5)
        assertPair("белый/brandFill", Color.White, p.brandFill, 4.5)
        assertPair("белый/activeFill", Color.White, p.activeFill, 4.5)
        assertPair("brandFill видна на bgMain", p.brandFill, p.bgMain, 2.0)
        assertPair("activeFill видна на bgMain", p.activeFill, p.bgMain, 2.0)
        assertPair("navSelected/surfaceHighlight", p.navSelected, p.surfaceHighlight, 4.5)
        assertPair("mapRoomBorder/bgMain", p.mapRoomBorder, p.bgMain, 2.0)
        assertPair("mapRouteCore/brandBlue", p.mapRouteCore, p.brandBlue, 2.0)
        assertPair("badgeDiploma/bgMain", p.badgeDiploma, p.bgMain, 4.5)
    }

    @Test
    fun `тёмная сливовая палитра проходит WCAG AA`() {
        val p = AppTheme.PLUM.darkPalette
        assertPair("textTitle/bgMain", p.textTitle, p.bgMain, 4.5)
        assertPair("textBody/bgMain", p.textBody, p.bgMain, 4.5)
        assertPair("textMuted/surfaceVariant", p.textMuted, p.surfaceVariant, 4.5)
        assertPair("textMuted/surfaceHighlight", p.textMuted, p.surfaceHighlight, 4.5)
        assertPair("brandBlue/bgMain", p.brandBlue, p.bgMain, 4.5)
        assertPair("activeBlue/bgMain", p.activeBlue, p.bgMain, 4.5)
        assertPair("белый/topBar", Color.White, p.topBar, 4.5)
        assertPair("белый/brandFill", Color.White, p.brandFill, 4.5)
        assertPair("белый/activeFill", Color.White, p.activeFill, 4.5)
        assertPair("brandFill видна на bgMain", p.brandFill, p.bgMain, 2.0)
        assertPair("activeFill видна на bgMain", p.activeFill, p.bgMain, 2.0)
        assertPair("topBar отличен от bgMain", p.topBar, p.bgMain, 1.3)
        assertPair("navSelected/surfaceHighlight", p.navSelected, p.surfaceHighlight, 4.5)
        assertPair("mapRoomBorder/bgMain", p.mapRoomBorder, p.bgMain, 2.0)
        assertPair("mapRouteCore/brandBlue", p.mapRouteCore, p.brandBlue, 2.0)
        assertPair("badgeDiploma/bgMain", p.badgeDiploma, p.bgMain, 4.5)
    }

    @Test
    fun `виджет в стиле APP со сливовой темой читается`() {
        val p: MpkPalette = AppTheme.PLUM.palette
        assertPair("main белый/brandFill", Color.White, p.brandFill, 4.5)
        assertPair("sub D6E2F0/brandFill", Color(0xFFD6E2F0), p.brandFill, 4.0)
        assertPair("past 9FB0C4/brandFill", Color(0xFF9FB0C4), p.brandFill, 3.0)
        assertPair("highlight topBar отличим от brandFill", p.topBar, p.brandFill, 1.3)
    }

    @Test
    fun `тёмная сливовая палитра отличается от светлой`() {
        val light = AppTheme.PLUM.palette
        val dark = AppTheme.PLUM.darkPalette
        assertTrue(light.topBar != dark.topBar)
        assertTrue(light.brandBlue != dark.brandBlue)
        assertTrue(light.brandFill != dark.brandFill)
        assertTrue(luminance(dark.brandBlue) > luminance(light.brandBlue))
        assertTrue(luminance(dark.mapRoomBorder) > luminance(light.mapRoomBorder))
    }
}
