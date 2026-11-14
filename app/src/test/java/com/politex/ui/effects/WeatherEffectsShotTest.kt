package com.politex.ui.effects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.applyAppearance
import com.politex.weather.Precipitation
import com.politex.weather.WeatherScene
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Погодные эффекты на нейтральном контенте: светлая и тёмная тема,
 * дождь, снег, ясная ночь и ясный день.
 *
 * Один и тот же кадр используется как «до/после»: имя файла задаётся
 * переменной окружения `WEATHER_SHOT_PREFIX`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class WeatherEffectsShotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val prefix: String
        get() = System.getenv("WEATHER_SHOT_PREFIX") ?: "weather"

    private fun shoot(
        name: String,
        dark: Boolean,
        scene: WeatherScene,
        advanceMs: Long = 3_000,
    ) {
        applyAppearance(
            AppTheme.COLLEGE,
            if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            SurfaceTone.NATIVE,
            systemDark = dark,
        )
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize().weatherScene(scene)) {
                    Content(dark)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(advanceMs)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("build/screenshots/$prefix-$name.png")
    }

    @androidx.compose.runtime.Composable
    private fun Content(dark: Boolean) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Расписание на сегодня",
                color = if (dark) Color(0xFFF2F5FA) else Color(0xFF111827),
                fontSize = 22.sp,
            )
            repeat(3) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                    color = if (dark) Color(0xFF1B2433) else Color(0xFFFFFFFF),
                    tonalElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "8:30–10:00 · Математика",
                            color = if (dark) Color(0xFFE8EDF6) else Color(0xFF1F2937),
                            fontSize = 14.sp,
                        )
                        Text(
                            text = "каб. 128 · Иванова А. П.",
                            color = if (dark) Color(0xFFAAB6C8) else Color(0xFF6B7280),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    private fun rain(dark: Boolean) =
        shoot("rain-${if (dark) "dark" else "light"}", dark, WeatherScene(Precipitation.RAIN, night = false, stars = false, animated = true))

    private fun snow(dark: Boolean) =
        shoot("snow-${if (dark) "dark" else "light"}", dark, WeatherScene(Precipitation.SNOW, night = false, stars = false, animated = true))

    private fun clearNight(dark: Boolean) =
        shoot(
            "night-${if (dark) "dark" else "light"}",
            dark,
            // Заказчик (2026-11-13): звёзды только в тёмной теме, поэтому в светлой
            // ночи слой пустой — политика resolveStars гейтит их темой.
            WeatherScene(Precipitation.NONE, night = true, stars = dark, animated = true),
        )

    private fun clearDay(dark: Boolean) =
        shoot("day-${if (dark) "dark" else "light"}", dark, WeatherScene(Precipitation.NONE, night = false, stars = false, animated = false))

    @Test
    fun `дождь светлая тема`() = rain(false)

    @Test
    fun `дождь тёмная тема`() = rain(true)

    @Test
    fun `снег светлая тема`() = snow(false)

    @Test
    fun `снег тёмная тема`() = snow(true)

    @Test
    fun `ясная ночь светлая тема`() = clearNight(false)

    @Test
    fun `ясная ночь тёмная тема`() = clearNight(true)

    @Test
    fun `падающая звезда в окне прохода`() {
        // Кадр ловим в середине окна первой падающей звезды: часы теста
        // двигаются детерминированно, а генератор — с фиксированным зерном.
        val shot = AmbientParticles.shootingStars().first()
        // Середина пролёта: elapsed = flightSec/2 от старта цикла.
        val cycle = shot.flightSec / 2f / shot.periodSec
        val phaseFrac = ((cycle - shot.phase % 1f) % 1f + 1f) % 1f
        val seconds = phaseFrac * shot.periodSec
        shoot(
            "night-shooting",
            true,
            WeatherScene(Precipitation.NONE, night = true, stars = true, animated = true),
            advanceMs = (seconds * 1000).toLong().coerceAtLeast(1L),
        )
    }

    @Test
    fun `ясный день светлая тема`() = clearDay(false)

    @Test
    fun `ясный день тёмная тема`() = clearDay(true)

    private fun seasonal(
        name: String,
        dark: Boolean,
        season: com.politex.weather.Season,
    ) = shoot(
        name,
        dark,
        WeatherScene(
            Precipitation.NONE,
            night = dark,
            stars = false,
            animated = true,
            season = season,
        ),
    )

    @Test
    fun `Новый год светлая тема`() = seasonal("newyear-light", false, com.politex.weather.Season.NEW_YEAR)

    @Test
    fun `Новый год тёмная тема`() = seasonal("newyear-dark", true, com.politex.weather.Season.NEW_YEAR)
}