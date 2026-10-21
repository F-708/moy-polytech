package com.politex

import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.applyAppTheme
import com.politex.ui.theme.isDarkThemeActive
import com.politex.util.AppThemeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppThemeStoreDarkTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun reset() {
        AppThemeStore.setDark(context, false)
        applyAppTheme(AppTheme.COLLEGE, false)
    }

    @Test
    fun `тёмный режим по умолчанию выключен`() {
        assertFalse(AppThemeStore.isDark(context))
    }

    @Test
    fun `setDark сохраняет и возвращает значение`() {
        AppThemeStore.setDark(context, true)
        assertTrue(AppThemeStore.isDark(context))
        AppThemeStore.setDark(context, false)
        assertFalse(AppThemeStore.isDark(context))
    }

    @Test
    fun `setDark сразу переключает глобальную палитру`() {
        AppThemeStore.load(context)
        AppThemeStore.setDark(context, true)
        assertTrue(isDarkThemeActive)
        assertEquals(AppTheme.COLLEGE.darkPalette.bgMain, ColorBgMain)
        AppThemeStore.setDark(context, false)
        assertFalse(isDarkThemeActive)
        assertEquals(AppTheme.COLLEGE.palette.bgMain, ColorBgMain)
    }

    @Test
    fun `load подхватывает сохранённый тёмный режим`() {
        AppThemeStore.setDark(context, true)
        applyAppTheme(AppTheme.COLLEGE, false)
        AppThemeStore.load(context)
        assertTrue(isDarkThemeActive)
        assertEquals(AppTheme.COLLEGE.darkPalette.bgMain, ColorBgMain)
    }

    @Test
    fun `смена темы оформления не сбрасывает тёмный режим`() {
        AppThemeStore.setDark(context, true)
        applyAppTheme(AppTheme.STEEL)
        assertTrue(isDarkThemeActive)
        assertEquals(AppTheme.STEEL.darkPalette.bgMain, ColorBgMain)
    }

    @Test
    fun `сохранённая тема и тёмный режим независимы`() {
        AppThemeStore.setDark(context, true)
        assertEquals(AppTheme.COLLEGE, AppThemeStore.load(context))
        assertTrue(AppThemeStore.isDark(context))
    }
}
