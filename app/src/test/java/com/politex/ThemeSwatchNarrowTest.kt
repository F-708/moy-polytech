package com.politex

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.ui.components.AppearanceSection
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.applyAppearance
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Плитка пресетов на узком экране 320 dp: длинные названия обязаны влезать. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h640dp")
class ThemeSwatchNarrowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        applyAppearance(AppTheme.COLLEGE, ThemeMode.SYSTEM, SurfaceTone.NATIVE, systemDark = false)
    }

    private fun render(mode: ThemeMode) {
        applyAppearance(AppTheme.COLLEGE, mode, SurfaceTone.NATIVE, systemDark = false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.fillMaxSize().background(ColorBgMain)) {
                    AppearanceSection()
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
    }

    @Test
    fun `320dp - все названия на месте и в два ряда`() {
        render(ThemeMode.LIGHT)
        AppTheme.entries.forEach { composeRule.onNodeWithText(it.title).assertExists() }
        composeRule.onNodeWithTag("theme_swatches").assertExists()
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-theme-tiles-light-320.png")
    }

    @Test
    fun `320dp - тёмная тема - названия не переносятся`() {
        render(ThemeMode.DARK)
        AppTheme.entries.forEach { composeRule.onNodeWithText(it.title).assertExists() }
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-theme-tiles-dark-320.png")
    }
}