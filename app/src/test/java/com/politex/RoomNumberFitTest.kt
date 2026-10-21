package com.politex

import com.politex.ui.screens.roomNumberFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomNumberFitTest {
    @Test
    fun `никогда не превышает влезающий размер`() {

        val fit = roomNumberFit(raw = 0.5f, median = 1.2f, minReadable = 0.2f)
        assertTrue("fit=$fit должен быть <= raw=0.5", fit!! <= 0.5f)
    }

    @Test
    fun `мелкий кабинет не раздувается до медианы`() {

        val fit = roomNumberFit(raw = 0.4f, median = 1.0f, minReadable = 0.1f)
        assertEquals(0.4f, fit!!, 0.0001f)
    }

    @Test
    fun `крупный кабинет получает немножечко больше медианы`() {
        val fit = roomNumberFit(raw = 5.0f, median = 1.0f, minReadable = 0.1f)!!
        assertTrue("ожидали > медианы, получили $fit", fit > 1.0f)
        assertEquals("не более 115% медианы", 1.15f, fit, 0.0001f)
    }

    @Test
    fun `крупный кабинет всё равно ограничен влезающим размером`() {

        val fit = roomNumberFit(raw = 1.05f, median = 1.0f, minReadable = 0.1f)!!
        assertTrue("fit=$fit должен быть <= raw=1.05", fit <= 1.05f)
    }

    @Test
    fun `кабинет ровно по медиане получает медиану`() {

        val fit = roomNumberFit(raw = 1.0f, median = 1.0f, minReadable = 0.1f)!!
        assertEquals(1.0f, fit, 0.0001f)
    }

    @Test
    fun `мелкий кабинет всё равно виден — ужимается до влезающего, а не пропадает`() {

        val fit = roomNumberFit(raw = 0.05f, median = 1.0f, minReadable = 0.2f)
        assertEquals(0.05f, fit!!, 0.0001f)
    }

    @Test
    fun `размеры большинства кабинетов совпадают`() {

        val fits =
            listOf(2.0f, 3.5f, 10f, 1.5f).map {
                roomNumberFit(raw = it, median = 1.0f, minReadable = 0.1f)!!
            }
        assertEquals(1, fits.distinct().size)
    }

    @Test
    fun `нулевой или отрицательный размер — не рисуем`() {
        assertNull(roomNumberFit(raw = 0f, median = 1f, minReadable = 0.1f))
        assertNull(roomNumberFit(raw = -1f, median = 1f, minReadable = 0.1f))
    }

    @Test
    fun `почти все кабинеты получают номер`() {

        var raw = 0.1f
        var drawn = 0
        var total = 0
        while (raw <= 1.0f) {
            total++
            if (roomNumberFit(raw, median = 0.8f, minReadable = 0.35f) != null) drawn++
            raw += 0.05f
        }
        assertEquals("должны рисоваться все кабинеты с запасом > 0", total, drawn)
    }

    @Test
    fun `минимальный размер не выталкивает цифру за рамку`() {

        var raw = 0.05f
        while (raw < 3f) {
            roomNumberFit(raw, median = 1.0f, minReadable = 0.9f)?.let {
                assertTrue("при raw=$raw получили $it — вылезает за рамку", it <= raw + 0.0001f)
            }
            raw += 0.01f
        }
    }

    @Test
    fun `почти все кабинеты получают номер при разумном минимуме`() {

        var raw = 0.1f
        var drawn = 0
        var total = 0
        while (raw <= 1.0f) {
            total++
            if (roomNumberFit(raw, median = 0.8f, minReadable = 0.3f) != null) drawn++
            raw += 0.05f
        }
        assertEquals(total, drawn)
    }
}
