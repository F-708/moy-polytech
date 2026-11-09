package com.politex

import com.politex.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Названия пресетов темы вернулись к «человеческим» прилагательным и обязаны
 * влезать в плитку одной строкой — тексты здесь же служат контрактом для UI-теста.
 */
class ThemePresetNamesTest {
    private val expected =
        listOf(
            "college" to "Фирменная",
            "emerald" to "Изумрудная",
            "bordeaux" to "Бордовая",
            "graphite" to "Графитовая",
            "indigo" to "Индиго",
            "amber" to "Янтарная",
            "steel" to "Стальная",
            "plum" to "Сливовая",
        )

    @Test
    fun `названия пресетов - прилагательные, порядок и wire-id стабильны`() {
        assertEquals(expected.map { it.first }, AppTheme.entries.map { it.id })
        assertEquals(expected.map { it.second }, AppTheme.entries.map { it.title })
    }

    @Test
    fun `названия уникальны и не вырождаются`() {
        val titles = AppTheme.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        assertTrue("у всех пресетов должно быть название", titles.all { it.isNotBlank() })
        assertTrue("названия - одиночные слова", titles.none { it.trim().contains(' ') })
    }

    @Test
    fun `самое длинное название укладывается в 3 плитки на 320dp`() {
        // Плитка: 14dp полей секции, 8dp зазор, вес 1f. Внутри 4dp поля на сторону.
        val screenDp = 320f
        val tileWidth = (screenDp - 2 * 14f - 2 * 8f) / 3f
        val textWidth = tileWidth - 2 * 4f
        val longest = AppTheme.entries.maxOf { it.title.length }
        // 11sp Medium: строчная кириллица ≈ 0.62 em; требуем запас под 11sp.
        val approxTextDp = longest * 11f * 0.62f
        assertTrue(
            "«${AppTheme.entries.maxBy { it.title.length }.title}» ($approxTextDp dp) шире текста плитки ($textWidth dp)",
            approxTextDp <= textWidth,
        )
    }
}