package com.politex

import com.politex.ui.screens.MaterialListUi
import com.politex.ui.screens.materialListUi
import org.junit.Assert.assertEquals
import org.junit.Test

class MaterialListUiTest {
    @Test
    fun `CC7 loading until first Room emission, empty only when loaded and truly empty`() {
        assertEquals(MaterialListUi.LOADING, materialListUi(loaded = false, itemCount = 0))
        assertEquals(MaterialListUi.LOADING, materialListUi(loaded = false, itemCount = 3))
        assertEquals(MaterialListUi.EMPTY, materialListUi(loaded = true, itemCount = 0))
        assertEquals(MaterialListUi.CONTENT, materialListUi(loaded = true, itemCount = 1))
    }
}
