package com.politex

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.CollegeScreen
import com.politex.ui.teacher.TeacherMoreScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.update.ApkRejection
import com.politex.update.InstallButtonState
import com.politex.update.UpdateAsset
import com.politex.update.UpdateDownloader
import com.politex.update.UpdateInfo
import com.politex.update.UpdateInstallController
import com.politex.util.GroupParser
import com.politex.util.UpdateChannel
import com.politex.util.UpdateChannelStore
import com.politex.util.UpdatePendingStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xhdpi")
class UpdateMoreButtonComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val group = GroupParser.parse("41О")!!

    private val foundInfo =
        UpdateInfo(
            tagName = "v2.0.1",
            versionName = "2.0.1",
            versionCode = BuildConfig.VERSION_CODE + 1000,
            notes = "",
            asset = UpdateAsset("a.apk", "https://example.com/a.apk", 100L),
        )

    @Before
    fun setUp() {
        UpdateChannelStore.set(context, UpdateChannel.STABLE)
        resetController()
    }

    @After
    fun tearDown() = resetController()

    private fun resetController() {
        UpdateInstallController.downloadOverride = null
        UpdateInstallController.validateOverride = null
        UpdateInstallController.installLaunchOverride = null
        UpdateInstallController.cancelAndReset(context)
    }

    private fun renderTeacherMore() {
        composeRule.setContent {
            MyApplicationTheme {
                TeacherMoreScreen(profile = null)
            }
        }
        composeRule.waitForIdle()
    }

    private fun renderStudentOther() {
        composeRule.setContent {
            MyApplicationTheme {
                CollegeScreen(groupInfo = group)
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollToUpdateCard() {
        composeRule.onNodeWithTag("more_settings").performScrollTo()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("more_update_btn").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("more_update_btn").performScrollTo()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `карточка видна в учительском Ещё при найденном обновлении`() {
        UpdateInstallController.rememberFound(context, foundInfo)
        renderTeacherMore()
        scrollToUpdateCard()
        composeRule.onNodeWithTag("more_update_btn").assertIsDisplayed()
        composeRule.onNodeWithText("ДОСТУПНО ОБНОВЛЕНИЕ").assertIsDisplayed()
        composeRule.onNodeWithText("Версия 2.0.1 · нажмите, чтобы скачать").assertIsDisplayed()
    }

    @Test
    fun `карточка видна в студенческом Другое при найденном обновлении`() {
        UpdateInstallController.rememberFound(context, foundInfo)
        renderStudentOther()
        composeRule.onNodeWithText("Настройки").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("more_update_btn").assertIsDisplayed()
        composeRule.onNodeWithText("ДОСТУПНО ОБНОВЛЕНИЕ").assertIsDisplayed()
    }

    @Test
    fun `карточка скрыта без найденного обновления`() {
        renderTeacherMore()
        composeRule.onNodeWithTag("more_update_btn").assertDoesNotExist()
        composeRule.onNodeWithText("ДОСТУПНО ОБНОВЛЕНИЕ").assertDoesNotExist()
    }

    @Test
    fun `тап по карточке запускает загрузку с прогрессом и блокирует её`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        UpdateInstallController.downloadOverride = { _, _, onProgress, _ ->
            onProgress(40L, 100L)
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            null
        }
        UpdateInstallController.rememberFound(context, foundInfo)
        renderTeacherMore()
        scrollToUpdateCard()
        composeRule.onNodeWithTag("more_update_btn").performClick()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        waitForText("Загрузка обновления… 40%")
        composeRule.onNodeWithTag("more_update_btn").assertIsNotEnabled()
        release.countDown()
    }

    @Test
    fun `скачанный apk переживает перезапуск и устанавливается по тапу`() {
        val file =
            UpdateDownloader.targetFile(context, foundInfo.versionName).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(16))
            }
        var launchedPath: String? = null
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdateInstallController.installLaunchOverride = { _, target -> launchedPath = target.absolutePath }
        UpdatePendingStore.save(context, foundInfo)

        renderTeacherMore()
        scrollToUpdateCard()

        waitForText("Обновление 2.0.1 загружено. Нажмите, чтобы установить")
        composeRule.onNodeWithTag("more_update_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(file.absolutePath, launchedPath)
        assertTrue(UpdateInstallController.state.value is InstallButtonState.InstallLaunched)
        file.delete()
    }
}
