package com.politex

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.politex.util.SubjectRenameStore
import com.politex.util.subjectDisplayName
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectDisplayNameUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SubjectRenameStore.clearAll(context)
    }

    @After
    fun tearDown() {
        SubjectRenameStore.clearAll(context)
    }

    @Test
    fun `хелпер пересчитывает имя предмета при bump revision без пересоздания композиции`() {
        composeRule.setContent {
            Text(subjectDisplayName("Математика"))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("*Мат.").assertDoesNotExist()

        SubjectRenameStore.setCustomName(context, "Математика", "Мат.")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("*Мат.").assertExists()

        SubjectRenameStore.reset(context, "Математика")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("*Мат.").assertDoesNotExist()
    }
}
