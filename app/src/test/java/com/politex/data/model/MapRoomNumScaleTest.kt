package com.politex.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapRoomNumScaleTest {
    private val base =
        """{"id":"r1","number":"101","title":"","note":"","status":"normal","x":0.5,"y":0.5,"w":0.1,"h":0.1"""

    private fun room(json: String): MapRoom =
        CollegeMapData
            .parse("""{"floors":{"1":{"rooms":[$json]}}}""")
            .floor("1")
            .rooms
            .single()

    @Test
    fun `numScale отсутствует — единица`() {
        assertEquals(1f, room("$base}").numScale, 1e-6f)
    }

    @Test
    fun `numScale читается из json`() {
        assertEquals(0.8f, room("""$base,"numScale":0.8}""").numScale, 1e-6f)
    }

    @Test
    fun `безумный numScale зажимается в границы`() {
        assertEquals(0.1f, room("""$base,"numScale":0}""").numScale, 1e-6f)
        assertEquals(3f, room("""$base,"numScale":99}""").numScale, 1e-6f)
    }
}
