package com.politex

import android.content.Context
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.PulseActive
import com.politex.ui.teacher.TeacherNav
import com.politex.ui.teacher.TeacherTheme
import com.politex.update.UpdateAsset
import com.politex.update.UpdateInfo
import com.politex.update.UpdateInstallController
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class UpdateTabPulseTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val foundInfo =
        UpdateInfo(
            tagName = "v2.0.1",
            versionName = "2.0.1",
            versionCode = 20001999,
            notes = "",
            asset = UpdateAsset("a.apk", "https://example.com/a.apk", 100L),
        )

    @Before
    fun setUp() = UpdateInstallController.cancelAndReset(context)

    @After
    fun tearDown() = UpdateInstallController.cancelAndReset(context)

    private fun waitForTag(tag: String) {
        repeat(100) {
            composeRule.mainClock.advanceTimeBy(100)
            composeRule.waitForIdle()
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
        }
        org.junit.Assert.fail("тег не найден: $tag")
    }

    @Test
    fun `вкладка Ещё пульсирует пока есть найденное обновление`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { TeacherTheme { TeacherNav(profile = null) } }
        waitForTag("teacher_tab_more")

        composeRule
            .onNodeWithTag("teacher_tab_more", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(PulseActive))

        UpdateInstallController.rememberFound(context, foundInfo)
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("teacher_tab_more", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(PulseActive, true))
        composeRule
            .onNodeWithTag("teacher_tab_schedule", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(PulseActive))

        UpdateInstallController.cancelAndReset(context)
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("teacher_tab_more", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(PulseActive))
    }

    @Test
    fun `студенческая навигация подключена к пульсу вкладки Другое`() {
        val src =
            sequenceOf(
                File("src/main/java/com/politex/ui/screens/MainScreen.kt"),
                File("app/src/main/java/com/politex/ui/screens/MainScreen.kt"),
            ).first { it.exists() }.readText()
        assertTrue(src.contains("pulseHalo(tab == AppTab.COLLEGE && updatePending != null)"))
        assertTrue(src.contains("main_tab_"))
        assertTrue(src.contains("UpdateInstallController.restore(context)"))
        assertTrue(src.contains("UpdateInstallController.foundUpdate.collectAsState()"))
    }
}
