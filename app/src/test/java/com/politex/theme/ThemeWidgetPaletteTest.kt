package com.politex.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.applyAppTheme
import com.politex.ui.theme.resolvePalette
import com.politex.util.AppThemeStore
import com.politex.widget.WidgetStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Виджет «Как в приложении» обязан брать эффективную палитру приложения
 * (пресет × режим × тон), а не светлую палитру пресета, как было раньше.
 *
 * Рендер RemoteViews в тесте не нужен — проверяется функция выбора палитры.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeWidgetPaletteTest {
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
    fun `APP берёт тёмную палитру приложения`() {
        AppThemeStore.saveTheme(context, AppTheme.BORDEAUX)
        AppThemeStore.saveMode(context, ThemeMode.DARK)

        val expected =
            resolvePalette(
                AppTheme.BORDEAUX,
                ThemeMode.DARK,
                SurfaceTone.NATIVE,
                systemDark = AppThemeStore.isSystemDark(context),
            )
        val colors = WidgetStyle.APP.colors(context)

        assertEquals(expected.brandFill.toArgb(), colors.backgroundColor)
        assertEquals(expected.onAccent.toArgb(), colors.mainColor)
        assertEquals(expected.onAccentMuted.toArgb(), colors.subColor)
        assertEquals(expected.topBar.toArgb(), colors.highlightColor)
        // Регрессия: раньше здесь всегда была светлая палитра пресета.
        assertNotEquals(AppTheme.BORDEAUX.palette.brandFill.toArgb(), colors.backgroundColor)
    }

    @Test
    fun `APP берёт светлую палитру при светлом режиме`() {
        AppThemeStore.saveTheme(context, AppTheme.EMERALD)
        AppThemeStore.saveMode(context, ThemeMode.LIGHT)

        val colors = WidgetStyle.APP.colors(context)
        assertEquals(AppTheme.EMERALD.palette.brandFill.toArgb(), colors.backgroundColor)
    }

    @Test
    fun `APP следует AMOLED-тону в части контраста текста`() {
        AppThemeStore.saveMode(context, ThemeMode.DARK)
        AppThemeStore.saveTone(context, SurfaceTone.DEEP)

        val tones = AppThemeStore.effectivePalette(context)
        val colors = WidgetStyle.APP.colors(context)

        assertEquals(androidx.compose.ui.graphics.Color.Black, tones.bgMain)
        // Заливка виджета — акцент пресета: тон её не меняет, но текст берётся из токенов.
        assertEquals(tones.brandFill.toArgb(), colors.backgroundColor)
        assertEquals(tones.onAccent.toArgb(), colors.mainColor)
    }

    @Test
    fun `SYSTEM следует системной теме, APP - настройке приложения`() {
        AppThemeStore.saveMode(context, ThemeMode.DARK)

        val app = WidgetStyle.APP.colors(context)
        val system = WidgetStyle.SYSTEM.colors(context)

        // В Robolectric системная тема — светлая: стили обязаны различаться.
        assertEquals(WidgetStyle.SYSTEM_LIGHT.backgroundColor, system.backgroundColor)
        assertNotEquals(system.backgroundColor, app.backgroundColor)
    }

    @Test
    @Config(qualifiers = "night")
    fun `APP в режиме SYSTEM в ночной конфигурации тёмный`() {
        // ключа app_dark нет → SYSTEM
        val colors = WidgetStyle.APP.colors(context)
        assertEquals(AppTheme.COLLEGE.darkPalette.brandFill.toArgb(), colors.backgroundColor)
        assertTrue(AppThemeStore.isDark(context))
    }

    @Test
    fun `по умолчанию виджет системный`() {
        assertEquals(WidgetStyle.SYSTEM, WidgetStyle.fromName(null))
        assertEquals(WidgetStyle.SYSTEM, WidgetStyle.fromName("нет такого"))
        assertEquals(WidgetStyle.APP, WidgetStyle.fromName("APP"))
    }
}