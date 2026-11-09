package com.politex.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Числа эмбиент-слоя и математика движения — без Robolectric и без Compose.
 *
 * Тест держит диапазоны постановки: дождь 60–90 штрихов по 10–16 dp с alpha
 * 0.10–0.18 и падением 0.8–1.4 с, снег 40–60 точек r=1–2 dp за 4–7 с,
 * звёзды 50–70 точек с мерцанием 2–4 с.
 */
class AmbientParticlesTest {

    @Test
    fun `дождь попадает в заданные диапазоны`() {
        val drops = AmbientParticles.rain()
        assertTrue("капель ${drops.size}", drops.size in AmbientSpec.RAIN_COUNT)
        drops.forEach { drop ->
            assertTrue(drop.lengthDp in AmbientSpec.RAIN_LENGTH_DP)
            assertTrue(drop.alpha in AmbientSpec.RAIN_ALPHA)
            assertTrue(drop.fallSec in AmbientSpec.RAIN_FALL_SEC)
            assertTrue(drop.x in 0f..1f)
            assertTrue(drop.phase >= 0f && drop.phase < 1f)
        }
    }

    @Test
    fun `снег попадает в заданные диапазоны`() {
        val flakes = AmbientParticles.snow()
        assertTrue("снежинок ${flakes.size}", flakes.size in AmbientSpec.SNOW_COUNT)
        flakes.forEach { flake ->
            assertTrue(flake.radiusDp in AmbientSpec.SNOW_RADIUS_DP)
            assertTrue(flake.fallSec in AmbientSpec.SNOW_FALL_SEC)
            assertTrue(flake.driftSec > 0f)
            assertTrue(flake.driftDp > 0f)
        }
    }

    @Test
    fun `звёзды попадают в заданные диапазоны`() {
        val stars = AmbientParticles.stars()
        assertTrue("звёзд ${stars.size}", stars.size in AmbientSpec.STAR_COUNT)
        stars.forEach { star ->
            assertTrue(star.radiusDp in AmbientSpec.STAR_RADIUS_DP)
            assertTrue(star.twinkleSec in AmbientSpec.STAR_TWINKLE_SEC)
            assertTrue(star.lifeSec in AmbientSpec.STAR_LIFE_SEC)
            assertTrue(star.alpha > 0f)
            // Всё поле, а не только верх: нижняя половина тоже должна светиться.
            assertTrue(star.x in 0f..1f)
            assertTrue("звезда на y=${star.y}", star.y in 0f..1f)
        }
        assertTrue("есть звёзды ниже середины", stars.any { it.y > 0.5f })
        assertTrue("есть звёзды в верхней половине", stars.any { it.y < 0.5f })
    }

    @Test
    fun `набор частиц детерминирован`() {
        assertEquals(AmbientParticles.rain(), AmbientParticles.rain())
        assertEquals(AmbientParticles.snow(), AmbientParticles.snow())
        assertEquals(AmbientParticles.stars(), AmbientParticles.stars())
    }

    @Test
    fun `падение идёт по кругу и не выходит за границы`() {
        assertEquals(0f, AmbientParticles.fallProgress(0f, 1f, 0f), 0.0001f)
        assertEquals(0.5f, AmbientParticles.fallProgress(0.5f, 1f, 0f), 0.0001f)
        assertEquals(0f, AmbientParticles.fallProgress(1f, 1f, 0f), 0.0001f)

        var previous = -1f
        var wraps = 0
        (0..40).forEach { step ->
            val progress = AmbientParticles.fallProgress(step * 0.03125f, 1.25f, 0.3f)
            assertTrue(progress >= 0f && progress < 1f)
            if (progress < previous) wraps++
            previous = progress
        }
        assertTrue("падение должно зацикливаться", wraps > 0)
    }

    @Test
    fun `дрейф снежинки — синус в пределах единицы`() {
        (0..20).forEach { step ->
            val value = AmbientParticles.driftFactor(step * 0.25f, 3f, 0.1f)
            assertTrue(value >= -1.0001f && value <= 1.0001f)
        }
        assertEquals(0f, AmbientParticles.driftFactor(0f, 0f, 0f), 0.0001f)
    }

    @Test
    fun `мерцание звезды держится в нуле и единице`() {
        (0..30).forEach { step ->
            val value = AmbientParticles.twinkleFactor(step * 0.1f, 2.5f, 0.2f)
            assertTrue(value >= 0f && value <= 1f)
        }
    }

    @Test
    fun `цикл жизни звезды плавно зажигает и гасит её`() {
        val values = (0..120).map { AmbientParticles.starAlpha(it * 0.1f, 8f, 3f, 0.15f) }
        values.forEach { value ->
            assertTrue("альфа $value", value in 0f..1f)
        }
        // За цикл звезда обязана побывать и почти погасшей, и яркой.
        assertTrue("нет тёмной фазы", values.minOrNull()!! < 0.08f)
        assertTrue("нет яркой фазы", values.maxOrNull()!! > 0.8f)
        assertEquals(0f, AmbientParticles.starAlpha(1f, 0f, 3f, 0f), 0.0001f)
    }

    @Test
    fun `капля падает строго вертикально на всём времени жизни`() {
        val drops = AmbientParticles.rain()
        val widthPx = 1080f
        val heightPx = 2340f
        assertTrue(drops.isNotEmpty())
        drops.forEach { drop ->
            val lengthPx = drop.lengthDp * 3f
            (0..200).forEach { step ->
                val seconds = step * 0.07f
                val segment =
                    AmbientParticles.rainSegment(
                        drop = drop,
                        seconds = seconds,
                        widthPx = widthPx,
                        heightPx = heightPx,
                        lengthPx = lengthPx,
                    )
                assertEquals("капля съехала по X на ${seconds}s", 0f, segment.dx, 0.000001f)
                assertEquals("начало и конец по X", segment.startX, segment.endX, 0.000001f)
                assertTrue("капля длиной 0", segment.endY > segment.startY)
            }
        }
        assertEquals(0f, AmbientSpec.RAIN_SLANT_DEG, 0f)
    }
}