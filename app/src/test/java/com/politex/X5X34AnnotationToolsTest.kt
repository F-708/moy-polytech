package com.politex

import androidx.compose.ui.geometry.Offset
import com.politex.ui.screens.materials.Stroke
import com.politex.ui.screens.materials.eraserHitTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X5X34AnnotationToolsTest {
    private val line = Stroke(0xFF000000.toInt(), 4f, listOf(Offset(0.1f, 0.5f), Offset(0.5f, 0.5f), Offset(0.9f, 0.5f)))

    @Test
    fun `касание точно на линии — попадание`() {
        assertTrue(eraserHitTest(line, Offset(0.5f, 0.5f), threshold = 0.02f))
        assertTrue(eraserHitTest(line, Offset(0.1f, 0.5f), threshold = 0.02f))
    }

    @Test
    fun `касание далеко от линии — промах`() {
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.9f), threshold = 0.02f))
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.1f), threshold = 0.02f))
    }

    @Test
    fun `порог — расстояние меньше порога попадает, больше нет`() {

        assertTrue(eraserHitTest(line, Offset(0.5f, 0.519f), threshold = 0.02f))
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.55f), threshold = 0.02f))

        assertTrue(eraserHitTest(line, Offset(0.5f, 0.55f), threshold = 0.1f))
    }

    @Test
    fun `тычок из одной точки — расстояние до точки`() {
        val dot = Stroke(0xFF000000.toInt(), 8f, listOf(Offset(0.3f, 0.3f)))
        assertTrue(eraserHitTest(dot, Offset(0.3f, 0.3f), threshold = 0.02f))
        assertTrue(eraserHitTest(dot, Offset(0.31f, 0.3f), threshold = 0.02f))
        assertFalse(eraserHitTest(dot, Offset(0.4f, 0.3f), threshold = 0.02f))
        assertFalse(eraserHitTest(Stroke(0, 4f, emptyList()), Offset(0.3f, 0.3f), threshold = 0.5f))
    }

    @Test
    fun `aspect учитывается — вертикальное расстояние домножается на высоту-к-ширине`() {

        assertFalse(eraserHitTest(line, Offset(0.5f, 0.53f), threshold = 0.02f, aspect = 1f))

        assertTrue(eraserHitTest(line, Offset(0.5f, 0.53f), threshold = 0.02f, aspect = 0.5f))

        assertTrue(eraserHitTest(line, Offset(0.5f, 0.515f), threshold = 0.02f, aspect = 1f))
        assertFalse(eraserHitTest(line, Offset(0.5f, 0.515f), threshold = 0.02f, aspect = 2f))
    }
}
