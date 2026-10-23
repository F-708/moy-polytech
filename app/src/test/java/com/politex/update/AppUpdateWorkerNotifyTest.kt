package com.politex.update

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppUpdateWorkerNotifyTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val info =
        UpdateInfo(
            tagName = "v2.0.1",
            versionName = "2.0.1",
            versionCode = 20001999,
            notes = "",
            asset = UpdateAsset("a.apk", "https://example.com/a.apk", 100L),
        )

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences("mpk_update_prefs", Context.MODE_PRIVATE)

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)!!

    @Before
    fun setUp() {
        resetAll()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = resetAll()

    private fun resetAll() {
        UpdateInstallController.downloadOverride = null
        UpdateInstallController.validateOverride = null
        UpdateInstallController.installLaunchOverride = null
        AppUpdateWorker.downloadOverride = null
        UpdateInstallController.cancelAndReset(context)
    }

    private fun postedTitles(): List<String> = shadowOf(notificationManager).allNotifications.map {
        it.extras.getString(Notification.EXTRA_TITLE).orEmpty()
    }

    private fun awaitState(predicate: (InstallButtonState) -> Boolean): InstallButtonState {
        repeat(200) {
            val current = UpdateInstallController.state.value
            if (predicate(current)) return current
            Thread.sleep(25)
        }
        throw AssertionError("состояние не достигнуто: ${UpdateInstallController.state.value}")
    }

    @Test
    fun `найдено обновление - уведомление постится и версия запоминается`() {
        val file = File(context.cacheDir, "worker-ok.apk").apply { writeBytes(ByteArray(16)) }
        AppUpdateWorker.downloadOverride = { _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }

        val result = AppUpdateWorker.processFoundUpdate(context, info)

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(postedTitles().any { it.contains("Доступно обновление 2.0.1") })
        assertEquals(info.versionCode, prefs.getInt("notified_version_code", 0))
        assertEquals(info.versionName, prefs.getString("notified_version_name", null))
        assertEquals(info, UpdateInstallController.foundUpdate.value)
        val state = UpdateInstallController.state.value
        assertTrue(state is InstallButtonState.Downloaded)
        assertEquals(file.absolutePath, (state as InstallButtonState.Downloaded).apkPath)
        file.delete()
    }

    @Test
    fun `анти-спам - повтор для того же versionCode не уведомляет`() {
        val file = File(context.cacheDir, "worker-antispam.apk").apply { writeBytes(ByteArray(16)) }
        AppUpdateWorker.downloadOverride = { _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        AppUpdateWorker.processFoundUpdate(context, info)
        val before = postedTitles().size

        val result = AppUpdateWorker.processFoundUpdate(context, info)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(before, postedTitles().size)
        file.delete()
    }

    @Test
    fun `анти-спам предикат - по коду и по легаси-имени`() {
        assertFalse(AppUpdateWorker.alreadyNotified(prefs, info))
        AppUpdateWorker.remember(prefs, info)
        assertTrue(AppUpdateWorker.alreadyNotified(prefs, info))
        assertTrue(AppUpdateWorker.alreadyNotified(prefs, info.copy(versionName = "2.0.1+hotfix")))
        assertFalse(AppUpdateWorker.alreadyNotified(prefs, info.copy(versionCode = 20002999, versionName = "2.0.2")))
        prefs.edit().clear().putString("notified_version_name", info.versionName).apply()
        assertTrue(AppUpdateWorker.alreadyNotified(prefs, info))
    }

    @Test
    fun `сбой скачивания - retry без уведомления`() {
        AppUpdateWorker.downloadOverride = { _, _ -> null }
        val result = AppUpdateWorker.processFoundUpdate(context, info)
        assertTrue(result is ListenableWorker.Result.Retry)
        assertTrue(postedTitles().isEmpty())
    }

    @Test
    fun `контрольная сумма не совпала - retry и файл удалён`() {
        val file = File(context.cacheDir, "worker-checksum.apk").apply { writeBytes(ByteArray(16)) }
        AppUpdateWorker.downloadOverride = { _, _ -> file }
        val result = AppUpdateWorker.processFoundUpdate(context, info.copy(sha256 = "deadbeef"))
        assertTrue(result is ListenableWorker.Result.Retry)
        assertFalse(file.exists())
        assertTrue(postedTitles().isEmpty())
    }

    @Test
    fun `подпись не совпала - уведомление об отклонении и очистка pending`() {
        val file = File(context.cacheDir, "worker-signature.apk").apply { writeBytes(ByteArray(16)) }
        AppUpdateWorker.downloadOverride = { _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.SIGNATURE }
        val result = AppUpdateWorker.processFoundUpdate(context, info)
        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(postedTitles().any { it == "Обновление отклонено" })
        assertFalse(postedTitles().any { it.contains("Доступно обновление") })
        assertFalse(file.exists())
        assertNull(UpdateInstallController.foundUpdate.value)
        assertNull(com.politex.util.UpdatePendingStore.load(context))
    }

    @Test
    fun `без POST_NOTIFICATIONS уведомление не постится но версия запоминается`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val file = File(context.cacheDir, "worker-noperm.apk").apply { writeBytes(ByteArray(16)) }
        AppUpdateWorker.downloadOverride = { _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        val result = AppUpdateWorker.processFoundUpdate(context, info)
        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(postedTitles().isEmpty())
        assertEquals(info.versionCode, prefs.getInt("notified_version_code", 0))
        file.delete()
    }

    @Test
    fun `пользователь уже скачивает - воркер уступает через retry`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        UpdateInstallController.downloadOverride = { _, _, _, _ ->
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            null
        }
        UpdateInstallController.rememberFound(context, info)
        UpdateInstallController.startDownload(context, info)
        assertTrue(entered.await(5, TimeUnit.SECONDS))

        val result = AppUpdateWorker.processFoundUpdate(context, info)

        assertTrue(result is ListenableWorker.Result.Retry)
        assertTrue(postedTitles().isEmpty())
        release.countDown()
    }

    @Test
    fun `пользователь уже скачал - воркер не скачивает и не уведомляет`() {
        val file = File(context.cacheDir, "worker-userdone.apk").apply { writeBytes(ByteArray(16)) }
        UpdateInstallController.downloadOverride = { _, _, _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdateInstallController.rememberFound(context, info)
        UpdateInstallController.startDownload(context, info)
        awaitState { it is InstallButtonState.Downloaded }
        AppUpdateWorker.downloadOverride = { _, _ ->
            throw AssertionError("воркер не должен скачивать повторно")
        }

        val result = AppUpdateWorker.processFoundUpdate(context, info)

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(postedTitles().isEmpty())
        assertEquals(info.versionCode, prefs.getInt("notified_version_code", 0))
        file.delete()
    }

    @Test
    fun `уже скачанный валидный файл переиспользуется без сети`() {
        val file =
            UpdateDownloader.targetFile(context, info.versionName).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(16))
            }
        AppUpdateWorker.downloadOverride = { _, _ ->
            throw AssertionError("сеть не должна использоваться")
        }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }

        val result = AppUpdateWorker.processFoundUpdate(context, info)

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(postedTitles().any { it.contains("Доступно обновление 2.0.1") })
        file.delete()
    }
}
