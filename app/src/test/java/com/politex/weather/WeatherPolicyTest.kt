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

    /** 2026-11-12 22:00 Минска — заведомо ночь по восходу/заходу. */
    private val nightNow = 1_794_510_000_000L

    /** 2026-11-12 12:00 Минска — заведомо день. */
    private val dayNow = 1_794_474_000_000L

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
        now: Long = nightNow,
    ): WeatherScene = resolveWeatherScene(effects, sky, snapshot, dark, animations, nowMillis = now)

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
    fun `ночь в светлой теме - звёзд нет, тема гейтит слой`() {
        // Заказчик (2026-11-13): при светлой теме звёзды не показываются вовсе.
        val night = scene(snapshot = nightClear, dark = false, sky = SkyMode.NIGHT)
        assertTrue(night.night)
        assertFalse(night.stars)
    }

    @Test
    fun `глубокая ночь в светлой теме — звёзд нет, в тёмной — есть`() {
        assertFalse(scene(snapshot = nightClear, dark = false, now = nightNow).stars)
        assertTrue(scene(snapshot = nightClear, dark = true, now = nightNow).stars)
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
    fun `авто-ночь идёт по восходу и заходу колледжа, а не по прогнозу`() {
        assertTrue(scene(snapshot = nightClear, dark = true, now = nightNow).night)
        val day = scene(snapshot = nightClear, dark = true, now = dayNow)
        assertFalse(day.night)
        assertFalse(day.stars)
    }

    @Test
    fun `авто-звёзды только глубокой ночью, а не с первой минуты сумерек`() {
        // 2026-11-12 18:00 Минска: заход был в 17:18, отступ 2 ч ещё не прошёл.
        val dusk = scene(snapshot = nightClear, dark = true, now = 1_794_495_600_000L)
        assertTrue(dusk.night)
        assertFalse(dusk.stars)
        // 22:00 — середина ночи: звёзды есть.
        assertTrue(scene(snapshot = nightClear, dark = true, now = nightNow).stars)
    }

    @Test
    fun `ручное небо Ночь не гейтится глубокой ночью`() {
        val forced = scene(sky = SkyMode.NIGHT, snapshot = nightClear, dark = true, now = dayNow)
        assertTrue(forced.night)
        assertTrue(forced.stars)
    }

    @Test
    fun `глубокая ночь начинается через отступ после захода и кончается до восхода`() {
        assertFalse(SolarNight.isDeepNight(1_794_495_600_000L)) // 18:00 — сумерки
        assertTrue(SolarNight.isDeepNight(nightNow)) // 22:00 — середина ночи
        assertFalse(SolarNight.isDeepNight(dayNow)) // полдень
        assertFalse(SolarNight.isDeepNight(1_794_456_000_000L)) // 07:00 — близко к восходу
    }

    @Test
    fun `ночь считается даже без прогноза`() {
        assertTrue(scene(snapshot = null, dark = true, now = nightNow).night)
        assertFalse(scene(snapshot = null, dark = true, now = dayNow).night)
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
        assertFalse(scene(snapshot = null, now = dayNow).hasEffects)
        assertTrue(scene(now = nightNow).hasEffects)
    }
}