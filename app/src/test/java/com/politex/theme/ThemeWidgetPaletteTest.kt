package com.politex.theme

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.applyAppTheme
import com.politex.widget.WidgetStyle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Палитра виджета = палитра выбранной темы приложения (заказчик 2026-11-13):
 * 8 слотов, светлая/тёмная палитра темы — по ночному режиму системы.
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
    fun `каждая тема виджета даёт свою палитру приложения`() {
        AppTheme.entries.forEach { theme ->
            val style = WidgetStyle.valueOf(theme.name)
            val colors = style.colors(context)
            assertEquals(
                "${theme.name}: фон виджета = brandFill светлой палитры темы",
                theme.palette.brandFill.toArgb(),
                colors.backgroundColor,
            )
            assertEquals(theme.title, style.title)
        }
    }

    @Test
    @Config(qualifiers = "night")
    fun `в ночной конфигурации тема виджета берёт тёмную палитру`() {
        AppTheme.entries.forEach { theme ->
            assertEquals(
                "${theme.name}: фон виджета = brandFill тёмной палитры темы",
                theme.darkPalette.brandFill.toArgb(),
                WidgetStyle.valueOf(theme.name).colors(context).backgroundColor,
            )
        }
    }

    @Test
    fun `текст читаем на фоне во всех темах и обеих конфигурациях`() {
        AppTheme.entries.forEach { theme ->
            val colors = WidgetStyle.valueOf(theme.name).colors(context)
            assertTrue(
                "${theme.name}: mainColor сливается с фоном",
                contrastRatio(colors.mainColor, colors.backgroundColor) >= 4.5,
            )
            assertTrue(
                "${theme.name}: subColor сливается с фоном",
                contrastRatio(colors.subColor, colors.backgroundColor) >= 4.0,
            )
            assertTrue(
                "${theme.name}: текст чипа сливается с подсветкой",
                contrastRatio(colors.mainColor, colors.highlightColor) >= 4.5,
            )
            assertTrue(
                "${theme.name}: pastColor совсем не виден на фоне",
                contrastRatio(colors.pastColor, colors.backgroundColor) >= 3.0,
            )
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
