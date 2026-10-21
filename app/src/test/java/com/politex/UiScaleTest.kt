package com.politex

import com.politex.util.UiScale
import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {
    @Test
    fun `три варианта масштаба и точный from`() {
        assertEquals(UiScale.SMALL, UiScale.from(0.75f))
        assertEquals(UiScale.NORMAL, UiScale.from(1.0f))
        assertEquals(UiScale.LARGE, UiScale.from(1.25f))
    }

    @Test
    fun `from округляет к ближайшему`() {
        assertEquals(UiScale.NORMAL, UiScale.from(0.9f))
        assertEquals(UiScale.LARGE, UiScale.from(1.2f))
        assertEquals(UiScale.SMALL, UiScale.from(0.5f))
    }

    @Test
    fun `шаг 25 процентов между соседями`() {
        assertEquals(0.25f, UiScale.NORMAL.factor - UiScale.SMALL.factor, 1e-6f)
        assertEquals(0.25f, UiScale.LARGE.factor - UiScale.NORMAL.factor, 1e-6f)
    }
}
