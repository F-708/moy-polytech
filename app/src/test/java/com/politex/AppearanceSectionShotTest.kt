package com.politex

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.ui.components.AppearanceSection
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.applyAppearance
import com.politex.util.AppThemeStore
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Секция «Тема оформления»: тон доступен в обеих темах, подписей под чипами нет.
 * Скриншоты пишутся только с -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class AppearanceSectionShotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AppThemeStore.saveMode(context, ThemeMode.SYSTEM)
        AppThemeStore.saveTone(context, SurfaceTone.NATIVE)
        AppThemeStore.load(context)
    }

    private fun render(
        mode: ThemeMode,
        tone: SurfaceTone = SurfaceTone.NATIVE,
        expandTone: Boolean = false,
        systemDark: Boolean = false,
    ) {
        AppThemeStore.saveMode(context, mode)
        AppThemeStore.saveTone(context, tone)
        applyAppearance(AppTheme.COLLEGE, mode, tone, systemDark = systemDark)
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
        if (expandTone) {
            composeRule.onNodeWithTag("theme_tone_row").performClick()
            composeRule.mainClock.advanceTimeBy(1_500)
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `светлая тема — тон доступен и раскрывается`() {
        render(ThemeMode.LIGHT, SurfaceTone.SOFT)
        composeRule.onNodeWithTag("theme_mode_segments").assertExists()
        composeRule.onNodeWithTag("theme_tone_row").assertExists()
        composeRule.onNodeWithTag("theme_tone_native").assertDoesNotExist()
        composeRule.onRoot().captureRoboImage("build/screenshots/theme-light-tone-row.png")
    }

    @Test
    fun `светлая тема — чипы тона раскрыты`() {
        render(ThemeMode.LIGHT, SurfaceTone.DEEP, expandTone = true)
        composeRule.onNodeWithTag("theme_tone_native").assertExists()
        composeRule.onNodeWithTag("theme_tone_deep").assertExists()
        composeRule.onRoot().captureRoboImage("build/screenshots/theme-light-tone-expanded.png")
    }

    @Test
    fun `системная тема с ночью — строка тона видна`() {
        render(ThemeMode.SYSTEM, systemDark = true)
        composeRule.onNodeWithTag("theme_tone_row").assertExists()
        composeRule.onRoot().captureRoboImage("build/screenshots/theme-system-dark.png")
    }

    @Test
    fun `тёмная тема — чипы тона без подписей`() {
        render(ThemeMode.DARK, SurfaceTone.SOFT, expandTone = true)
        composeRule.onNodeWithTag("theme_tone_soft").assertExists()
        composeRule.onRoot().captureRoboImage("build/screenshots/theme-dark-tone-expanded.png")
    }
}