package com.politex.content

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupParser
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("backup_save_btn"))
        composeRule.waitForIdle()
    }

    @Test
    fun `секция данных не показывает строку контент-пакета`() {
        renderSettings()
        composeRule.onNodeWithTag("backup_save_btn").assertIsDisplayed()
        composeRule.onAllNodes(hasTestTag("content_pack_status_row")).assertCountEquals(0)
        composeRule.onAllNodes(hasTestTag("content_pack_update_btn")).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Данные колледжа")).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Встроенные данные")).assertCountEquals(0)
    }

    @Test
    fun `версия активного пакета не показывается в настройках`() {
        val dir = ContentPackStore.packDir(context, 5).apply { mkdirs() }
        ContentPackValidator.REQUIRED_FILES.forEach { File(dir, it).writeText("x") }
        ContentPackStore.activate(context, 5, "2026-10-20T10:00:00Z", 0)
        renderSettings()
        composeRule.onAllNodes(hasText("Пакет v5 от 2026-10-20")).assertCountEquals(0)
        composeRule.onAllNodes(hasText("Пакет v5")).assertCountEquals(0)
        composeRule.onAllNodes(hasTestTag("content_pack_status_row")).assertCountEquals(0)
    }

    @Test
    fun `резервные копии остались в секции данных`() {
        renderSettings()
        composeRule.onNodeWithTag("backup_save_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_restore_btn").assertIsDisplayed()
    }
}
