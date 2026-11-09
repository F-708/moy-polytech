package com.politex.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила слоя: осадки, ночь, звёзды и приоритет ручных тумблеров над авто.
 *
 * Всё без Android: [resolveWeatherScene] — чистая функция, тесты гоняются обычным JUnit.
 */
class WeatherPolicyTest {

    private val dayRain = WeatherSnapshot(
        precipitation = Precipitation.RAIN,
        isDay = true,
        temperatureC = 4,
        weatherCode = 61,
        precipitationMm = 0.4,
        snowfallCm = 0.0,
        fetchedAtMillis = 1_762_600_000_000L,
    )

    private val nightClear = dayRain.copy(precipitation = Precipitation.NONE, isDay = false, weatherCode = 0)

    private fun scene(
        effects: EffectsMode = EffectsMode.AUTO,
        sky: SkyMode = SkyMode.AUTO,
        snapshot: WeatherSnapshot? = dayRain,
        dark: Boolean = false,
        animations: Boolean = true,
    ): WeatherScene = resolveWeatherScene(effects, sky, snapshot, dark, animations)

    @Test
    fun `авто берёт дождь из прогноза`() {
        assertEquals(Precipitation.RAIN, scene().precipitation)
    }

    @Test
    fun `авто без данных не рисует осадки`() {
        assertEquals(Precipitation.NONE, scene(snapshot = null).precipitation)
    }

    @Test
    fun `ручной дождь сильнее авто-ясно`() {
        assertEquals(Precipitation.RAIN, scene(effects = EffectsMode.RAIN, snapshot = nightClear).precipitation)
    }

    @Test
    fun `ручной снег сильнее авто-дождя`() {
        assertEquals(Precipitation.SNOW, scene(effects = EffectsMode.SNOW).precipitation)
    }

    @Test
    fun `выключено гасит и прогноз, и ручной выбор`() {
        assertEquals(Precipitation.NONE, scene(effects = EffectsMode.OFF).precipitation)
        assertEquals(Precipitation.NONE, scene(effects = EffectsMode.OFF, snapshot = nightClear).precipitation)
    }

    @Test
    fun `ночь и тёмная тема — звёзды есть`() {
        val night = scene(snapshot = nightClear, dark = true)
        assertTrue(night.night)
        assertTrue(night.stars)
    }

    @Test
    fun `ночь в светлой теме — звёзды есть, но фон не темнеет`() {
        val night = scene(snapshot = nightClear, dark = false, sky = SkyMode.NIGHT)
        assertTrue(night.night)
        // Цвет звёзд выбирает слой отрисовки: на светлом фоне они тёмно-синие.
        assertTrue(night.stars)
    }

    @Test
    fun `дождь перекрывает звёзды, как и снег`() {
        val rainy = scene(snapshot = dayRain, dark = true, sky = SkyMode.NIGHT)
        assertEquals(Precipitation.RAIN, rainy.precipitation)
        assertFalse(rainy.stars)
    }

    @Test
    fun `днём звёзд нет даже в тёмной теме`() {
        val day = scene(snapshot = dayRain, dark = true, sky = SkyMode.DAY)
        assertFalse(day.night)
        assertFalse(day.stars)
    }

    @Test
    fun `ручное небо Ночь сильнее дневного прогноза`() {
        // В дождь звёзд нет, поэтому берём ясный день: проверяем именно тумблер «Небо».
        val clearDay = dayRain.copy(precipitation = Precipitation.NONE, precipitationMm = 0.0, weatherCode = 0)
        val forced = scene(sky = SkyMode.NIGHT, snapshot = clearDay, dark = true)
        assertTrue(forced.night)
        assertTrue(forced.stars)
    }

    @Test
    fun `ручное небо День сильнее ночного прогноза`() {
        val forced = scene(sky = SkyMode.DAY, snapshot = nightClear, dark = true)
        assertFalse(forced.night)
        assertFalse(forced.stars)
    }

    @Test
    fun `снегопад перекрывает звёзды`() {
        val snowy = scene(effects = EffectsMode.SNOW, snapshot = nightClear, dark = true)
        assertTrue(snowy.night)
        assertFalse(snowy.stars)
    }

    @Test
    fun `анимация выключается системным флагом и энергосбережением`() {
        assertTrue(effectsAnimationAllowed(animatorScaleZero = false, powerSaveMode = false))
        assertFalse(effectsAnimationAllowed(animatorScaleZero = true, powerSaveMode = false))
        assertFalse(effectsAnimationAllowed(animatorScaleZero = false, powerSaveMode = true))
        assertFalse(scene(animations = false).animated)
    }

    @Test
    fun `ясный день без эффектов ничего не рисует`() {
        assertFalse(scene(snapshot = null).hasEffects)
        assertTrue(scene().hasEffects)
    }
}