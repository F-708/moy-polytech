package com.politex.content

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupParser
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class ContentSettingsRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val group = GroupParser.parse("41О")!!

    @Before
    fun setUp() {
        ContentPackStore.attach(context)
    }

    @After
    fun tearDown() {
        ContentPackStore.reset(context)
    }

    private fun renderSettings() {
        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(groupInfo = group, onGroupChanged = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("content_pack_update_btn"))
        composeRule.waitForIdle()
    }

    @Test
    fun `строка обновления данных показана со статусом встроенных данных`() {
        renderSettings()
        composeRule.onNodeWithTag("content_pack_update_btn").assertIsDisplayed()
        composeRule.onNodeWithText("Обновить данные колледжа").assertIsDisplayed()
        composeRule.onNodeWithText("Встроенные данные").assertIsDisplayed()
    }

    @Test
    fun `статус показывает версию активного пакета`() {
        val dir = ContentPackStore.packDir(context, 5).apply { mkdirs() }
        ContentPackValidator.REQUIRED_FILES.forEach { File(dir, it).writeText("x") }
        ContentPackStore.activate(context, 5, "2026-10-20T10:00:00Z", 0)
        renderSettings()
        composeRule.onNodeWithText("Пакет v5 от 2026-10-20").assertIsDisplayed()
    }

    @Test
    fun `клик запускает проверку и показывает сообщение`() {
        renderSettings()
        composeRule.onNodeWithTag("content_pack_update_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("content_pack_message").assertIsDisplayed()
        composeRule.onNodeWithText("Проверка обновления данных запущена").assertIsDisplayed()
    }
}
