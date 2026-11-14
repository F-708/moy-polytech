package com.politex.ui.effects

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.isDarkThemeActive
import com.politex.weather.DevControlsStore
import com.politex.weather.EffectsMode
import com.politex.weather.SkyMode
import com.politex.weather.WeatherHub
import com.politex.weather.WeatherScene
import com.politex.weather.currentSeason
import com.politex.weather.resolveWeatherScene

/**
 * Решение слоя «что рисовать сейчас» из двух источников: прогноз и тема.
 *
 * Погода и ночь не настраиваются: осадки берутся из прогноза, ночь — из времени
 * закат/восход. Подписки: [WeatherHub.snapshot] (пришёл новый прогноз) и
 * `isDarkThemeActive` (смена темы). Обновление сети инициируется здесь же,
 * но не чаще раза в час — гейт в [WeatherHub].
 */
@Composable
fun rememberWeatherScene(): WeatherScene {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snapshot by WeatherHub.snapshot.collectAsState()
    val dark = isDarkThemeActive
    val animations = rememberAnimationsAllowed()

    // Обновление — на возврате в приложение, а не на каждом показе экрана:
    // гейт «не чаще раза в час» всё равно отсечёт лишнее, но так композиция
    // никогда не запускает сеть (важно для тестов и превью).
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) WeatherHub.ensureFresh(context)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ручные переключатели из панели AdminPlus; в обычной сборке всё в AUTO.
    val devRevision = DevControlsStore.revision.intValue
    val effects = remember(context, devRevision) { DevControlsStore.readEffects(context) }
    val sky = remember(context, devRevision) { DevControlsStore.readSky(context) }
    val seasonOverride = remember(context, devRevision) { DevControlsStore.readSeason(context) }

    return remember(snapshot, dark, animations, effects, sky, seasonOverride) {
        resolveWeatherScene(
            effects = effects,
            sky = sky,
            snapshot = snapshot,
            darkTheme = dark,
            animationsAllowed = animations,
            season = if (seasonOverride != com.politex.weather.Season.NONE) seasonOverride else currentSeason(),
        )
    }
}

/**
 * Разрешена ли анимация: читается при появлении слоя и перепроверяется
 * на `ON_RESUME` — пользователь мог включить энергосбережение, не закрывая приложение.
 */
@Composable
fun rememberAnimationsAllowed(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(WeatherHub.animationsAllowed(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    allowed = WeatherHub.animationsAllowed(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return allowed
}

/**
 * Фон экрана с погодой: базовый цвет, ночная заливка и частицы — всё под контентом.
 *
 * Рисуется в фазе отрисовки модификатора, поэтому контент выше не рекомпозируется
 * на кадр: частицы читают кадровое время уже внутри `drawBehind`.
 *
 * При «ясно днём» результат совпадает с прежним `background(ColorBgMain)` —
 * привычный вид не меняется, пока погода или ночь реально не включились.
 */
@Composable
fun Modifier.weatherScene(scene: WeatherScene = rememberWeatherScene()): Modifier {
    val field = rememberAmbientField(scene)
    val seconds = rememberAmbientSeconds(scene.animated)
    val dark = isDarkThemeActive
    val base = ColorBgMain
    // Ночь меняет фон только в тёмной теме. Светлая тема — это выбор пользователя,
    // и «ночь» не должна её затемнять: фон остаётся ровно ColorBgMain.
    val night = scene.night && dark
    val nightTop = Color(0xFF071527)

    return this
        .fillMaxSize()
        .drawBehind {
            drawRect(color = base)
            if (night) {
                drawRect(
                    brush =
                    Brush.verticalGradient(
                        0f to nightTop.copy(alpha = 0.72f),
                        0.85f to nightTop.copy(alpha = 0f),
                    ),
                )
            }
            if (!field.isEmpty) {
                drawAmbientParticles(field = field, seconds = seconds.floatValue, dark = dark)
            }
        }
}

/**
 * Только частицы, без подложки: для экранов со своим фоном (карта).
 *
 * Ночная заливка сюда не входит намеренно — она перекрасила бы схему этажа.
 */
@Composable
fun Modifier.weatherParticles(scene: WeatherScene = rememberWeatherScene()): Modifier {
    val field = rememberAmbientField(scene)
    if (field.isEmpty) return this
    val seconds = rememberAmbientSeconds(scene.animated)
    val dark = isDarkThemeActive
    return this.drawBehind {
        drawAmbientParticles(field = field, seconds = seconds.floatValue, dark = dark)
    }
}