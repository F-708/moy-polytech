package com.politex.theme

import androidx.compose.ui.graphics.Color
import com.politex.ui.theme.AA_LARGE_CONTRAST
import com.politex.ui.theme.AA_TEXT_CONTRAST
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.resolvePalette
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Гейт контраста: 8 пресетов × 2 (светлая/тёмная) × 3 тона = 48 наборов × 15 пар.
 *
 * Пары взяты из реального рендера: навигация рисует иконку в «пилюле» (surfaceHighlight)
 * и подпись на фоне бара (bgMain), поэтому `navSelected` проверяется против обоих фонов.
 */
class ThemeContrastTest {
    private data class Pair(
        val label: String,
        val fg: (Palette) -> Color,
        val bg: (Palette) -> Color,
        val min: Double,
    )

    /** Локальный снимок полей палитры, чтобы тест не зависел от internal-видимости. */
    private data class Palette(
        val bgMain: Color,
        val surfaceVariant: Color,
        val surfaceHighlight: Color,
        val topBar: Color,
        val menuBg: Color,
        val menuText: Color,
        val menuSubtext: Color,
        val textTitle: Color,
        val textBody: Color,
        val textMuted: Color,
        val textWeekdays: Color,
        val brandFill: Color,
        val navSelected: Color,
        val successText: Color,
        val success: Color,
        val successBg: Color,
        val errorText: Color,
        val mapDeadText: Color,
        val mapDeadBg: Color,
        val accentGold: Color,
        val annotationDanger: Color,
        val onAccent: Color,
        val brandBlue: Color,
        val activeBlue: Color,
        val onLightAccent: Color,
        val onSuccess: Color,
        val onAccentMuted: Color,
    )

    private fun snapshot(theme: AppTheme, mode: ThemeMode, tone: SurfaceTone, systemDark: Boolean): Palette {
        val p = resolvePalette(theme, mode, tone, systemDark)
        return Palette(
            bgMain = p.bgMain,
            surfaceVariant = p.surfaceVariant,
            surfaceHighlight = p.surfaceHighlight,
            topBar = p.topBar,
            menuBg = p.menuBg,
            menuText = p.menuText,
            menuSubtext = p.menuSubtext,
            textTitle = p.textTitle,
            textBody = p.textBody,
            textMuted = p.textMuted,
            textWeekdays = p.textWeekdays,
            brandFill = p.brandFill,
            navSelected = p.navSelected,
            successText = p.successText,
            success = p.success,
            successBg = p.successBg,
            errorText = p.errorText,
            mapDeadText = p.mapDeadText,
            mapDeadBg = p.mapDeadBg,
            accentGold = p.accentGold,
            annotationDanger = p.annotationDanger,
            onAccent = p.onAccent,
            brandBlue = p.brandBlue,
            activeBlue = p.activeBlue,
            onLightAccent = p.onLightAccent,
            onSuccess = p.onSuccess,
            onAccentMuted = p.onAccentMuted,
        )
    }

