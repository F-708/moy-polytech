package com.politex.data.model

import com.politex.ui.screens.offsetPolyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RouteOrthogonalityTest {
    private val eps = 1e-4f
    private val aspect = CollegeRouteFinder.PLAN_ASPECT

    private fun p(x: Float, y: Float) = MapPoint(x, y)

    private fun assertOrthogonal(points: List<MapPoint>, label: String) {
        for (i in 1 until points.size) {
            val dx = abs(points[i].x - points[i - 1].x) * aspect
            val dy = abs(points[i].y - points[i - 1].y)
            assertTrue(
                "$label: сегмент $i (${points[i - 1]}→${points[i]}) не осевой в plan-px",
                dx < eps || dy < eps,
            )
        }
    }

    private fun assertNoZigzag(points: List<MapPoint>, label: String) {
        for (i in 2 until points.size) {
            val a = points[i - 2]
            val b = points[i - 1]
            val c = points[i]
            val abx = b.x - a.x
            val aby = b.y - a.y
            val cross = abx * (c.y - b.y) - aby * (c.x - b.x)
            val dot = abx * (c.x - b.x) + aby * (c.y - b.y)
            assertFalse(
                "$label: зигзаг вышел-вернулся в точках $a→$b→$c",
                abs(cross) < eps && dot < 0f,
            )
        }
    }

    private fun floorWithCorridor() = FloorMap(
        corridors = listOf(MapCorridor("c1", listOf(p(0.1f, 0.5f), p(0.9f, 0.5f)), 0.012f)),
    )

    @Test
    fun `JJ16 - routePolyline строго ортогонален на дрожащем входе`() {
        val floor = floorWithCorridor()
        val jitter = listOf(
            p(0.1f, 0.5f),
            p(0.3f, 0.502f),
            p(0.52f, 0.498f),
            p(0.7f, 0.501f),
            p(0.9f, 0.5f),
        )
        val out = CollegeRouteFinder.routePolyline(jitter, floor)
        assertOrthogonal(out, "jitter")
        assertNoZigzag(out, "jitter")
        assertEquals(jitter.first(), out.first())
        assertEquals(jitter.last(), out.last())
    }

    @Test
    fun `JJ16 - routePolyline убирает зигзаг вышел-вернулся`() {
        val floor = floorWithCorridor()
        val spiked = listOf(p(0.2f, 0.5f), p(0.8f, 0.5f), p(0.4f, 0.5f))
        val out = CollegeRouteFinder.routePolyline(spiked, floor)
        assertNoZigzag(out, "spike")
        assertEquals(p(0.2f, 0.5f), out.first())
        assertEquals(p(0.4f, 0.5f), out.last())
    }

    @Test
    fun `JJ16 - routePolyline убирает полный разворот за точку входа`() {
        val floor = floorWithCorridor()
        val reversed = listOf(p(0.5f, 0.5f), p(0.8f, 0.5f), p(0.2f, 0.5f))
        val out = CollegeRouteFinder.routePolyline(reversed, floor)
        assertNoZigzag(out, "full reversal")
        assertEquals(2, out.size)
        assertEquals(p(0.5f, 0.5f), out.first())
        assertEquals(p(0.2f, 0.5f), out.last())
    }

    @Test
    fun `JJ16 - routePolyline сохраняет старт и финиш точно (коннект к двери)`() {
        val floor = floorWithCorridor()
        val doorA = p(0.3f, 0.46f)
        val doorB = p(0.8f, 0.501f)
        val raw = listOf(doorA, p(0.3f, 0.5f), p(0.8f, 0.5f), doorB)
        val out = CollegeRouteFinder.routePolyline(raw, floor)
        assertEquals("старт остаётся в двери A", doorA, out.first())
        assertEquals("финиш остаётся в двери B", doorB, out.last())
        assertOrthogonal(out, "door connect")
    }

    @Test
    fun `JJ16 - routePolyline не выпускает точки за пределы плана`() {
        val floor = floorWithCorridor()
        val raw = listOf(p(-0.05f, 0.5f), p(0.5f, 0.5f), p(1.1f, 0.5f))
        val out = CollegeRouteFinder.routePolyline(raw, floor)
        assertTrue("все x в 0..1: $out", out.all { it.x in 0f..1f })
        assertTrue("все y в 0..1: $out", out.all { it.y in 0f..1f })
        assertOrthogonal(out, "clamp")
    }

    @Test
    fun `JJ16 - orthogonalize preserveEnds не двигает последнюю точку`() {
        val pts = listOf(p(0.1f, 0.5f), p(0.5f, 0.5f), p(0.52f, 0.2f))
        val out = CollegeRouteFinder.orthogonalize(pts, emptyList(), aspect, preserveEnds = true)
        assertEquals("последняя точка сохранена", pts.last(), out.last())
        assertEquals("первая точка сохранена", pts.first(), out.first())
        assertOrthogonal(out, "preserveEnds")
    }

    @Test
    fun `JJ16 - orthogonalize без preserveEnds сохраняет старое поведение снапа`() {
        val pts = listOf(p(0.1f, 0.5f), p(0.5f, 0.502f), p(0.52f, 0.2f))
        val out = CollegeRouteFinder.orthogonalize(pts, emptyList(), aspect)
        assertEquals(3, out.size)
        assertEquals(0.5f, out[1].y, 1e-6f)
        assertEquals(0.5f, out[2].x, 1e-6f)
    }

    @Test
    fun `JJ16 - offsetPolyline сохраняет ортогональность L-образного пути`() {
        val line = listOf(p(0.1f, 0.5f), p(0.5f, 0.5f), p(0.5f, 0.2f))
        val out = offsetPolyline(line, imgW = 1376f, imgH = 768f, offsetPx = 6f)
        assertEquals(3, out.size)
        val d1x = abs(out[1].x - out[0].x)
        val d1y = abs(out[1].y - out[0].y)
        val d2x = abs(out[2].x - out[1].x)
        val d2y = abs(out[2].y - out[1].y)
        assertTrue("первая нога остаётся горизонтальной: $out", d1y < 1e-6f && d1x > 1e-6f)
        assertTrue("вторая нога остаётся вертикальной: $out", d2x < 1e-6f && d2y > 1e-6f)
    }

    @Test
    fun `JJ16 - offsetPolyline сдвигает обе ноги на одну сторону`() {
        val line = listOf(p(0.1f, 0.5f), p(0.5f, 0.5f), p(0.5f, 0.2f))
        val out = offsetPolyline(line, imgW = 1000f, imgH = 1000f, offsetPx = 8f)
        assertEquals(0.5f - 0.008f, out[0].y, 1e-5f)
        assertEquals(0.5f - 0.008f, out[2].x, 1e-5f)
        assertEquals(out[0].y, out[1].y, 1e-6f)
        assertEquals(out[1].x, out[2].x, 1e-6f)
    }

    @Test
    fun `JJ16 - simplifyRoute схлопывает разворот dot меньше 0`() {
        val out = CollegeRouteFinder.simplifyRoute(listOf(p(0f, 0f), p(1f, 0f), p(-1f, 0f)))
        assertEquals(listOf(p(0f, 0f), p(-1f, 0f)), out)
    }

    @Test
    fun `JJ16 - routePolyline сохраняет маленький легитимный уголок (регрессия 331-301)`() {
        val floor = floorWithCorridor()
        val raw = listOf(p(0.3347f, 0.3063f), p(0.3374f, 0.2931f), p(0.55f, 0.2931f))
        val out = CollegeRouteFinder.routePolyline(raw, floor)
        assertOrthogonal(out, "small corner")
        assertEquals(raw.first(), out.first())
        assertEquals(raw.last(), out.last())
        assertTrue(
            "уголок не должен быть съеден финальной чисткой: $out",
            out.any { abs(it.x - 0.3347f) < 1e-4f && abs(it.y - 0.2931f) < 1e-4f } ||
                out.any { abs(it.x - 0.3374f) < 1e-4f && abs(it.y - 0.3063f) < 1e-4f },
        )
    }

    @Test
    fun `JJ16 - routePolyline на петле из уголков не оставляет коллинеарных дублей`() {
        val floor = floorWithCorridor()
        val raw = listOf(p(0.2f, 0.5f), p(0.6f, 0.53f), p(0.62f, 0.5f), p(0.9f, 0.5f))
        val out = CollegeRouteFinder.routePolyline(raw, floor)
        assertOrthogonal(out, "corner loop")
        assertNoZigzag(out, "corner loop")
        for (i in 2 until out.size) {
            val a = out[i - 2]
            val b = out[i - 1]
            val c = out[i]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            assertTrue("коллинеарный дубль в $a→$b→$c", abs(cross) >= eps)
        }
        assertEquals(raw.first(), out.first())
        assertEquals(raw.last(), out.last())
    }
}
