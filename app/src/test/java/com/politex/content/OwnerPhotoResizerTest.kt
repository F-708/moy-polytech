package com.politex.content

import org.junit.Assert.assertEquals
import org.junit.Test

class OwnerPhotoResizerTest {
    @Test
    fun `константы ресайза 512 px q80`() {
        assertEquals(512, OwnerPhotoResizer.MAX_SIDE)
        assertEquals(80, OwnerPhotoResizer.QUALITY)
    }

    @Test
    fun `крупное фото уменьшается по длинной стороне`() {
        assertEquals(512 to 384, OwnerPhotoResizer.resizePlan(4000, 3000))
        assertEquals(384 to 512, OwnerPhotoResizer.resizePlan(3000, 4000))
        assertEquals(512 to 256, OwnerPhotoResizer.resizePlan(1000, 500))
    }

    @Test
    fun `мелкое фото не увеличивается`() {
        assertEquals(300 to 200, OwnerPhotoResizer.resizePlan(300, 200))
        assertEquals(512 to 512, OwnerPhotoResizer.resizePlan(512, 512))
        assertEquals(512 to 100, OwnerPhotoResizer.resizePlan(512, 100))
    }

    @Test
    fun `план сохраняет пропорции с округлением и минимумом 1`() {
        val (w, h) = OwnerPhotoResizer.resizePlan(1000, 999)
        assertEquals(512, w)
        assertEquals(511, h)
        assertEquals(512 to 1, OwnerPhotoResizer.resizePlan(100000, 100))
    }

    @Test
    fun `некорректные размеры дают нулевой план`() {
        assertEquals(0 to 0, OwnerPhotoResizer.resizePlan(0, 100))
        assertEquals(0 to 0, OwnerPhotoResizer.resizePlan(100, -5))
        assertEquals(0 to 0, OwnerPhotoResizer.resizePlan(100, 100, 0))
    }

    @Test
    fun `sampleSize степень двойки и не мельче цели`() {
        assertEquals(4, OwnerPhotoResizer.sampleSize(4000, 3000))
        assertEquals(4, OwnerPhotoResizer.sampleSize(2048, 2048))
        assertEquals(2, OwnerPhotoResizer.sampleSize(1024, 1024))
        assertEquals(1, OwnerPhotoResizer.sampleSize(1200, 800))
        assertEquals(1, OwnerPhotoResizer.sampleSize(300, 200))
        assertEquals(1, OwnerPhotoResizer.sampleSize(0, 0))
    }

    @Test
    fun `sampleSize согласован с планом`() {
        val src = listOf(4000 to 3000, 1200 to 800, 512 to 512, 30000 to 200)
        for ((w, h) in src) {
            val sample = OwnerPhotoResizer.sampleSize(w, h)
            val (pw, ph) = OwnerPhotoResizer.resizePlan(w, h)
            org.junit.Assert.assertTrue(w / sample >= pw || sample == 1)
            org.junit.Assert.assertTrue(h / sample >= ph || sample == 1)
        }
    }
}
