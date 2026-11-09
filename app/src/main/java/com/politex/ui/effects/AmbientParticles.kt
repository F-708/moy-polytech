package com.politex.ui.effects

import kotlin.math.PI
import kotlin.random.Random

/**
 * Числа эмбиент-слоя в одном месте.
 *
 * Диапазоны — не вкусовая правка, а требования постановки: их проверяют тесты
 * [AmbientParticlesTest], чтобы «снег» однажды не превратился в 300 точек на весь экран.
 */
object AmbientSpec {
    val RAIN_COUNT = 60..90
    val RAIN_LENGTH_DP = 10f..16f
    val RAIN_ALPHA = 0.10f..0.18f
    val RAIN_FALL_SEC = 0.8f..1.4f

    /**
     * Наклона нет и не должно быть: капля падает строго вниз, X не меняется.
     *
     * Константа оставлена как явное «0», чтобы будущая правка не вернула
     * косой штрих незаметно — её проверяет [AmbientParticlesTest].
     */
    const val RAIN_SLANT_DEG = 0f

    val SNOW_COUNT = 40..60
    val SNOW_RADIUS_DP = 1f..2f
    val SNOW_FALL_SEC = 4f..7f
    val SNOW_DRIFT_SEC = 2.5f..5f
    val SNOW_DRIFT_DP = 4f..12f

    val STAR_COUNT = 50..70
    val STAR_RADIUS_DP = 0.6f..1.4f
    val STAR_TWINKLE_SEC = 2f..4f

    /** Полный цикл жизни звезды: плавно зажглась, померцала, погасла. */
    val STAR_LIFE_SEC = 7f..16f

    /** Фиксированное зерно: рисунок одинаков между кадрами и перезапусками. */
    const val SEED = 20261108L
}

/** Одна капля: позиция по X (0..1), фаза, период падения, длина штриха, прозрачность. */
data class RainDrop(
    val x: Float,
    val phase: Float,
    val fallSec: Float,
    val lengthDp: Float,
    val alpha: Float,
)

/** Одна снежинка: базовая позиция, фазы падения и дрейфа, амплитуда и радиус. */
data class SnowFlake(
    val x: Float,
    val phase: Float,
    val fallSec: Float,
    val driftSec: Float,
    val driftDp: Float,
    val radiusDp: Float,
)

/**
 * Одна звезда: позиция, радиус, период мерцания, цикл жизни и базовая прозрачность.
 *
 * [lifeSec] — сколько длится цикл «появилась → погасла»; [phase] сдвигает его,
 * поэтому звёзды зажигаются и гаснут не хором, а вразнобой.
 */
data class Star(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val twinkleSec: Float,
    val lifeSec: Float,
    val phase: Float,
    val alpha: Float,
)

/** Отрезок капли в пикселях: X начала и конца обязан совпадать. */
data class RainSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
) {
    /** Смещение по X за весь путь. Для вертикального дождя — ровно ноль. */
    val dx: Float get() = endX - startX
}

/**
 * Детерминированная генерация частиц.
 *
 * Чистые функции без Android и Compose: их проверяют обычные юнит-тесты,
 * а слой рисует ровно то, что здесь получилось.
 */
object AmbientParticles {

    fun rain(
        random: Random = Random(AmbientSpec.SEED),
    ): List<RainDrop> {
        val count = random.nextInt(AmbientSpec.RAIN_COUNT.first, AmbientSpec.RAIN_COUNT.last + 1)
        return List(count) {
            RainDrop(
                x = random.nextFloat(),
                phase = random.nextFloat(),
                fallSec = random.range(AmbientSpec.RAIN_FALL_SEC),
                lengthDp = random.range(AmbientSpec.RAIN_LENGTH_DP),
                alpha = random.range(AmbientSpec.RAIN_ALPHA),
            )
        }
    }

    fun snow(
        random: Random = Random(AmbientSpec.SEED + 1),
    ): List<SnowFlake> {
        val count = random.nextInt(AmbientSpec.SNOW_COUNT.first, AmbientSpec.SNOW_COUNT.last + 1)
        return List(count) {
            SnowFlake(
                x = random.nextFloat(),
                phase = random.nextFloat(),
                fallSec = random.range(AmbientSpec.SNOW_FALL_SEC),
                driftSec = random.range(AmbientSpec.SNOW_DRIFT_SEC),
                driftDp = random.range(AmbientSpec.SNOW_DRIFT_DP),
                radiusDp = random.range(AmbientSpec.SNOW_RADIUS_DP),
            )
        }
    }

