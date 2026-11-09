package com.politex.theme

import androidx.compose.ui.graphics.Color
import com.politex.ui.theme.AA_TEXT_CONTRAST
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.MpkPalette
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.effectiveDark
import com.politex.ui.theme.resolvePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Модель палитры: режим × тон × пресет.
 *
 * Здесь нет Robolectric — только чистые функции, поэтому тест быстрый и проверяет
 * именно математику, а не рендер.
 */
class ThemeModelTest {
    private val presets = AppTheme.entries.toList()
    private val modes = ThemeMode.entries.toList()
    private val tones = SurfaceTone.entries.toList()

    @Test
    fun `эффективная тьма - ручной выбор важнее системного`() {
        // SYSTEM следует за системой
        assertTrue(effectiveDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(effectiveDark(ThemeMode.SYSTEM, systemDark = false))
        // Ручной выбор игнорирует систему
        assertTrue(effectiveDark(ThemeMode.DARK, systemDark = false))
        assertTrue(effectiveDark(ThemeMode.DARK, systemDark = true))
        assertFalse(effectiveDark(ThemeMode.LIGHT, systemDark = true))
        assertFalse(effectiveDark(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun `SYSTEM даёт палитру пресета по системной теме`() {
        presets.forEach { theme ->
            assertEquals(
                "${theme.title}: SYSTEM при светлой системе обязан дать светлую палитру",
                theme.palette,
                resolvePalette(theme, ThemeMode.SYSTEM, SurfaceTone.NATIVE, systemDark = false),
            )
            assertEquals(
                "${theme.title}: SYSTEM при тёмной системе обязан дать тёмную палитру",
                theme.darkPalette,
                resolvePalette(theme, ThemeMode.SYSTEM, SurfaceTone.NATIVE, systemDark = true),
            )
        }
    }

    @Test
    fun `NATIVE не меняет привычные палитры ни на байт`() {
        presets.forEach { theme ->
            modes.forEach { mode ->
                listOf(false, true).forEach { systemDark ->
                    val dark = effectiveDark(mode, systemDark)
                    val expected = if (dark) theme.darkPalette else theme.palette
                    assertEquals(
                        "${theme.title} / ${mode.id} / systemDark=$systemDark: тон NATIVE обязан быть тождеством",
                        expected,
                        resolvePalette(theme, mode, SurfaceTone.NATIVE, systemDark),
                    )
                }
            }
        }
    }

    @Test
    fun `тон не трогает акцентные цвета пресета`() {
        presets.forEach { theme ->
            tones.forEach { tone ->
                listOf(false, true).forEach { systemDark ->
                    val base =
                        resolvePalette(theme, ThemeMode.SYSTEM, SurfaceTone.NATIVE, systemDark)
                    val toned = resolvePalette(theme, ThemeMode.SYSTEM, tone, systemDark)
                    assertEquals("${theme.title}/${tone.id}: brandFill не должен меняться", base.brandFill, toned.brandFill)
                    assertEquals("${theme.title}/${tone.id}: activeFill не должен меняться", base.activeFill, toned.activeFill)
                    assertEquals("${theme.title}/${tone.id}: topBar не должен меняться", base.topBar, toned.topBar)
                }
            }
        }
    }

    @Test
    fun `SOFT светлее NATIVE, а DEEP в тёмной теме - чёрный`() {
        presets.forEach { theme ->
            val nativeLight = resolvePalette(theme, ThemeMode.LIGHT, SurfaceTone.NATIVE, false)
            val softLight = resolvePalette(theme, ThemeMode.LIGHT, SurfaceTone.SOFT, false)
            assertNotEquals("${theme.title}: SOFT обязан изменить светлый фон", nativeLight.bgMain, softLight.bgMain)
            assertTrue(
                "${theme.title}: SOFT-светлая должна быть темнее белого (тонированный фон)",
                luminance(softLight.bgMain) < luminance(nativeLight.bgMain),
            )

            val nativeDark = resolvePalette(theme, ThemeMode.DARK, SurfaceTone.NATIVE, false)
            val softDark = resolvePalette(theme, ThemeMode.DARK, SurfaceTone.SOFT, false)
            assertTrue(
                "${theme.title}: SOFT-тёмная обязана быть светлее NATIVE-тёмной",
                luminance(softDark.bgMain) > luminance(nativeDark.bgMain),
            )

            val deepDark = resolvePalette(theme, ThemeMode.DARK, SurfaceTone.DEEP, false)
            assertEquals("${theme.title}: DEEP-тёмная — чистый чёрный (AMOLED)", Color.Black, deepDark.bgMain)
            assertEquals("${theme.title}: DEEP-тёмная — чёрное меню", Color.Black, deepDark.menuBg)
        }
    }

    @Test
    fun `нет литерала белого вместо onAccent`() {
        presets.forEach { theme ->
            modes.forEach { mode ->
                tones.forEach { tone ->
                    val p = resolvePalette(theme, mode, tone, systemDark = false)
                    assertTrue(
                        "${theme.title}/${mode.id}/${tone.id}: onAccent обязан контрастировать с brandFill",
                        contrast(p.onAccent, p.brandFill) >= AA_TEXT_CONTRAST,
                    )
                    assertTrue(
                        "${theme.title}/${mode.id}/${tone.id}: onAccentMuted обязан контрастировать с brandFill",
                        contrast(p.onAccentMuted, p.brandFill) >= AA_TEXT_CONTRAST,
                    )
                    assertNotEquals(
                        "${theme.title}/${mode.id}/${tone.id}: вторичный текст на заливке не должен совпадать с основным",
                        p.onAccent,
                        p.onAccentMuted,
                    )
                }
            }
        }
    }

    @Test
    fun `все комбинации собираются`() {
        var count = 0
        presets.forEach { theme ->
            modes.forEach { mode ->
                tones.forEach { tone ->
                    listOf(false, true).forEach { systemDark ->
                        val p: MpkPalette = resolvePalette(theme, mode, tone, systemDark)
                        // Базовая санити: фон и текст не прозрачные
                        assertTrue(p.bgMain.alpha > 0f)
                        assertTrue(p.textTitle.alpha > 0f)
                        count++
                    }
                }
            }
        }
        assertEquals("8 пресетов × 3 режима × 3 тона × 2 системных темы", 144, count)
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