package com.politex.ui.effects

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.isDarkThemeActive
import com.politex.weather.Precipitation
import com.politex.weather.WeatherScene

/** Готовый набор частиц под текущее состояние: пересобирается только при его смене. */
data class AmbientField(
    val rain: List<RainDrop>,
    val snow: List<SnowFlake>,
    val stars: List<Star>,
) {
    val isEmpty: Boolean get() = rain.isEmpty() && snow.isEmpty() && stars.isEmpty()
}

/** Детерминированный набор частиц для [scene] (без обращения к Android). */
@Composable
fun rememberAmbientField(scene: WeatherScene): AmbientField {
    val hasRain = scene.precipitation == Precipitation.RAIN
    val hasSnow = scene.precipitation == Precipitation.SNOW
    // В дождь и снег неба не видно: звёзды не рисуем, даже если сцена их просит.
    val hasStars = scene.stars && !hasRain && !hasSnow
    return remember(hasRain, hasSnow, hasStars) {
        AmbientField(
            rain = if (hasRain) AmbientParticles.rain() else emptyList(),
            snow = if (hasSnow) AmbientParticles.snow() else emptyList(),
            stars = if (hasStars) AmbientParticles.stars() else emptyList(),
        )
    }
}

/**
 * Кадровые часы слоя: секунды с первого кадра.
 *
 * Время берётся из кадрового цикла Compose, поэтому слой не держит собственный поток.
 * При выключенной анимации цикл не запускается и значение остаётся нулевым —
 * на экране один статичный кадр.
 */
@Composable
fun rememberAmbientSeconds(animated: Boolean): MutableFloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var startNanos = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { frameNanos ->
                if (startNanos < 0L) startNanos = frameNanos
                seconds.floatValue = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }
    return seconds
}

/**
 * Эмбиент-слой погоды: дождь, снег и звёзды на Canvas.
 *
 * Рисует только частицы — фон под ними остаётся чужим (это и нужно карте: слой
 * лежит на фоне карты, но под схемой этажа). Касания не перехватываются: у слоя
 * нет `pointerInput`, события проходят к элементам под ним.
 *
 * Состояние времени читается внутри фазы отрисовки, поэтому кадр вызывает только
 * перерисовку, без рекомпозиции дерева.
 */
@Composable
fun AmbientLayer(
    scene: WeatherScene,
    modifier: Modifier = Modifier,
) {
    val field = rememberAmbientField(scene)
    if (field.isEmpty) return
    val dark = isDarkThemeActive
    val seconds = rememberAmbientSeconds(scene.animated)

    Canvas(modifier = modifier) {
        drawAmbientParticles(
            field = field,
            seconds = seconds.floatValue,
            dark = dark,
        )
    }
}

/**
 * Общая отрисовка частиц: один код на Canvas-слой, модификатор фона и полосу.
 *
 * [seconds] — время в секундах (0 при выключенной анимации), [dark] — тема,
 * от неё зависят цвета: на светлом фоне тёмные капли, на тёмном — светлые.
 *
 * [alphaScale] приглушает весь слой (для ненавязчивой полосы на чужих экранах),
 * [verticalFade] плавно гасит частицы к низу (0 — не гасить, 1 — в ноль),
 * [surfaceHeight] — высота области падения: для полосы это её высота, а не экрана.
 */
internal fun DrawScope.drawAmbientParticles(
    field: AmbientField,
    seconds: Float,
    dark: Boolean,
    alphaScale: Float = 1f,
    verticalFade: Float = 0f,
    surfaceHeight: Float = size.height,
) {
    val widthPx = size.width
    val heightPx = surfaceHeight
    if (widthPx <= 0f || heightPx <= 0f) return

    fun fadeAt(y: Float): Float {
        if (verticalFade <= 0f) return 1f
        val k = (y / heightPx).coerceIn(0f, 1f)
        return (1f - verticalFade * k).coerceIn(0f, 1f)
    }

    if (field.rain.isNotEmpty()) {
        val rainColor = if (dark) Color(0xFFCFE0F2) else Color(0xFF33517A)
        // На светлой теме дождь почти растворялся: чуть поднимаем альфу и толщину.
        // Геометрия при этом не меняется — капля по-прежнему строго вертикальна.
        val themeScale = if (dark) 1f else 1.45f
        val strokeWidth = if (dark) 1.dp.toPx() else 1.15.dp.toPx()
        field.rain.forEach { drop ->
            val length = drop.lengthDp.dp.toPx()
            val segment =
                AmbientParticles.rainSegment(
                    drop = drop,
                    seconds = seconds,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    lengthPx = length,
                )
            val alpha = (drop.alpha * themeScale * alphaScale * fadeAt(segment.endY)).coerceIn(0f, 1f)
            drawLine(
                color = rainColor.copy(alpha = alpha),
                start = Offset(segment.startX, segment.startY),
                end = Offset(segment.endX, segment.endY),
                strokeWidth = strokeWidth,
            )
        }
    }

    if (field.snow.isNotEmpty()) {
        val snowColor = if (dark) Color(0xFFF2F7FF) else Color(0xFF5B7FA8)
        val baseAlpha = if (dark) 0.72f else 0.55f
        field.snow.forEach { flake ->
            val radius = flake.radiusDp.dp.toPx()
            val progress = AmbientParticles.fallProgress(seconds, flake.fallSec, flake.phase)
            val y = progress * (heightPx + radius * 2f) - radius * 2f
            val drift = AmbientParticles.driftFactor(seconds, flake.driftSec, flake.phase) * flake.driftDp.dp.toPx()
            val alpha = (baseAlpha * alphaScale * fadeAt(y)).coerceIn(0f, 1f)
            drawCircle(
                color = snowColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(flake.x * widthPx + drift, y),
            )
        }
    }

    if (field.stars.isNotEmpty()) {
        // На светлом небе белые точки не читаются — рисуем тёмно-синие и тише.
        val starColor = if (dark) Color(0xFFFFF6E0) else Color(0xFF44506B)
        val themeScale = if (dark) 1f else 0.6f
        field.stars.forEach { star ->
            val life = AmbientParticles.starAlpha(seconds, star.lifeSec, star.twinkleSec, star.phase)
            val y = star.y * heightPx
            val alpha = (star.alpha * life * themeScale * alphaScale * fadeAt(y)).coerceIn(0f, 1f)
            if (alpha <= 0.01f) return@forEach
            drawCircle(
                color = starColor.copy(alpha = alpha),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(star.x * widthPx, y),
            )
        }
    }
}