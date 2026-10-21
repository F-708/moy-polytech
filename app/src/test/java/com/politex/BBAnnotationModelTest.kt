package com.politex

import androidx.compose.ui.geometry.Offset
import com.politex.ui.screens.materials.AnnotationAction
import com.politex.ui.screens.materials.AnnotationUndoSession
import com.politex.ui.screens.materials.Stroke
import com.politex.ui.screens.materials.applyUndo
import com.politex.ui.screens.materials.clampToBox
import com.politex.ui.screens.materials.compensatedStrokeWidthPx
import com.politex.ui.screens.materials.eraserHitTest
import com.politex.ui.screens.materials.eraserThresholdNorm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BBAnnotationModelTest {
    private val eps = 1e-4f
    private val s1 = Stroke(0xFF000000.toInt(), 4f, listOf(Offset(0.1f, 0.5f), Offset(0.9f, 0.5f)))
    private val s2 = Stroke(0xFFE53935.toInt(), 2f, listOf(Offset(0.3f, 0.3f)))

    @Test
    fun `CC5 ImageViewer — штрих константен НА ЭКРАНЕ при graphicsLayer scale (layerScale = zoom)`() {

        val pxPerDp = 3f
        listOf(0.5f, 1f, 2f, 4f).forEach { scale ->
            val drawn = compensatedStrokeWidthPx(4f, pxPerDp, scale)
            assertEquals("screen-const при scale=$scale", 12f, drawn * scale, eps)
        }
    }

    @Test
    fun `DD2+HH3 PDF — толщина штриха константна ОТНОСИТЕЛЬНО СТРАНИЦЫ (экранная ~ zoom)`() {

        val containerW = 1080f
        val pxPerDp = 3f
        listOf(0.5f, 1f, 2f, 4f).forEach { zoom ->
            val drawn = compensatedStrokeWidthPx(4f, pxPerDp, 1f)
            assertEquals("экранная толщина ~ zoom при $zoom", 12f * zoom, drawn * zoom, eps)
            assertEquals("page-const при $zoom", 12f / containerW, drawn / containerW, eps)
        }
    }

    @Test
    fun `BB3 компенсация толщины — layerScale 0 не даёт бесконечность`() {
        assertTrue(compensatedStrokeWidthPx(4f, 3f, 0f).isFinite())
        assertTrue(eraserThresholdNorm(4f, 8f, 3f, 0f, 0f).isFinite())
    }

    @Test
    fun `DD2+HH3 PDF eraser — порог в page-координатах, нормализованный не зависит от zoom`() {

        val containerW = 1080f
        val norm =
            listOf(0.5f, 1f, 2f, 4f).map {
                eraserThresholdNorm(
                    4f,
                    8f,
                    3f,
                    boxWidthPx = containerW,
                    layerScale = 1f,
                )
            }
        norm.forEach { assertEquals(norm.first(), it, eps) }

        val stroke = Stroke(0, 4f, listOf(Offset(0.2f, 0.5f), Offset(0.8f, 0.5f)))
        norm.forEach { th ->
            assertTrue(eraserHitTest(stroke, Offset(0.5f, 0.5f), th, aspect = 1.4f))
            assertFalse(eraserHitTest(stroke, Offset(0.5f, 0.9f), th, aspect = 1.4f))
        }
    }

    @Test
    fun `BB3 eraser при зуме — image viewer компенсирует graphicsLayer scale`() {
        val atZoom1 = eraserThresholdNorm(4f, 8f, 3f, boxWidthPx = 1080f, layerScale = 1f)
        val atZoom2 = eraserThresholdNorm(4f, 8f, 3f, boxWidthPx = 1080f, layerScale = 2f)
        assertEquals(atZoom1, atZoom2 * 2f, eps)
    }

    @Test
    fun `BB3 clamp к границам страницы — симметрично со всех четырёх сторон`() {
        assertEquals(Offset(0f, 10f), clampToBox(Offset(-50f, 10f), 100f, 100f))
        assertEquals(Offset(100f, 10f), clampToBox(Offset(150f, 10f), 100f, 100f))
        assertEquals(Offset(10f, 0f), clampToBox(Offset(10f, -50f), 100f, 100f))
        assertEquals(Offset(10f, 100f), clampToBox(Offset(10f, 150f), 100f, 100f))
        assertEquals(Offset(10f, 10f), clampToBox(Offset(10f, 10f), 100f, 100f))
    }

    @Test
    fun `BB3 undo-сессия — LIFO по действиям, выход из документа очищает историю`() {
        val session = AnnotationUndoSession()
        session.record(AnnotationAction.Add(0, s1))
        session.record(AnnotationAction.Erase(1, 0, s2))
        assertTrue(session.pop() is AnnotationAction.Erase)
        assertTrue(session.pop() is AnnotationAction.Add)
        assertNull(session.pop())

        session.record(AnnotationAction.Add(0, s1))
        session.clear()
        assertNull("история сессии не переживает выход из документа", session.pop())
    }

    @Test
    fun `BB3 undo Add — убирает последний добавленный штрих`() {
        val updated = applyUndo(AnnotationAction.Add(0, s1), mapOf(0 to listOf(s2, s1)))
        assertEquals(listOf(s2), updated[0])
    }

    @Test
    fun `BB3 undo Erase — возвращает стёртый штрих на его позицию`() {
        val updated = applyUndo(AnnotationAction.Erase(0, 1, s2), mapOf(0 to listOf(s1)))
        assertEquals(listOf(s1, s2), updated[0])
    }

    @Test
    fun `BB3 undo Erase — индекс клампится для пустого списка`() {
        val updated = applyUndo(AnnotationAction.Erase(3, 5, s1), emptyMap())
        assertEquals(listOf(s1), updated[3])
    }

    @Test
    fun `BB3 корзина — undo ClearAll восстанавливает аннотации всего документа`() {
        val before = mapOf(0 to listOf(s1), 2 to listOf(s2), 5 to listOf(s1, s2))
        val cleared = before.mapValues { emptyList<Stroke>() }
        val snapshot = before.filterValues { it.isNotEmpty() }
        val restored = applyUndo(AnnotationAction.ClearAll(snapshot), cleared)
        assertEquals(before, restored)
    }

    @Test
    fun `BB3 applyUndo Add не трогает другие страницы`() {
        val map = mapOf(0 to listOf(s1), 1 to listOf(s2))
        val updated = applyUndo(AnnotationAction.Add(0, s1), map)
        assertEquals(listOf(s2), updated[1])
        assertEquals(emptyList<Stroke>(), updated[0])
    }
}
