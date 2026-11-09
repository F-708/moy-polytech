package com.politex.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.AA_TEXT_CONTRAST
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.AppearanceConfig
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.applyAppTheme
import com.politex.ui.theme.resolvePalette
import com.politex.util.AppThemeStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Миграция настроек: `app_dark` (boolean) → `app_mode` (id режима).
 *
 * Главное правило: у кого ключ `app_dark` есть — выбор сохраняется;
 * у кого его нет (никогда не трогал тёмную) — включается «Системная».
 */
@RunWith(RobolectricTestRunner::class)
class AppThemeStoreMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("mpk_ui_prefs", Context.MODE_PRIVATE)

    @Before
    fun clean() {
        prefs.edit().clear().commit()
    }

    @After
    fun reset() {
        prefs.edit().clear().commit()
        applyAppTheme(AppTheme.COLLEGE, false)
    }

    @Test
    fun `нет app_dark - режим Системная`() {
        assertEquals(ThemeMode.SYSTEM, AppThemeStore.readPrefs(context).mode)
    }

    @Test
    fun `app_dark true - режим Тёмная`() {
        prefs.edit().putBoolean("app_dark", true).commit()
        assertEquals(ThemeMode.DARK, AppThemeStore.readPrefs(context).mode)
    }

    @Test
    fun `app_dark false - режим Светлая`() {
        prefs.edit().putBoolean("app_dark", false).commit()
        assertEquals(ThemeMode.LIGHT, AppThemeStore.readPrefs(context).mode)
    }

    @Test
    fun `app_mode важнее легаси app_dark`() {
        prefs
            .edit()
            .putString("app_mode", ThemeMode.LIGHT.id)
            .putBoolean("app_dark", true)
            .commit()
        assertEquals(ThemeMode.LIGHT, AppThemeStore.readPrefs(context).mode)
    }

    @Test
    fun `тон и тема по умолчанию не меняют привычный вид`() {
        val config = AppThemeStore.readPrefs(context)
        assertEquals(SurfaceTone.NATIVE, config.tone)
        assertEquals(AppTheme.COLLEGE, config.theme)
    }

    @Test
    fun `сохранение и чтение режима и тона`() {
        AppThemeStore.saveMode(context, ThemeMode.DARK)
        AppThemeStore.saveTone(context, SurfaceTone.DEEP)
        AppThemeStore.saveTheme(context, AppTheme.PLUM)

        assertEquals(ThemeMode.DARK, AppThemeStore.loadMode(context))
        assertEquals(SurfaceTone.DEEP, AppThemeStore.loadTone(context))
        assertEquals(AppTheme.PLUM, AppThemeStore.readPrefs(context).theme)
        assertTrue(AppThemeStore.isDark(context))
    }

    @Test
    fun `saveMode пишет легаси-зеркало app_dark`() {
        AppThemeStore.saveMode(context, ThemeMode.DARK)
        assertTrue(prefs.getBoolean("app_dark", false))
        AppThemeStore.saveMode(context, ThemeMode.LIGHT)
        assertFalse(prefs.getBoolean("app_dark", true))
    }

    @Test
    fun `setDark это шим над режимом и не сбрасывает тон`() {
        AppThemeStore.saveTone(context, SurfaceTone.SOFT)
        AppThemeStore.setDark(context, true)
        assertEquals(ThemeMode.DARK, AppThemeStore.loadMode(context))
        assertEquals(SurfaceTone.SOFT, AppThemeStore.loadTone(context))
        assertTrue(prefs.getBoolean("app_dark", false))

        AppThemeStore.setDark(context, false)
        assertEquals(ThemeMode.LIGHT, AppThemeStore.loadMode(context))
        assertEquals(SurfaceTone.SOFT, AppThemeStore.loadTone(context))
    }

    @Test
    fun `смена пресета не трогает режим`() {
        AppThemeStore.saveMode(context, ThemeMode.DARK)
        AppThemeStore.saveTheme(context, AppTheme.BORDEAUX)
        assertEquals(ThemeMode.DARK, AppThemeStore.loadMode(context))
        assertEquals(AppTheme.BORDEAUX, AppThemeStore.readPrefs(context).theme)
    }

    @Test
    fun `saveAll сохраняет всё и зеркалит app_dark`() {
        AppThemeStore.saveAll(
            context,
            AppearanceConfig(
                theme = AppTheme.INDIGO,
                mode = ThemeMode.DARK,
                tone = SurfaceTone.SOFT,
            ),
        )
        val config = AppThemeStore.readPrefs(context)
        assertEquals(AppTheme.INDIGO, config.theme)
        assertEquals(ThemeMode.DARK, config.mode)
        assertEquals(SurfaceTone.SOFT, config.tone)
        assertTrue(prefs.getBoolean("app_dark", false))
    }

    @Test
    fun `эффективная палитра тёмная даже если система светлая`() {
        AppThemeStore.saveTheme(context, AppTheme.EMERALD)
        AppThemeStore.saveMode(context, ThemeMode.DARK)

        val expected =
            resolvePalette(
                AppTheme.EMERALD,
                ThemeMode.DARK,
                SurfaceTone.NATIVE,
                systemDark = AppThemeStore.isSystemDark(context),
            )
        assertEquals(expected, AppThemeStore.effectivePalette(context))
        assertEquals(AppTheme.EMERALD.darkPalette, AppThemeStore.effectivePalette(context))
        assertNotEquals(AppTheme.EMERALD.palette, AppThemeStore.effectivePalette(context))
    }

    @Test
    fun `чтение настроек не меняет глобальную палитру`() {
        // Регрессия: AppThemeStore.load() вызывался из композиции и фоновых потоков виджетов
        // и попутно переписывал глобальное состояние.
        prefs
            .edit()
            .putString("app_theme", AppTheme.AMBER.id)
            .putString("app_tone", SurfaceTone.SOFT.id)
            .commit()
        applyAppTheme(AppTheme.COLLEGE, false)
        val appliedBg = com.politex.ui.theme.ColorBgMain

        val palette = AppThemeStore.effectivePalette(context)

        assertEquals("чтение не должно применяться к глобальному состоянию", appliedBg, com.politex.ui.theme.ColorBgMain)
        assertNotEquals("эффективная палитра отличается от применённой", appliedBg, palette.bgMain)
    }

    @Test
    @Config(qualifiers = "night")
    fun `в ночной конфигурации SYSTEM даёт тёмную палитру`() {
        assertTrue(AppThemeStore.isSystemDark(context))
        // ключа app_dark нет → SYSTEM
        assertEquals(ThemeMode.SYSTEM, AppThemeStore.readPrefs(context).mode)
        assertTrue(AppThemeStore.isDark(context))
        assertEquals(AppTheme.COLLEGE.darkPalette, AppThemeStore.effectivePalette(context))
    }

    @Test
    @Config(qualifiers = "night")
    fun `ручная светлая тема важнее ночной системной`() {
        AppThemeStore.saveMode(context, ThemeMode.LIGHT)
        assertFalse(AppThemeStore.isDark(context))
        assertEquals(AppTheme.COLLEGE.palette, AppThemeStore.effectivePalette(context))
    }

    @Test
    fun `порог AA остался прежним`() {
        assertEquals(4.5, AA_TEXT_CONTRAST, 0.0)
    }
}