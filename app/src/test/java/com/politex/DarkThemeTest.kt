package com.politex

import androidx.compose.ui.graphics.Color
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.DarkPalette
import com.politex.ui.theme.MpkPalette
import com.politex.ui.theme.applyAppTheme
import com.politex.ui.theme.applyDarkMode
import com.politex.ui.theme.isDarkThemeActive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkThemeTest {
    @After
    fun reset() {
        applyAppTheme(AppTheme.COLLEGE, false)
    }

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

    private fun assertDarkPaletteContrast(theme: AppTheme, p: MpkPalette) {
        val t = theme.title
        assertPair("$t textTitle/bgMain", p.textTitle, p.bgMain, 4.5)
        assertPair("$t textBody/bgMain", p.textBody, p.bgMain, 4.5)
        assertPair("$t textMuted/bgMain", p.textMuted, p.bgMain, 4.5)
        assertPair("$t textMuted/surfaceVariant", p.textMuted, p.surfaceVariant, 4.5)
        assertPair("$t textMuted/surfaceHighlight", p.textMuted, p.surfaceHighlight, 4.5)
        assertPair("$t textTitle/surfaceVariant", p.textTitle, p.surfaceVariant, 4.5)
        assertPair("$t textBody/surfaceHighlight", p.textBody, p.surfaceHighlight, 4.5)
        assertPair("$t textWeekdays/bgMain", p.textWeekdays, p.bgMain, 4.5)
        assertPair("$t brandBlue/bgMain", p.brandBlue, p.bgMain, 4.5)
        assertPair("$t activeBlue/bgMain", p.activeBlue, p.bgMain, 4.5)
        assertPair("$t белый/brandFill", Color.White, p.brandFill, 4.5)
        assertPair("$t белый/activeFill", Color.White, p.activeFill, 4.5)
        assertPair("$t белый/topBar", Color.White, p.topBar, 4.5)
        assertPair("$t белый/dangerFill", Color.White, p.dangerFill, 4.5)
        assertPair("$t brandFill видна на bgMain", p.brandFill, p.bgMain, 2.0)
        assertPair("$t activeFill видна на bgMain", p.activeFill, p.bgMain, 2.0)
        assertPair("$t topBar отличен от bgMain", p.topBar, p.bgMain, 1.3)
        assertPair("$t menuText/menuBg", p.menuText, p.menuBg, 4.5)
        assertPair("$t menuSubtext/menuSubBg", p.menuSubtext, p.menuSubBg, 4.5)
        assertPair("$t menuIcon/menuBg", p.menuIcon, p.menuBg, 4.5)
        assertPair("$t successText/successBg", p.successText, p.successBg, 4.5)
        assertPair("$t success/bgMain", p.success, p.bgMain, 4.5)
        assertPair("$t warning/bgMain", p.warning, p.bgMain, 4.5)
        assertPair("$t error/bgMain", p.error, p.bgMain, 4.5)
        assertPair("$t errorText/bgMain", p.errorText, p.bgMain, 4.5)
        assertPair("$t badgeHW/bgMain", p.badgeHW, p.bgMain, 4.5)
        assertPair("$t badgeLab/bgMain", p.badgeLab, p.bgMain, 4.5)
        assertPair("$t badgePract/bgMain", p.badgePract, p.bgMain, 4.5)
        assertPair("$t badgeExam/bgMain", p.badgeExam, p.bgMain, 4.5)
        assertPair("$t badgeCourse/bgMain", p.badgeCourse, p.bgMain, 4.5)
        assertPair("$t badgeDiploma/bgMain", p.badgeDiploma, p.bgMain, 4.5)
    }

    @Test
    fun `тёмные палитры всех тем проходят WCAG AA`() {
        AppTheme.entries.forEach { theme -> assertDarkPaletteContrast(theme, theme.darkPalette) }
    }

    @Test
    fun `у каждой темы своя тёмная палитра`() {
        val palettes: List<MpkPalette> = AppTheme.entries.map { it.darkPalette }
        assertEquals(palettes.size, palettes.toSet().size)
    }

    @Test
    fun `тёмная палитра отличается от светлой той же темы`() {
        AppTheme.entries.forEach { theme ->
            assertNotEquals("${theme.title}: фон обязан стать тёмным", theme.palette.bgMain, theme.darkPalette.bgMain)
            assertNotEquals("${theme.title}: текст обязан стать светлым", theme.palette.textTitle, theme.darkPalette.textTitle)
        }
    }

    @Test
    fun `фирменная тёмная палитра — это DarkPalette`() {
        assertEquals(DarkPalette, AppTheme.COLLEGE.darkPalette)
    }

    @Test
    fun `applyAppTheme с dark переключает глобальные цвета`() {
        AppTheme.entries.forEach { theme ->
            applyAppTheme(theme, true)
            assertTrue(isDarkThemeActive)
            assertEquals("${theme.title}: тёмный фон", theme.darkPalette.bgMain, com.politex.ui.theme.ColorBgMain)
            assertEquals("${theme.title}: тёмный акцент", theme.darkPalette.brandFill, com.politex.ui.theme.ColorBrandFill)
            applyAppTheme(theme, false)
            assertFalse(isDarkThemeActive)
            assertEquals("${theme.title}: светлый фон", theme.palette.bgMain, com.politex.ui.theme.ColorBgMain)
        }
    }

    @Test
    fun `applyDarkMode переключает режим без смены выбранной темы`() {
        applyAppTheme(AppTheme.EMERALD, false)
        applyDarkMode(true)
        assertTrue(isDarkThemeActive)
        assertEquals(AppTheme.EMERALD.darkPalette.bgMain, com.politex.ui.theme.ColorBgMain)
        assertEquals(AppTheme.EMERALD.darkPalette.textTitle, com.politex.ui.theme.ColorTextTitle)
        applyDarkMode(false)
        assertFalse(isDarkThemeActive)
        assertEquals(AppTheme.EMERALD.palette.bgMain, com.politex.ui.theme.ColorBgMain)
    }

    @Test
    fun `светлая тема по умолчанию`() {
        applyAppTheme(AppTheme.COLLEGE)
        assertFalse(isDarkThemeActive)
        assertEquals(AppTheme.COLLEGE.palette.bgMain, com.politex.ui.theme.ColorBgMain)
    }
}
