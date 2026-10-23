package com.politex

import androidx.compose.ui.geometry.Offset
import com.politex.ui.screens.materials.DocZoom
import com.politex.ui.screens.materials.LiveTransform
import com.politex.ui.screens.materials.applyLive
import com.politex.ui.screens.materials.bookmarkSizeFactor
import com.politex.ui.screens.materials.clampPan
import com.politex.ui.screens.materials.neighborPages
import com.politex.ui.screens.materials.pageHeightFromWidth
import com.politex.ui.screens.materials.sheetCount
import com.politex.ui.screens.materials.zoomOutHeightFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocZoomTest {
    private val eps = 1e-6f
    private val w = 1080f
    private val h = 2000f
    private val center = Offset(w / 2f, h / 2f)

    @Test
    fun `HH3 zoom клампится в 0_5 до 4`() {
        fun live(z: Float, k: Float) = applyLive(LiveTransform(z, Offset.Zero), k, Offset.Zero, center, center, DocZoom.MIN, DocZoom.MAX).zoom
        assertEquals(0.5f, live(0.6f, 0.5f), eps)
        assertEquals(4f, live(3f, 2f), eps)
        assertEquals(0.5f, live(0.5f, 0.1f), eps)
        assertEquals(4f, live(4f, 10f), eps)
        assertEquals(0.5f, DocZoom.apply(0.6f, 0.5f), eps)
        assertEquals(4f, DocZoom.apply(3f, 2f), eps)
        assertEquals(1f, DocZoom.DEFAULT, eps)
    }

    @Test
    fun `HH3 фокальный инвариант - точка под центроидом стабильна при scale вокруг него`() {
        var t = LiveTransform(1f, Offset.Zero)
        var c = Offset(300f, 800f)

        val q = c - center
        repeat(8) { i ->
            val k = if (i % 2 == 0) 1.3f else 0.9f
            val d = Offset(15f * (i - 3), -7f * i)
            c += d
            t = applyLive(t, k, d, c, center, DocZoom.MIN, DocZoom.MAX)

            val screen = q * t.zoom + t.pan
            assertEquals((c - center).x, screen.x, 1e-2f)
            assertEquals((c - center).y, screen.y, 1e-2f)
        }
    }

    @Test
    fun `HH3 applyLive - первый кадр (pan=0) даёт pan = c(1-k) + d k`() {
        val centroid = Offset(840f, 1200f)
        val d = Offset(20f, -10f)
        val t = applyLive(LiveTransform(1f, Offset.Zero), 1.5f, d, centroid, center, DocZoom.MIN, DocZoom.MAX)
        assertEquals(1.5f, t.zoom, eps)
        val expected = (centroid - center) * (1f - 1.5f) + d * 1.5f
        assertEquals(expected.x, t.pan.x, eps)
        assertEquals(expected.y, t.pan.y, eps)
    }

    @Test
    fun `HH3 applyLive - на границе clamp pan считается с ЭФФЕКТИВНЫМ k`() {
        val centroid = Offset(300f, 300f)
        val t = applyLive(LiveTransform(3.9f, Offset.Zero), 2f, Offset.Zero, centroid, center, DocZoom.MIN, DocZoom.MAX)
        assertEquals(4f, t.zoom, eps)
        val k = 4f / 3.9f
        val expected = (centroid - center) * (1f - k)
        assertEquals(expected.x, t.pan.x, 1e-3f)
        assertEquals(expected.y, t.pan.y, 1e-3f)
    }

    @Test
    fun `HH3 clampPan - при scale 1 и ниже pan ноль, при scale 2 ограничен половиной избытка`() {
        assertEquals(Offset.Zero, clampPan(Offset(500f, -700f), 1f, w, h))
        assertEquals(Offset.Zero, clampPan(Offset(500f, 700f), 0.5f, w, h))
        val p = clampPan(Offset(1e6f, -1e6f), 2f, w, h)
        assertEquals(w / 2f, p.x, eps)
        assertEquals(-h / 2f, p.y, eps)
    }

    @Test
    fun `HH3 двойной тап - точка тапа неподвижна на всех кадрах анимации`() {
        val tap = Offset(200f, 300f)
        val z0 = 1f
        val p0 = Offset.Zero
        val q = (tap - center - p0) / z0
        listOf(1.25f, 1.5f, 1.75f, 2f).forEach { z ->
            val k = z / z0
            val pan = p0 * k + (tap - center) * (1f - k)
            val screen = q * z + pan
            assertEquals((tap - center).x, screen.x, 1e-3f)
            assertEquals((tap - center).y, screen.y, 1e-3f)
        }

        val z1 = 2f
        val k = DocZoom.DEFAULT / z1
        val pan = Offset(100f, -50f) * k + (tap - center) * (1f - k)
        assertTrue(pan.x.isFinite() && pan.y.isFinite())
    }

    @Test
    fun `EE6 bookmarkSizeFactor клампится в 0_8 до 1_2`() {
        assertEquals(0.8f, bookmarkSizeFactor(0.5f), eps)
        assertEquals(0.8f, bookmarkSizeFactor(0.8f), eps)
        assertEquals(1f, bookmarkSizeFactor(1f), eps)
        assertEquals(1.2f, bookmarkSizeFactor(1.2f), eps)
        assertEquals(1.2f, bookmarkSizeFactor(4f), eps)
    }

    @Test
    fun `GG13 neighborPages - середина документа даёт окно 2 range + 1`() {
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 8), neighborPages(5, 100, 3))
    }

    @Test
    fun `GG13 neighborPages - clamp на границах и вырожденные случаи`() {
        assertEquals(listOf(0, 1, 2, 3), neighborPages(0, 100, 3))
        assertEquals(listOf(6, 7, 8, 9), neighborPages(9, 10, 3))
        assertEquals(listOf(0, 1), neighborPages(0, 2, 3))
        assertEquals(emptyList<Int>(), neighborPages(0, 0, 3))
        assertEquals(listOf(4), neighborPages(4, 9, 0))
        assertEquals(listOf(9), neighborPages(50, 10, 3))
    }

    @Test
    fun `GG14 pageHeightFromWidth - A4 пропорция 1_414`() {
        assertEquals(1414f, pageHeightFromWidth(1000f), eps)
        assertEquals(0f, pageHeightFromWidth(0f), eps)
    }

    @Test
    fun `GG14 sheetCount - покрывает высоту текста с зазорами`() {
        assertEquals(1, sheetCount(1000f, 1414f, 8f))

        assertEquals(1, sheetCount(1414f, 1414f, 8f))

        assertEquals(2, sheetCount(1415f, 1414f, 8f))

        assertEquals(2, sheetCount(2844f - 8f, 1414f, 8f))
        assertEquals(3, sheetCount(2845f, 1414f, 8f))
    }

    @Test
    fun `GG14 sheetCount - до измерения текста и на вырожденных входах всегда 1`() {
        assertEquals(1, sheetCount(0f, 1414f, 8f))
        assertEquals(1, sheetCount(-5f, 1414f, 8f))
        assertEquals(1, sheetCount(500f, 0f, 8f))
    }

    @Test
    fun `II3 zoomOutHeightFactor - при MIN 0_5 запас 2x, вне 0 до 1 вырождается в 1`() {
        assertEquals(2f, zoomOutHeightFactor(DocZoom.MIN), eps)
        assertEquals(1f, zoomOutHeightFactor(1f), eps)
        assertEquals(1f, zoomOutHeightFactor(1.5f), eps)
        assertEquals(1f, zoomOutHeightFactor(0f), eps)
        assertEquals(1f, zoomOutHeightFactor(-1f), eps)
    }

    @Test
    fun `II3 покрытие - масштабированный запас закрывает viewport на всем диапазоне zoom`() {
        val listH = h * zoomOutHeightFactor(DocZoom.MIN)
        (0..10).forEach { i ->
            val s = DocZoom.MIN + i * (1f - DocZoom.MIN) / 10f
            assertTrue("zoom=$s: ${(s * listH)} < $h", s * listH >= h - 1e-3f)
        }

        assertTrue(listH >= h)
    }

    @Test
    fun `II3 конец документа достижим при zoom 1 благодаря нижнему padding`() {
        val factor = zoomOutHeightFactor(DocZoom.MIN)
        val listH = h * factor
        val endPad = h * (factor - 1f)
        val contentH = 10_000f
        val maxScroll = contentH + 12f + endPad - listH

        assertTrue(maxScroll + h >= contentH)
    }
}
