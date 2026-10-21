package com.politex

import androidx.compose.ui.geometry.Offset
import com.politex.data.model.FloorMap
import com.politex.data.model.MapRoom
import com.politex.ui.screens.mapTapToRoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapTapHitTest {
    private val imgW = 1000f
    private val imgH = 600f
    private val room =
        MapRoom(
            id = "r214",
            number = "214",
            title = "",
            note = "",
            status = "ok",
            x = 0.5f,
            y = 0.5f,
            w = 0.1f,
            h = 0.1f,
        )
    private val floor = FloorMap(rooms = listOf(room))

    private val zoom = 2.6f
    private val targetOffsetX = imgW / 2f - room.x * imgW * zoom
    private val targetOffsetY = imgH / 2f - room.y * imgH * zoom

    @Test
    fun `тап в центре приближённой карты попадает в кабинет по target-значениям`() {
        val hit =
            mapTapToRoom(
                pos = Offset(imgW / 2f, imgH / 2f),
                targetScale = zoom,
                targetOffsetX = targetOffsetX,
                targetOffsetY = targetOffsetY,
                imgW = imgW,
                imgH = imgH,
                floor = floor,
            )
        assertEquals("214", hit?.number)
    }

    @Test
    fun `тап рядом с кабинетом дотягивается slop-фолбэком`() {

        val hit =
            mapTapToRoom(
                pos = Offset(imgW / 2f + 18f, imgH / 2f - 12f),
                targetScale = zoom,
                targetOffsetX = targetOffsetX,
                targetOffsetY = targetOffsetY,
                imgW = imgW,
                imgH = imgH,
                floor = floor,
            )
        assertEquals("214", hit?.number)
    }

    @Test
    fun `тап далеко от кабинета — промах`() {
        val hit =
            mapTapToRoom(
                pos = Offset(20f, 20f),
                targetScale = zoom,
                targetOffsetX = targetOffsetX,
                targetOffsetY = targetOffsetY,
                imgW = imgW,
                imgH = imgH,
                floor = floor,
            )
        assertNull(hit)
    }

    @Test
    fun `нулевые размеры картинки не роняют hit-test`() {
        assertNull(
            mapTapToRoom(
                pos = Offset(0f, 0f),
                targetScale = zoom,
                targetOffsetX = 0f,
                targetOffsetY = 0f,
                imgW = 0f,
                imgH = 0f,
                floor = floor,
            ),
        )
    }
}
