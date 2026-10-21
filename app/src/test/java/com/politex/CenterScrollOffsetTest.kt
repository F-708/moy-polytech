package com.politex

import com.politex.ui.screens.centerScrollOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class CenterScrollOffsetTest {
    @Test
    fun `first chip scrolls to zero`() {
        assertEquals(0, centerScrollOffset(chipStart = 0, chipWidth = 100, viewport = 800, maxScroll = 1000))
    }

    @Test
    fun `middle chip is centered`() {

        assertEquals(150, centerScrollOffset(chipStart = 500, chipWidth = 100, viewport = 800, maxScroll = 1000))
    }

    @Test
    fun `last chip clamps to max`() {

        assertEquals(1000, centerScrollOffset(chipStart = 1700, chipWidth = 100, viewport = 800, maxScroll = 1000))
    }
}
