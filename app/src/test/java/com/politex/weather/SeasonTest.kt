package com.politex.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Окно Нового года: 20.12–10.01. */
class SeasonTest {

    @Test
    fun `Новый год начинается 20 декабря`() {
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.DECEMBER, 20))
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.DECEMBER, 31))
    }

    @Test
    fun `Новый год длится до 10 января`() {
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.JANUARY, 1))
        assertEquals(Season.NEW_YEAR, seasonForDate(Calendar.JANUARY, 10))
        assertEquals(Season.NONE, seasonForDate(Calendar.JANUARY, 11))
    }

    @Test
    fun `до новогоднего окна сезона нет`() {
        assertEquals(Season.NONE, seasonForDate(Calendar.DECEMBER, 19))
    }

    @Test
    fun `обычная весенняя дата без сезона`() {
        assertEquals(Season.NONE, seasonForDate(Calendar.MARCH, 15))
    }

    @Test
    fun `OFF гасит сезон в сцене`() {
        val scene = resolveWeatherScene(
            effects = EffectsMode.OFF,
            sky = SkyMode.AUTO,
            snapshot = null,
            darkTheme = false,
            animationsAllowed = true,
            season = Season.NEW_YEAR,
        )
        assertEquals(Season.NONE, scene.season)
    }

    @Test
    fun `сезон проходит в сцену при включённых эффектах`() {
        val scene = resolveWeatherScene(
            effects = EffectsMode.AUTO,
            sky = SkyMode.AUTO,
            snapshot = null,
            darkTheme = false,
            animationsAllowed = true,
            season = Season.NEW_YEAR,
        )
        assertEquals(Season.NEW_YEAR, scene.season)
    }
}
