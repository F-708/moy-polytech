package com.politex.data.model

import com.politex.ui.screens.offsetPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimplifyRouteTest {
    private fun p(x: Float, y: Float) = MapPoint(x, y)

    private fun assertSimplified(expected: List<MapPoint>, input: List<MapPoint>) {
        val out = CollegeRouteFinder.simplifyRoute(input)
        assertEquals(expected, out)
    }

    @Test
    fun `коллинеарная середина убирается`() {
        assertSimplified(listOf(p(0f, 0f), p(2f, 0f)), listOf(p(0f, 0f), p(1f, 0f), p(2f, 0f)))
    }

    @Test
    fun `шип - возврат назад - убирается`() {
        assertSimplified(listOf(p(0f, 0f), p(2f, 0f)), listOf(p(0f, 0f), p(5f, 0f), p(2f, 0f)))
    }

    @Test
    fun `каскад шип плюс коллинеар схлопывается за вызов`() {
        assertSimplified(
            listOf(p(0f, 0f), p(2f, 0f)),
            listOf(p(0f, 0f), p(3f, 0f), p(1f, 0f), p(2f, 0f)),
        )
    }

    @Test
    fun `прямой угол не трогается`() {
        val corner = listOf(p(0f, 0f), p(1f, 0f), p(1f, 1f))
        assertSimplified(corner, corner)
    }

    @Test
    fun `дубли подряд убираются`() {
        assertSimplified(listOf(p(0f, 0f), p(1f, 0f)), listOf(p(0f, 0f), p(0f, 0f), p(1f, 0f)))
    }

    @Test
    fun `короткие списки возвращаются как есть`() {
        assertEquals(emptyList<MapPoint>(), CollegeRouteFinder.simplifyRoute(emptyList()))
        assertEquals(listOf(p(1f, 2f)), CollegeRouteFinder.simplifyRoute(listOf(p(1f, 2f))))
    }

    @Test
    fun `концы пути сохраняются даже среди шума`() {
        val input = listOf(p(0f, 0f), p(0.5f, 0f), p(0.25f, 0f), p(0.5f, 0.0001f), p(0.9f, 0.4f))
        val out = CollegeRouteFinder.simplifyRoute(input)
        assertEquals(p(0f, 0f), out.first())
        assertEquals(p(0.9f, 0.4f), out.last())
        assertTrue("шум должен уменьшать число точек", out.size < input.size)
    }
}

class RouteBuilderPureTest {
    @Test
    fun `offsetPolyline сдвигает горизонталь вдоль перпендикуляра`() {
        val line = listOf(MapPoint(0f, 0f), MapPoint(1f, 0f))

        val out = offsetPolyline(line, imgW = 100f, imgH = 100f, offsetPx = 10f)
        assertEquals(2, out.size)
        assertEquals(0f, out[0].x, 1e-5f)
        assertEquals(-0.1f, out[0].y, 1e-5f)
        assertEquals(1f, out[1].x, 1e-5f)
        assertEquals(-0.1f, out[1].y, 1e-5f)
    }

    @Test
    fun `offsetPolyline с нулевым сдвигом не трогает точки`() {
        val line = listOf(MapPoint(0f, 0f), MapPoint(0.5f, 0.3f))
        assertEquals(line, offsetPolyline(line, 100f, 60f, 0f))
    }
}
