package com.politex.ui.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Генераторы сезонного декора: количество, диапазоны, детерминизм. */
class SeasonParticlesTest {

    @Test
    fun `гирлянда из фиксированного числа огней вдоль верха`() {
        val g = AmbientParticles.garland()
        assertEquals(AmbientSpec.GARLAND_COUNT, g.size)
        g.forEach { light ->
            assertTrue("x в 0..1", light.x in 0f..1f)
            assertTrue("у верхней кромки", light.y <= 0.05f)
            assertTrue("цвет в палитре", light.colorIndex in 0..1)
        }
        // Равномерная сетка слева направо.
        assertEquals(0f, g.first().x, 1e-6f)
        assertEquals(1f, g.last().x, 1e-6f)
    }

    @Test
    fun `гирлянда детерминирована`() {
        assertEquals(AmbientParticles.garland(), AmbientParticles.garland())
    }
}
