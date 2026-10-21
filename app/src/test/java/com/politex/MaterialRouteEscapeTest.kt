package com.politex

import com.politex.ui.screens.MaterialRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaterialRouteEscapeTest {

    private fun roundTrip(name: String) {
        val route = MaterialRoute.SubjectScreen(name)
        assertEquals(name, route.subjectName)
        assertEquals(route, MaterialRoute.decode(route.toString()))
    }

    @Test
    fun `pipe in subject name survives toString-decode round trip`() {
        roundTrip("Мат|анализ")
        roundTrip("a|b|c")
        roundTrip("|")
        roundTrip("обычный предмет")
    }

    @Test
    fun `percent sequences round trip unambiguously`() {
        roundTrip("100%")
        roundTrip("%7C")
        roundTrip("%25")
        roundTrip("a%7Cb%25c")
    }

    @Test
    fun `stack of routes with pipes splits back correctly`() {
        val routes =
            listOf(
                MaterialRoute.SubjectScreen("Хим|ия 100%"),
                MaterialRoute.ArchiveScreen,
                MaterialRoute.Viewer(42),
            )
        val stack = routes.joinToString("|") { it.toString() }
        assertEquals(routes, stack.split('|').mapNotNull(MaterialRoute.Companion::decode))
    }

    @Test
    fun `empty subject still decodes to null`() {
        assertNull(MaterialRoute.decode("s:"))
    }
}
