package com.politex.ui.changelog

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class WhatsNewDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = WhatsNewStore.clear(context)

    @After
    fun tearDown() = WhatsNewStore.clear(context)

    @Test
    fun `после обновления диалог показан и все пункты на месте`() {
        WhatsNewStore.recordSeen(context, 20000999)
        val shown = WhatsNewStore.onLaunch(context, 20200999)
        assertEquals(1, shown.size)

        composeRule.setContent {
            MyApplicationTheme {
                WhatsNewDialog(
                    versionName = shown.last().versionName,
                    items = shown.flatMap { it.items },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("whats_new_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("whats_new_title").assertIsDisplayed()
        composeRule.onNodeWithText("ЧТО НОВОГО").assertIsDisplayed()
        composeRule.onNodeWithTag("whats_new_version").assertIsDisplayed()
        composeRule.onNodeWithText("Версия 2.0.1").assertIsDisplayed()
        shown
            .flatMap { it.items }
            .forEach { item ->
                composeRule.onNodeWithText(item.text).assertIsDisplayed()
            }
        composeRule.onAllNodesWithTag("whats_new_item").assertCountEquals(4)
        composeRule.onNodeWithTag("whats_new_continue_btn").assertIsDisplayed()
    }

    @Test
    fun `кнопка Продолжить не ниже 44dp и закрывает диалог`() {
        var dismissed by mutableStateOf(false)
        composeRule.setContent {
            MyApplicationTheme {
                if (!dismissed) {
                    WhatsNewDialog(
                        versionName = "2.0.1",
                        items = WhatsNewEntries.single().items,
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val bounds = composeRule.onNodeWithTag("whats_new_continue_btn").getBoundsInRoot()
        assertTrue("высота кнопки ${bounds.bottom - bounds.top} < 44.dp", bounds.bottom - bounds.top >= 44.dp)

        composeRule.onNodeWithTag("whats_new_continue_btn").performClick()
        composeRule.waitForIdle()

        assertTrue(dismissed)
        composeRule.onAllNodesWithTag("whats_new_dialog").assertCountEquals(0)
    }
}
