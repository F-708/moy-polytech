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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SWATCH_PAD = 14f
private const val SWATCH_GAP = 8f
private const val INNER_PAD = 8f

/** Плитка пресетов: 8 названий по четыре в ряд — два горизонтальных ряда. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class ThemeSwatchLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        AppThemeStore.saveMode(context, ThemeMode.SYSTEM)
        AppThemeStore.saveTone(context, SurfaceTone.NATIVE)
        applyAppearance(AppTheme.COLLEGE, ThemeMode.SYSTEM, SurfaceTone.NATIVE, systemDark = false)
    }

    private fun render(mode: ThemeMode) {
        AppThemeStore.saveMode(context, mode)
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
    fun `все восемь названий присутствуют и лежат в плитках`() {
        render(ThemeMode.LIGHT)
        composeRule.onNodeWithTag("theme_swatches").assertExists()
        AppTheme.entries.forEach { theme ->
            composeRule.onNodeWithText(theme.title).assertExists()
        }
    }

    @Test
    fun `плитки идут по четыре в ряд - восемь тем дают два ряда`() {
        render(ThemeMode.LIGHT)
        val swatches =
            AppTheme.entries.map { theme ->
                composeRule.onNodeWithTag("theme_swatch_${theme.id}").fetchSemanticsNode()
            }
        val rows = swatches.groupBy { it.boundsInRoot.top.toInt() }
        assertTrue("ожидались 2 ряда, получено ${rows.size}: ${rows.keys.sorted()}", rows.size == 2)
        val perRow = rows.values.map { it.size }.sorted()
        assertTrue("раскладка 4+4, получено $perRow", perRow == listOf(4, 4))
    }

    @Test
    fun `самое длинное название не шире текстовой области плитки`() {
        var measurer: TextMeasurer? = null
        composeRule.setContent {
            MyApplicationTheme { measurer = rememberTextMeasurer() }
        }
        composeRule.waitForIdle()
        val text = measurer ?: error("TextMeasurer не создан")
        val density = composeRule.density
        val availablePx =
            with(density) {
                ((360f - 2 * SWATCH_PAD - 3 * SWATCH_GAP) / 4f).dp.toPx() - INNER_PAD.dp.toPx()
            }
        AppTheme.entries.forEach { theme ->
            listOf(FontWeight.Medium, FontWeight.Bold).forEach { weight ->
                val layout =
                    text.measure(
                        text = theme.title,
                        style =
                        TextStyle(
                            fontWeight = weight,
                            fontSize = 10.sp,
                            letterSpacing = (-0.3).sp,
                        ),
                    )
                assertTrue(
                    "«${theme.title}» ($weight): ${layout.size.width} px > $availablePx px",
                    layout.size.width <= availablePx,
                )
            }
        }
    }

    @Test
    fun `скриншот плитки тем - светлая`() {
        render(ThemeMode.LIGHT)
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-theme-tiles-light-360.png")
    }

    @Test
    fun `скриншот плитки тем - тёмная`() {
        render(ThemeMode.DARK)
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-theme-tiles-dark-360.png")
    }
}