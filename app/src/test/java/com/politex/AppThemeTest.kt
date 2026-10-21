package com.politex

import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.MpkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {
    @Test
    fun `выбор пользователя читается по id`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(theme, AppTheme.from(theme.id))
        }
    }

    @Test
    fun `неизвестный или пустой id откатывается на фирменную`() {
        assertEquals(AppTheme.COLLEGE, AppTheme.from(null))
        assertEquals(AppTheme.COLLEGE, AppTheme.from(""))
        assertEquals(AppTheme.COLLEGE, AppTheme.from("no_such_theme"))
    }

    @Test
    fun `id тем уникальны`() {
        val ids = AppTheme.entries.map { it.id }
        assertEquals("id не должны повторяться", ids.size, ids.toSet().size)
    }

    @Test
    fun `все темы дают отличающиеся палитры`() {
        val added = listOf(AppTheme.INDIGO, AppTheme.AMBER, AppTheme.STEEL)
        added.forEach { assertTrue("${it.title} должна быть в списке", it in AppTheme.entries) }

        AppTheme.entries.filter { it != AppTheme.COLLEGE }.forEach { theme ->
            assertNotEquals(
                "${theme.title}: шапка должна отличаться от фирменной",
                AppTheme.COLLEGE.palette.topBar,
                theme.palette.topBar,
            )
            assertNotEquals(
                "${theme.title}: акцент должен отличаться от фирменной",
                AppTheme.COLLEGE.palette.brandBlue,
                theme.palette.brandBlue,
            )
        }
    }

    @Test
    fun `заливка кнопок достаточно тёмная для белого текста`() {
        AppTheme.entries.forEach { theme ->
            val fill = theme.palette.brandFill
            val luminance = 0.299 * fill.red + 0.587 * fill.green + 0.114 * fill.blue
            assertTrue(
                "${theme.title}: brandFill слишком светлый для белого текста (luma=$luminance)",
                luminance < 0.6f,
            )
        }
    }

    @Test
    fun `темы не ссылаются на одну палитру`() {
        val palettes: List<MpkPalette> = AppTheme.entries.map { it.palette }
        assertEquals(palettes.size, palettes.toSet().size)
    }

    @Test
    fun `смена темы сразу меняет глобальные цвета`() {
        AppTheme.entries.forEach { theme ->
            com.politex.ui.theme.applyAppTheme(theme)
            assertEquals(
                "${theme.title}: ColorBrandFill обязан следовать за applyAppTheme без перекомпоновки",
                theme.palette.brandFill,
                com.politex.ui.theme.ColorBrandFill,
            )
            assertEquals(
                "${theme.title}: ColorTopBar обязан следовать за applyAppTheme",
                theme.palette.topBar,
                com.politex.ui.theme.ColorTopBar,
            )
        }
        com.politex.ui.theme.applyAppTheme(AppTheme.COLLEGE)
    }
}