    fun stars(
        random: Random = Random(AmbientSpec.SEED + 2),
    ): List<Star> {
        val count = random.nextInt(AmbientSpec.STAR_COUNT.first, AmbientSpec.STAR_COUNT.last + 1)
        return List(count) {
            Star(
                // Всё поле, а не только верхняя половина: заказчик видел пустую нижнюю часть.
                x = random.nextFloat(),
                y = random.nextFloat(),
                radiusDp = random.range(AmbientSpec.STAR_RADIUS_DP),
                twinkleSec = random.range(AmbientSpec.STAR_TWINKLE_SEC),
                lifeSec = random.range(AmbientSpec.STAR_LIFE_SEC),
                phase = random.nextFloat(),
                alpha = 0.25f + random.nextFloat() * 0.45f,
            )
        }
    }

    /**
     * Прогресс падения частицы: 0 — над верхней кромкой, 1 — за нижней.
     *
     * [seconds] — время с начала кадра, [periodSec] — полный проход, [phase] — сдвиг.
     */
    fun fallProgress(
        seconds: Float,
        periodSec: Float,
        phase: Float,
    ): Float {
        if (periodSec <= 0f) return phase
        val raw = (seconds / periodSec + phase) % 1f
        return if (raw < 0f) raw + 1f else raw
    }

    /** Смещение дрейфа снежинки по синусу, доля амплитуды в -1..1. */
    fun driftFactor(
        seconds: Float,
        periodSec: Float,
        phase: Float,
    ): Float {
        if (periodSec <= 0f) return 0f
        return kotlin.math.sin((2.0 * PI * (seconds / periodSec + phase)).toFloat())
    }

    /** Мерцание звезды: 0 — погасла, 1 — в полную силу. */
    fun twinkleFactor(
        seconds: Float,
        periodSec: Float,
        phase: Float,
    ): Float {
        if (periodSec <= 0f) return 0.5f
        val wave = kotlin.math.sin((2.0 * PI * (seconds / periodSec + phase)).toFloat())
        return 0.5f + 0.5f * wave
    }

    /**
     * Жизненный цикл звезды: 0 — ещё не зажглась / уже погасла, 1 — в зените.
     *
     * Кривая `sin²(π·цикл)` даёт ноль и нулевую производную на краях цикла,
     * поэтому звезда не «щёлкает», а плавно проявляется и растворяется.
     * Поверх — лёгкое мерцание, чтобы небо не выглядело гирляндой.
     */
    fun starAlpha(
        seconds: Float,
        lifeSec: Float,
        twinkleSec: Float,
        phase: Float,
    ): Float {
        if (lifeSec <= 0f) return 0f
        val raw = (seconds / lifeSec + phase) % 1f
        val cycle = if (raw < 0f) raw + 1f else raw
        val fade = kotlin.math.sin((PI * cycle).toFloat())
        val smooth = fade * fade
        val twinkle = 0.65f + 0.35f * twinkleFactor(seconds, twinkleSec, phase)
        return (smooth * twinkle).coerceIn(0f, 1f)
    }

    /**
     * Геометрия одной капли в пикселях.
     *
     * X берётся один раз и не меняется ни на каком времени: капля падает строго
     * вниз. Именно это проверяет тест на вертикальность — `dx` всегда 0.
     */
    fun rainSegment(
        drop: RainDrop,
        seconds: Float,
        widthPx: Float,
        heightPx: Float,
        lengthPx: Float,
    ): RainSegment {
        val progress = fallProgress(seconds, drop.fallSec, drop.phase)
        val y = progress * (heightPx + lengthPx) - lengthPx
        val x = drop.x * widthPx
        return RainSegment(startX = x, startY = y, endX = x, endY = y + lengthPx)
    }

    private fun Random.range(range: ClosedFloatingPointRange<Float>): Float {
        val span = range.endInclusive - range.start
        return range.start + nextFloat() * span
    }
}