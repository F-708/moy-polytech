package com.politex.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateInstallStateTest {
    private val downloading = InstallButtonState.Downloading(0.4f)
    private val downloaded = InstallButtonState.Downloaded("/cache/a.apk")
    private val launched = InstallButtonState.InstallLaunched("/cache/a.apk")
    private val error = InstallButtonState.Error(InstallError.Network)

    @Test
    fun `старт загрузки переводит исходное состояние в загрузку`() {
        assertEquals(
            InstallButtonState.Downloading(0f),
            installReduce(InstallButtonState.IdleFound, InstallEvent.DownloadStarted),
        )
    }

    @Test
    fun `старт загрузки из ошибки повторяет попытку`() {
        assertEquals(InstallButtonState.Downloading(0f), installReduce(error, InstallEvent.DownloadStarted))
    }

    @Test
    fun `старт загрузки игнорируется в активных состояниях`() {
        assertEquals(downloading, installReduce(downloading, InstallEvent.DownloadStarted))
        assertEquals(downloaded, installReduce(downloaded, InstallEvent.DownloadStarted))
        assertEquals(launched, installReduce(launched, InstallEvent.DownloadStarted))
    }

    @Test
    fun `прогресс обновляет состояние загрузки`() {
        assertEquals(
            InstallButtonState.Downloading(0.5f),
            installReduce(downloading, InstallEvent.Progress(50L, 100L)),
        )
    }

    @Test
    fun `прогресс игнорируется вне загрузки`() {
        assertEquals(
            InstallButtonState.IdleFound,
            installReduce(InstallButtonState.IdleFound, InstallEvent.Progress(50L, 100L)),
        )
        assertEquals(downloaded, installReduce(downloaded, InstallEvent.Progress(50L, 100L)))
    }

    @Test
    fun `завершение загрузки открывает установку`() {
        assertEquals(downloaded, installReduce(downloading, InstallEvent.Completed("/cache/a.apk")))
    }

    @Test
    fun `завершение игнорируется вне загрузки`() {
        assertEquals(
            InstallButtonState.IdleFound,
            installReduce(InstallButtonState.IdleFound, InstallEvent.Completed("/cache/a.apk")),
        )
    }

    @Test
    fun `запуск установки из загруженного состояния`() {
        assertEquals(launched, installReduce(downloaded, InstallEvent.InstallStarted))
    }

    @Test
    fun `повторный запуск установки сохраняет путь`() {
        assertEquals(launched, installReduce(launched, InstallEvent.InstallStarted))
    }

    @Test
    fun `запуск установки игнорируется во время загрузки`() {
        assertEquals(downloading, installReduce(downloading, InstallEvent.InstallStarted))
    }

    @Test
    fun `провал из активных состояний даёт ошибку`() {
        val rejection = InstallError.Rejected(ApkRejection.SIGNATURE)
        assertEquals(InstallButtonState.Error(rejection), installReduce(downloading, InstallEvent.Failed(rejection)))
        assertEquals(
            InstallButtonState.Error(InstallError.Network),
            installReduce(downloaded, InstallEvent.Failed(InstallError.Network)),
        )
        assertEquals(
            InstallButtonState.Error(InstallError.Launch),
            installReduce(launched, InstallEvent.Failed(InstallError.Launch)),
        )
    }

    @Test
    fun `провал в исходном состоянии игнорируется`() {
        assertEquals(
            InstallButtonState.IdleFound,
            installReduce(InstallButtonState.IdleFound, InstallEvent.Failed(InstallError.Network)),
        )
    }

    @Test
    fun `сброс возвращает исходное состояние из любого`() {
        listOf(InstallButtonState.IdleFound, downloading, downloaded, launched, error).forEach {
            assertEquals(InstallButtonState.IdleFound, installReduce(it, InstallEvent.Reset))
        }
    }

    @Test
    fun `прогресс считается и округляется`() {
        assertEquals(0f, installProgressOf(0L, 100L), 0.0001f)
        assertEquals(0f, installProgressOf(10L, 0L), 0.0001f)
        assertEquals(0f, installProgressOf(10L, -1L), 0.0001f)
        assertEquals(0.5f, installProgressOf(50L, 100L), 0.0001f)
        assertEquals(0.33f, installProgressOf(333L, 1000L), 0.0001f)
        assertEquals(1f, installProgressOf(150L, 100L), 0.0001f)
    }

    @Test
    fun `сообщения об ошибках соответствуют строкам отклонения`() {
        assertEquals(
            "Обновление отклонено: подпись не совпадает",
            installErrorMessage(InstallError.Rejected(ApkRejection.SIGNATURE)),
        )
        assertEquals(
            "Обновление отклонено: пакет не совпадает",
            installErrorMessage(InstallError.Rejected(ApkRejection.PACKAGE)),
        )
        assertEquals(
            "Обновление отклонено: версия не новее установленной",
            installErrorMessage(InstallError.Rejected(ApkRejection.VERSION)),
        )
        assertEquals("Обновление отклонено: файл повреждён", installErrorMessage(InstallError.Checksum))
        assertEquals("Не удалось скачать обновление", installErrorMessage(InstallError.Network))
        assertEquals("Не удалось открыть установщик", installErrorMessage(InstallError.Launch))
    }

    @Test
    fun `описание кнопки зависит от состояния`() {
        assertEquals("Скачать обновление", installButtonDescription(InstallButtonState.IdleFound))
        assertEquals("Скачать обновление", installButtonDescription(error))
        assertEquals("Загрузка обновления", installButtonDescription(downloading))
        assertEquals("Установить обновление", installButtonDescription(downloaded))
        assertEquals("Установить обновление", installButtonDescription(launched))
    }

    @Test
    fun `тексты статусов загрузки и готовности`() {
        assertEquals("Загрузка обновления…", downloadingStatusText(0f))
        assertEquals("Загрузка обновления… 42%", downloadingStatusText(0.42f))
        assertEquals("Загрузка обновления… 100%", downloadingStatusText(1f))
        assertEquals("Обновление 2.0.1 загружено. Нажмите, чтобы установить", downloadedStatusText("2.0.1"))
        assertEquals("Обновление загружено. Нажмите, чтобы установить", downloadedStatusText(null))
    }
}
