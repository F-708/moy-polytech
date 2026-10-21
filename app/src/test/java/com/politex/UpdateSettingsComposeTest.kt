package com.politex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.screens.showUpdateSection
import com.politex.ui.theme.MyApplicationTheme
import com.politex.update.ManualCheckResult
import com.politex.update.UpdateAsset
import com.politex.update.UpdateCheckController
import com.politex.update.UpdateInfo
import com.politex.util.GroupParser
import com.politex.util.TeacherModeStore
import com.politex.util.UpdateChannel
import com.politex.util.UpdateChannelStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class UpdateSettingsComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val group = GroupParser.parse("41О")!!

    @Before
    fun setUp() {
        TeacherModeStore.clear(context)
        UpdateChannelStore.set(context, UpdateChannel.STABLE)
        UpdateCheckController.checkOverride = null
    }

    @After
    fun tearDown() {
        UpdateCheckController.checkOverride = null
        UpdateChannelStore.set(context, UpdateChannel.STABLE)
    }

    private fun renderSettings() {
        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(groupInfo = group, onGroupChanged = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("updates_card"))
        composeRule.waitForIdle()
    }

    private fun waitForStatus(text: String) {
        repeat(100) {
            composeRule.waitForIdle()
            if (composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()) return
            Thread.sleep(50)
        }
        org.junit.Assert.fail("статус не показан: $text")
    }

    @Test
    fun `секция обновлений показывает канал и проверку`() {
        renderSettings()
        composeRule.onNodeWithText("ОБНОВЛЕНИЯ").assertIsDisplayed()
        composeRule.onNodeWithTag("update_channel_card").assertIsDisplayed()
        composeRule.onNodeWithTag("update_channel_stable").assertIsSelected()
        composeRule.onNodeWithTag("update_channel_preview").assertIsNotSelected()
        composeRule.onNodeWithText("Проверить обновления").assertIsDisplayed()
        composeRule.onNodeWithTag("update_status")
            .assertTextEquals("Установлена ${BuildConfig.VERSION_NAME} · канал стабильный")
    }

    @Test
    fun `переключение на превью сохраняет канал и обновляет статус`() {
        renderSettings()
        composeRule.onNodeWithTag("update_channel_preview").performClick()
        composeRule.waitForIdle()
        assertEquals(UpdateChannel.PREVIEW, UpdateChannelStore.channel(context))
        composeRule.onNodeWithTag("update_channel_preview").assertIsSelected()
        composeRule.onNodeWithTag("update_channel_stable").assertIsNotSelected()
        composeRule.onNodeWithTag("update_status")
            .assertTextEquals("Установлена ${BuildConfig.VERSION_NAME} · канал превью")
    }

    @Test
    fun `повторный выбор того же канала не меняет сохранённый`() {
        renderSettings()
        composeRule.onNodeWithTag("update_channel_stable").performClick()
        composeRule.waitForIdle()
        assertEquals(UpdateChannel.STABLE, UpdateChannelStore.channel(context))
    }

    @Test
    fun `ручная проверка показывает нашедшийся результат без сети`() {
        UpdateCheckController.checkOverride = {
            ManualCheckResult.UpdateFound(
                UpdateInfo(
                    tagName = "v2.0.1",
                    versionName = "2.0.1",
                    versionCode = 20001999,
                    notes = "",
                    asset = UpdateAsset("a.apk", "https://example.com/a.apk", 0L),
                ),
            )
        }
        renderSettings()
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Найдено обновление 2.0.1")
    }

    @Test
    fun `ручная проверка показывает свежую версию и ошибку сети`() {
        UpdateCheckController.checkOverride = { ManualCheckResult.Latest }
        renderSettings()
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Вы уже используете последнюю версию")
        composeRule.onNodeWithTag("update_status").assertTextEquals("Вы уже используете последнюю версию")
    }

    @Test
    fun `ручная проверка при недоступной сети сообщает об этом`() {
        UpdateCheckController.checkOverride = { ManualCheckResult.NetworkError }
        renderSettings()
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Нет соединения")
        composeRule.onNodeWithTag("update_status").assertTextEquals("Нет соединения")
    }

    @Test
    fun `секция обновлений включена только для standard`() {
        assertTrue(showUpdateSection("standard"))
        assertFalse(showUpdateSection("admin"))
        assertFalse(showUpdateSection("teacher"))
    }
}
