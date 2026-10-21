package com.politex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.politex.ui.screens.CollegeMapScreen
import com.politex.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class RouteAutoStepsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun stepChips(initialRoom: String?, fromRoom: String?, showRoute: Boolean = false): Int {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                CollegeMapScreen(initialRoom = initialRoom, fromRoom = fromRoom, showRoute = showRoute, onBack = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.waitForIdle()
        return composeRule.onAllNodesWithText("Шаг", substring = true).fetchSemanticsNodes().size
    }

    @Test
    fun `маршрут от кабинета предыдущего урока показывает шаги`() {
        assertTrue(stepChips("301", "118") >= 2)
    }

    @Test
    fun `ненайденное откуда откатывается на главный вход а не гасит маршрут`() {
        assertTrue(
            "кабинет «999» отсутствует на плане — шаги пропали (регресс RC-AUTO-ROUTE)",
            stepChips("128", "999") >= 1,
        )
    }

    @Test
    fun `пустое откуда строит от главного входа`() {
        assertTrue(stepChips("128", "") >= 1)
    }

    @Test
    fun `без откуда маршрута нет`() {
        assertEquals(0, stepChips("128", null))
    }

    @Test
    fun `несуществующая цель маршрута не строится`() {
        assertEquals(0, stepChips("999", "101"))
    }
}
