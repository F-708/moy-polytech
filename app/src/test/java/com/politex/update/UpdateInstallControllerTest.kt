package com.politex.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.BuildConfig
import com.politex.util.UpdatePendingStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateInstallControllerTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val info =
        UpdateInfo(
            tagName = "v2.0.1",
            versionName = "2.0.1",
            versionCode = BuildConfig.VERSION_CODE + 1000,
            notes = "",
            asset = UpdateAsset("a.apk", "https://example.com/a.apk", 100L),
        )

    @Before
    fun setUp() = resetController()

    @After
    fun tearDown() = resetController()

    private fun resetController() {
        UpdateInstallController.downloadOverride = null
        UpdateInstallController.validateOverride = null
        UpdateInstallController.installLaunchOverride = null
        AppUpdateWorker.downloadOverride = null
        UpdateInstallController.cancelAndReset(context)
    }

    private fun awaitState(predicate: (InstallButtonState) -> Boolean): InstallButtonState {
        repeat(200) {
            val current = UpdateInstallController.state.value
            if (predicate(current)) return current
            Thread.sleep(25)
        }
        throw AssertionError("состояние не достигнуто: ${UpdateInstallController.state.value}")
    }

    private fun tempApk(name: String): File = File(context.cacheDir, name).apply { writeBytes(ByteArray(16)) }

    @Test
    fun `успешная загрузка доходит до состояния готовности`() {
        val file = tempApk("ctl-ok.apk")
        UpdateInstallController.downloadOverride = { _, _, onProgress, _ ->
            onProgress(50L, 100L)
            onProgress(100L, 100L)
            file
        }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdateInstallController.startDownload(context, info)
        val state = awaitState { it is InstallButtonState.Downloaded }
        assertEquals(file.absolutePath, (state as InstallButtonState.Downloaded).apkPath)
        file.delete()
    }

    @Test
    fun `запуск установки передаёт путь в лаунчер`() {
        val file = tempApk("ctl-launch.apk")
        var launchedPath: String? = null
        UpdateInstallController.downloadOverride = { _, _, _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdateInstallController.installLaunchOverride = { _, target -> launchedPath = target.absolutePath }
        UpdateInstallController.startDownload(context, info)
        awaitState { it is InstallButtonState.Downloaded }
        UpdateInstallController.launchInstall(context)
        val state = awaitState { it is InstallButtonState.InstallLaunched }
        assertEquals(file.absolutePath, launchedPath)
        assertEquals(file.absolutePath, (state as InstallButtonState.InstallLaunched).apkPath)
        file.delete()
    }

    @Test
    fun `повторный запуск установки снова вызывает лаунчер`() {
        val file = tempApk("ctl-relaunch.apk")
        val launches = AtomicInteger(0)
        UpdateInstallController.downloadOverride = { _, _, _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdateInstallController.installLaunchOverride = { _, _ -> launches.incrementAndGet() }
        UpdateInstallController.startDownload(context, info)
        awaitState { it is InstallButtonState.Downloaded }
        UpdateInstallController.onButtonTap(context, info)
        awaitState { it is InstallButtonState.InstallLaunched }
        UpdateInstallController.onButtonTap(context, info)
        assertEquals(2, launches.get())
        assertTrue(UpdateInstallController.state.value is InstallButtonState.InstallLaunched)
        file.delete()
    }

    @Test
    fun `провал валидации подписи даёт ошибку и сброс`() {
        val file = tempApk("ctl-bad-signature.apk")
        UpdateInstallController.downloadOverride = { _, _, _, _ -> file }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.SIGNATURE }
        UpdateInstallController.startDownload(context, info)
        val state = awaitState { it is InstallButtonState.Error }
        assertEquals(InstallError.Rejected(ApkRejection.SIGNATURE), (state as InstallButtonState.Error).cause)
        assertFalse(file.exists())
        UpdateInstallController.acknowledgeError()
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
    }

    @Test
    fun `провал сети даёт ошибку сети`() {
        UpdateInstallController.downloadOverride = { _, _, _, _ -> null }
        UpdateInstallController.startDownload(context, info)
        val state = awaitState { it is InstallButtonState.Error }
        assertEquals(InstallError.Network, (state as InstallButtonState.Error).cause)
    }

    @Test
    fun `несовпадение контрольной суммы отклоняет файл`() {
        val file = tempApk("ctl-checksum.apk")
        UpdateInstallController.downloadOverride = { _, _, _, _ -> file }
        UpdateInstallController.startDownload(context, info.copy(sha256 = "deadbeef"))
        val state = awaitState { it is InstallButtonState.Error }
        assertEquals(InstallError.Checksum, (state as InstallButtonState.Error).cause)
        assertFalse(file.exists())
    }

    @Test
    fun `отмена во время загрузки сбрасывает состояние без ошибки`() {
        val entered = CountDownLatch(1)
        val sawCancel = AtomicBoolean(false)
        UpdateInstallController.downloadOverride = { _, _, onProgress, isCancelled ->
            onProgress(10L, 100L)
            entered.countDown()
            val deadline = System.currentTimeMillis() + 5000
            while (!isCancelled() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            sawCancel.set(isCancelled())
            null
        }
        UpdateInstallController.startDownload(context, info)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertTrue(UpdateInstallController.state.value is InstallButtonState.Downloading)
        UpdateInstallController.cancelAndReset(context)
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
        val deadline = System.currentTimeMillis() + 5000
        while (!sawCancel.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        assertTrue(sawCancel.get())
        Thread.sleep(150)
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
    }

    @Test
    fun `повторный старт во время загрузки игнорируется`() {
        val calls = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        UpdateInstallController.downloadOverride = { _, _, _, _ ->
            calls.incrementAndGet()
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            null
        }
        UpdateInstallController.startDownload(context, info)
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        UpdateInstallController.startDownload(context, info)
        UpdateInstallController.onButtonTap(context, info)
        release.countDown()
        awaitState { it is InstallButtonState.Error }
        assertEquals(1, calls.get())
    }

    @Test
    fun `тап без найденного обновления ничего не делает`() {
        UpdateInstallController.onButtonTap(context, null)
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
    }

    @Test
    fun `сброс очищает найденное обновление и prefs`() {
        UpdateInstallController.rememberFound(context, info)
        assertEquals(info, UpdateInstallController.foundUpdate.value)
        assertEquals(info, UpdatePendingStore.load(context))
        UpdateInstallController.cancelAndReset(context)
        assertNull(UpdateInstallController.foundUpdate.value)
        assertNull(UpdatePendingStore.load(context))
    }

    @Test
    fun `restore восстанавливает найденное обновление из prefs без проверки`() {
        UpdatePendingStore.save(context, info)
        UpdateInstallController.restore(context)
        assertEquals(info, UpdateInstallController.foundUpdate.value)
        assertEquals(InstallButtonState.IdleFound, UpdateInstallController.state.value)
    }

    @Test
    fun `restore игнорирует и чистит pending с кодом не новее установленного`() {
        UpdatePendingStore.save(context, info.copy(versionCode = 1))
        UpdateInstallController.restore(context)
        assertNull(UpdateInstallController.foundUpdate.value)
        assertNull(UpdatePendingStore.load(context))
    }

    @Test
    fun `restore с валидным скачанным apk сразу даёт состояние готовности`() {
        val file = UpdateDownloader.targetFile(context, info.versionName).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(16))
        }
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdatePendingStore.save(context, info)
        UpdateInstallController.restore(context)
        val state = awaitState { it is InstallButtonState.Downloaded }
        assertEquals(file.absolutePath, (state as InstallButtonState.Downloaded).apkPath)
        file.delete()
    }

    @Test
    fun `старт загрузки переиспользует валидный файл без сети`() {
        val file = UpdateDownloader.targetFile(context, info.versionName).apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(16))
        }
        var networkCalled = false
        UpdateInstallController.validateOverride = { _, _ -> ApkRejection.NONE }
        UpdateInstallController.downloadOverride = { _, _, _, _ ->
            networkCalled = true
            null
        }
        UpdateInstallController.startDownload(context, info)
        val state = awaitState { it is InstallButtonState.Downloaded }
        assertEquals(file.absolutePath, (state as InstallButtonState.Downloaded).apkPath)
        assertFalse(networkCalled)
        file.delete()
    }

    @Test
    fun `результат воркера перенимается контроллером`() {
        val file = tempApk("ctl-adopt.apk")
        UpdateInstallController.adoptWorkerResult(context, info, file)
        assertEquals(info, UpdateInstallController.foundUpdate.value)
        val state = awaitState { it is InstallButtonState.Downloaded }
        assertEquals(file.absolutePath, (state as InstallButtonState.Downloaded).apkPath)
        assertEquals(info, UpdatePendingStore.load(context))
        file.delete()
    }
}
