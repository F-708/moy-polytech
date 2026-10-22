package com.politex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.update.ApkRejection
import com.politex.update.InstallButtonState
import com.politex.update.ManualCheckResult
import com.politex.update.UpdateAsset
import com.politex.update.UpdateCheckController
import com.politex.update.UpdateInfo
import com.politex.update.UpdateInstallController
import com.politex.util.GroupParser
import com.politex.util.TeacherModeStore
import com.politex.util.UpdateChannel
import com.politex.util.UpdateChannelStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
class UpdateInstallButtonComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val group = GroupParser.parse("41О")!!

    private val foundInfo =
        UpdateInfo(
            tagName = "v2.0.1",
            versionName = "2.0.1",
            versionCode = 20001999,
            notes = "",
            asset = UpdateAsset("a.apk", "https://example.com/a.apk", 100L),
        )

    @Before
    fun setUp() {
        TeacherModeStore.clear(context)
        UpdateChannelStore.set(context, UpdateChannel.STABLE)
        UpdateCheckController.checkOverride = null
        resetInstallController()
    }

    @After
    fun tearDown() {
        resetInstallController()
        UpdateCheckController.checkOverride = null
        UpdateChannelStore.set(context, UpdateChannel.STABLE)
    }

    private fun resetInstallController() {
        UpdateInstallController.downloadOverride = null
        UpdateInstallController.validateOverride = null
        UpdateInstallController.installLaunchOverride = null
        UpdateInstallController.cancelAndReset()
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
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun renderWithFoundUpdate() {
        UpdateCheckController.checkOverride = { ManualCheckResult.UpdateFound(foundInfo) }
        renderSettings()
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Найдено обновление 2.0.1")
    }

    @Test
    fun `кнопка установки скрыта до проверки и видна при найденном обновлении`() {
        UpdateCheckController.checkOverride = { ManualCheckResult.UpdateFound(foundInfo) }
        renderSettings()
        composeRule.onNodeWithTag("update_install_btn").assertDoesNotExist()
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Найдено обновление 2.0.1")
        composeRule.onNodeWithTag("update_install_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("update_install_btn").assertIsEnabled()
    }

    @Test
    fun `кнопка установки скрыта при свежей версии и ошибке сети`() {
        UpdateCheckController.checkOverride = { ManualCheckResult.Latest }
        renderSettings()
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Вы уже используете последнюю версию")
        composeRule.onNodeWithTag("update_install_btn").assertDoesNotExist()

        UpdateCheckController.checkOverride = { ManualCheckResult.NetworkError }
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Нет соединения")
        composeRule.onNodeWithTag("update_install_btn").assertDoesNotExist()
    }

    @Test
    fun `повторная проверка без обновления скрывает кнопку`() {
        renderWithFoundUpdate()
        composeRule.onNodeWithTag("update_install_btn").assertIsDisplayed()
        UpdateCheckController.checkOverride = { ManualCheckResult.Latest }
        composeRule.onNodeWithTag("update_check_btn").performClick()
        waitForStatus("Вы уже используете последнюю версию")
        composeRule.onNodeWithTag("update_install_btn").assertDoesNotExist()
    }

    @Test
    fun `клик запускает загрузку с прогрессом и блокирует кнопку`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        UpdateInstallController.downloadOverride = { _, _, onProgress, _ ->
            onProgress(50L, 100L)
            entered.countDown()
            release.await(10, TimeUnit.SECONDS)
            null
        }
        renderWithFoundUpdate()
        composeRule.onNodeWithTag("update_install_btn").performClick()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        assertTrue(UpdateInstallController.state.value is InstallButtonState.Downloading)
        waitForStatus("Загрузка обновления… 50%")
        composeRule.onNodeWithTag("update_install_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("update_install_btn").assertIsNotEnabled()
        release.countDown()
        waitForStatus("Не удалось скачать обновление")
        composeRule.onNodeWithTag("update_install_btn").assertIsEnabled()
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
    }

    @Test
    fun `загруженное обновление устанавливается по клику`() {
        val file = File(context.cacheDir, "compose-ok.apk").apply { writeBytes(ByteArray(16)) }
        var launchedPath: String? = null
        UpdateInstallController.downloadOverride = { _, _, onProgress, _ ->
            onProgress(100L, 100L)
            file
        }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdateInstallController.installLaunchOverride = { _, target -> launchedPath = target.absolutePath }
        renderWithFoundUpdate()
        composeRule.onNodeWithTag("update_install_btn").performClick()
        waitForStatus("Обновление 2.0.1 загружено. Нажмите, чтобы установить")
        val state = UpdateInstallController.state.value
        assertTrue(state is InstallButtonState.Downloaded)
        assertEquals(file.absolutePath, (state as InstallButtonState.Downloaded).apkPath)
        composeRule.onNodeWithTag("update_install_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(file.absolutePath, launchedPath)
        assertTrue(UpdateInstallController.state.value is InstallButtonState.InstallLaunched)
        waitForStatus("Установщик запущен")
        file.delete()
    }

    @Test
    fun `провал подписи показывает статус и возвращает кнопку`() {
        val file = File(context.cacheDir, "compose-bad.apk").apply { writeBytes(ByteArray(16)) }
        UpdateInstallController.downloadOverride = { _, _, _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.SIGNATURE }
        renderWithFoundUpdate()
        composeRule.onNodeWithTag("update_install_btn").performClick()
        waitForStatus("Обновление отклонено: подпись не совпадает")
        assertFalse(file.exists())
        composeRule.onNodeWithTag("update_install_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("update_install_btn").assertIsEnabled()
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
    }

    @Test
    fun `смена канала во время загрузки отменяет её`() {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val sawCancel = AtomicBoolean(false)
        UpdateInstallController.downloadOverride = { _, _, onProgress, isCancelled ->
            onProgress(20L, 100L)
            entered.countDown()
            val deadline = System.currentTimeMillis() + 10_000
            while (!isCancelled() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            sawCancel.set(isCancelled())
            cancelled.countDown()
            null
        }
        renderWithFoundUpdate()
        composeRule.onNodeWithTag("update_install_btn").performClick()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        composeRule.onNodeWithTag("update_channel_preview").performClick()
        composeRule.waitForIdle()
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
        composeRule.onNodeWithTag("update_install_btn").assertDoesNotExist()
        waitForStatus("Установлена ${BuildConfig.VERSION_NAME} · канал превью")
        assertTrue(cancelled.await(10, TimeUnit.SECONDS))
        assertTrue(sawCancel.get())
        Thread.sleep(150)
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
    }
}