    private val pairs =
        listOf(
            Pair("textTitle/bgMain", { it.textTitle }, { it.bgMain }, AA_TEXT_CONTRAST),
            Pair("textBody/bgMain", { it.textBody }, { it.bgMain }, AA_TEXT_CONTRAST),
            Pair("textMuted/bgMain", { it.textMuted }, { it.bgMain }, AA_TEXT_CONTRAST),
            Pair("textWeekdays/bgMain", { it.textWeekdays }, { it.bgMain }, AA_TEXT_CONTRAST),
            Pair("textMuted/surfaceVariant", { it.textMuted }, { it.surfaceVariant }, AA_TEXT_CONTRAST),
            Pair("menuText/menuBg", { it.menuText }, { it.menuBg }, AA_TEXT_CONTRAST),
            Pair("menuSubtext/menuBg", { it.menuSubtext }, { it.menuBg }, AA_TEXT_CONTRAST),
            // Навигация: иконка в пилюле и подпись на фоне нижнего бара.
            Pair("navSelected/surfaceHighlight", { it.navSelected }, { it.surfaceHighlight }, AA_TEXT_CONTRAST),
            Pair("navSelected/bgMain", { it.navSelected }, { it.bgMain }, AA_TEXT_CONTRAST),
            Pair("onAccent/brandFill", { it.onAccent }, { it.brandFill }, AA_TEXT_CONTRAST),
            Pair("onLightAccent/brandBlue", { it.onLightAccent }, { it.brandBlue }, AA_TEXT_CONTRAST),
            Pair("onLightAccent/activeBlue", { it.onLightAccent }, { it.activeBlue }, AA_TEXT_CONTRAST),
            Pair("onAccentMuted/brandFill", { it.onAccentMuted }, { it.brandFill }, AA_TEXT_CONTRAST),
            Pair("onSuccess/success", { it.onSuccess }, { it.success }, AA_TEXT_CONTRAST),
            Pair("successText/successBg", { it.successText }, { it.successBg }, AA_TEXT_CONTRAST),
            Pair("errorText/bgMain", { it.errorText }, { it.bgMain }, AA_TEXT_CONTRAST),
            Pair("mapDeadText/mapDeadBg", { it.mapDeadText }, { it.mapDeadBg }, AA_TEXT_CONTRAST),
            Pair("accentGold/bgMain", { it.accentGold }, { it.bgMain }, AA_TEXT_CONTRAST),
            // Графический элемент (иконка), порог AA для крупного/нетекстового — 3:1
            Pair("annotationDanger/bgMain", { it.annotationDanger }, { it.bgMain }, AA_LARGE_CONTRAST),
        )

    @Test
    fun `48 наборов проходят WCAG AA по всем парам`() {
        val failures = mutableListOf<String>()
        var checked = 0
        AppTheme.entries.forEach { theme ->
            listOf(false, true).forEach { dark ->
                val mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
                SurfaceTone.entries.forEach { tone ->
                    val palette = snapshot(theme, mode, tone, systemDark = false)
                    pairs.forEach { pair ->
                        val cr = contrast(pair.fg(palette), pair.bg(palette))
                        checked++
                        if (cr < pair.min - 1e-9) {
                            failures +=
                                "${theme.title} ${if (dark) "dark" else "light"} ${tone.id} " +
                                "${pair.label}: ${"%.2f".format(cr)} < ${pair.min}"
                        }
                    }
                }
            }
        }
        assertTrue(
            "Нарушений контраста: ${failures.size} из $checked\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun `навигация в тёмной теме различима на обоих фонах`() {
        // Регрессия из UX-ревью: выбранная вкладка не проходила AA ни в одной из 8 тёмных тем.
        AppTheme.entries.forEach { theme ->
            val p = resolvePalette(theme, ThemeMode.DARK, SurfaceTone.NATIVE, systemDark = false)
            val onPill = contrast(p.navSelected, p.surfaceHighlight)
            val onBar = contrast(p.navSelected, p.bgMain)
            assertTrue("${theme.title}: navSelected на пилюле ${"%.2f".format(onPill)} < 4.5", onPill >= AA_TEXT_CONTRAST)
            assertTrue("${theme.title}: navSelected на фоне бара ${"%.2f".format(onBar)} < 4.5", onBar >= AA_TEXT_CONTRAST)
            assertTrue(
                "${theme.title}: светлая навигация должна быть светлее фона бара",
                luminance(p.navSelected) > luminance(p.bgMain) + 0.15,
            )
        }
    }

    @Test
    fun `AMOLED-фон не теряет границы`() {
        // На чистом чёрном разница заливок 1.07 — разделять поверхности обязаны границы.
        AppTheme.entries.forEach { theme ->
            val p = resolvePalette(theme, ThemeMode.DARK, SurfaceTone.DEEP, systemDark = false)
            val border = contrast(p.borderLight, p.bgMain)
            assertTrue(
                "${theme.title}: DEEP-тёмная, граница к фону ${"%.2f".format(border)} < 3.0",
                border >= AA_LARGE_CONTRAST,
            )
        }
    }

    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}