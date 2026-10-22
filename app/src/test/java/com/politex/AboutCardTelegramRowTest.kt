package com.politex

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupParser
import com.politex.util.TeacherModeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class AboutCardTelegramRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val group = GroupParser.parse("41О")!!
    private var hostContext: Context? = null

    @Before
    fun setUp() {
        TeacherModeStore.clear(context)
    }

    private fun renderSettings() {
        composeRule.setContent {
            hostContext = LocalContext.current
            MyApplicationTheme {
                SettingsScreen(groupInfo = group, onGroupChanged = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_telegram_row"))
        composeRule.waitForIdle()
    }

    @Test
    fun `строка телеграм показана и кликабельна`() {
        renderSettings()
        composeRule.onNodeWithTag("settings_telegram_row").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_telegram_row").assertHasClickAction()
    }

    @Test
    fun `клик по строке телеграм открывает ссылку на канал`() {
        renderSettings()
        composeRule.onNodeWithTag("settings_telegram_row").performClick()
        composeRule.waitForIdle()
        val started = shadowOf(hostContext as Activity).nextStartedActivity
        assertNotNull(started)
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://t.me/Betterthannothing12", started.data.toString())
    }
}
