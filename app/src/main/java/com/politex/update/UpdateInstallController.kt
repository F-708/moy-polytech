package com.politex.update

import android.content.Context
import android.content.Intent
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface InstallButtonState {
    data object IdleFound : InstallButtonState

    data class Downloading(
        val progress: Float,
    ) : InstallButtonState

    data class Downloaded(
        val apkPath: String,
    ) : InstallButtonState

    data class InstallLaunched(
        val apkPath: String,
    ) : InstallButtonState

    data class Error(
        val cause: InstallError,
    ) : InstallButtonState
}

sealed interface InstallError {
    data object Network : InstallError

    data object Checksum : InstallError

    data object Launch : InstallError

    data class Rejected(
        val rejection: ApkRejection,
    ) : InstallError
}

sealed interface InstallEvent {
    data object DownloadStarted : InstallEvent

    data class Progress(
        val readBytes: Long,
        val totalBytes: Long,
    ) : InstallEvent

    data class Completed(
        val apkPath: String,
    ) : InstallEvent

    data object InstallStarted : InstallEvent

    data class Failed(
        val cause: InstallError,
    ) : InstallEvent

    data object Reset : InstallEvent
}

internal fun installReduce(
    state: InstallButtonState,
    event: InstallEvent,
): InstallButtonState =
    when (event) {
        InstallEvent.Reset -> InstallButtonState.IdleFound
        is InstallEvent.Failed ->
            when (state) {
                is InstallButtonState.Downloading,
                is InstallButtonState.Downloaded,
                is InstallButtonState.InstallLaunched,
                -> InstallButtonState.Error(event.cause)
                else -> state
            }
        InstallEvent.DownloadStarted ->
            when (state) {
                InstallButtonState.IdleFound,
                is InstallButtonState.Error,
                -> InstallButtonState.Downloading(0f)
                else -> state
            }
        is InstallEvent.Progress ->
            when (state) {
                is InstallButtonState.Downloading ->
                    InstallButtonState.Downloading(installProgressOf(event.readBytes, event.totalBytes))
                else -> state
            }
        is InstallEvent.Completed ->
            when (state) {
                is InstallButtonState.Downloading -> InstallButtonState.Downloaded(event.apkPath)
                else -> state
            }
        InstallEvent.InstallStarted ->
            when (state) {
                is InstallButtonState.Downloaded -> InstallButtonState.InstallLaunched(state.apkPath)
                else -> state
            }
    }

internal fun installProgressOf(
    readBytes: Long,
    totalBytes: Long,
): Float {
    if (readBytes <= 0L || totalBytes <= 0L) return 0f
    val ratio = (readBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    return (ratio * 100f).roundToInt() / 100f
}

internal fun installErrorMessage(cause: InstallError): String =
    when (cause) {
        InstallError.Network -> "Не удалось скачать обновление"
        InstallError.Checksum -> "Обновление отклонено: файл повреждён"
        InstallError.Launch -> "Не удалось открыть установщик"
        is InstallError.Rejected -> UpdateDownloader.rejectionMessage(cause.rejection)
    }

internal fun installButtonDescription(state: InstallButtonState): String =
    when (state) {
        is InstallButtonState.Downloading -> "Загрузка обновления"
        is InstallButtonState.Downloaded,
        is InstallButtonState.InstallLaunched,
        -> "Установить обновление"
        else -> "Скачать обновление"
    }

internal fun downloadingStatusText(progress: Float): String {
    val percent = (progress.coerceIn(0f, 1f) * 100f).roundToInt()
    return if (percent > 0) "Загрузка обновления… $percent%" else "Загрузка обновления…"
}

internal fun downloadedStatusText(versionName: String?): String =
    if (versionName.isNullOrBlank()) {
        "Обновление загружено. Нажмите, чтобы установить"
    } else {
        "Обновление $versionName загружено. Нажмите, чтобы установить"
    }

object UpdateInstallController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow<InstallButtonState>(InstallButtonState.IdleFound)
    val state: StateFlow<InstallButtonState> = mutableState.asStateFlow()

    private var activeJob: Job? = null
    private var activeCancel: AtomicBoolean? = null

    @Volatile
    var foundUpdate: UpdateInfo? = null
        private set

    internal var downloadOverride: ((Context, UpdateInfo, (Long, Long) -> Unit, () -> Boolean) -> File?)? = null
    internal var validateOverride: ((Context, File) -> ApkRejection)? = null
    internal var installLaunchOverride: ((Context, File) -> Unit)? = null

    fun rememberFound(info: UpdateInfo?) {
        foundUpdate = info
    }

    fun onButtonTap(
        context: Context,
        info: UpdateInfo?,
    ) {
        when (mutableState.value) {
            is InstallButtonState.Downloading -> Unit
            is InstallButtonState.Downloaded,
            is InstallButtonState.InstallLaunched,
            -> launchInstall(context)
            else -> {
                val target = info ?: foundUpdate ?: return
                startDownload(context, target)
            }
        }
    }

    fun startDownload(
        context: Context,
        info: UpdateInfo,
    ) {
        val current = mutableState.value
        if (current !is InstallButtonState.IdleFound && current !is InstallButtonState.Error) return
        activeCancel?.set(true)
        activeJob?.cancel()
        publish(InstallEvent.DownloadStarted)
        val cancelFlag = AtomicBoolean(false)
        activeCancel = cancelFlag
        val appContext = context.applicationContext
        activeJob =
            scope.launch {
                val onProgress: (Long, Long) -> Unit = { read, total -> publish(InstallEvent.Progress(read, total)) }
                val file =
                    runCatching {
                        downloadOverride
                            ?.invoke(appContext, info, onProgress) { cancelFlag.get() }
                            ?: UpdateDownloader.download(appContext, info.asset, info.versionName, onProgress) {
                                cancelFlag.get()
                            }
                    }.getOrNull()
                if (cancelFlag.get()) return@launch
                publish(evaluateDownload(appContext, file, info))
            }
    }

    fun launchInstall(context: Context) {
        val path =
            when (val current = mutableState.value) {
                is InstallButtonState.Downloaded -> current.apkPath
                is InstallButtonState.InstallLaunched -> current.apkPath
                else -> return
            }
        val file = File(path)
        val launched =
            runCatching {
                installLaunchOverride?.invoke(context, file)
                    ?: context.startActivity(
                        Intent(context, UpdateInstallActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(UpdateInstallActivity.EXTRA_APK_PATH, file.absolutePath),
                    )
            }.isSuccess
        publish(if (launched) InstallEvent.InstallStarted else InstallEvent.Failed(InstallError.Launch))
    }

    fun cancelAndReset() {
        activeCancel?.set(true)
        activeCancel = null
        activeJob?.cancel()
        activeJob = null
        foundUpdate = null
        publish(InstallEvent.Reset)
    }

    fun acknowledgeError() {
        if (mutableState.value is InstallButtonState.Error) publish(InstallEvent.Reset)
    }

    private fun evaluateDownload(
        context: Context,
        file: File?,
        info: UpdateInfo,
    ): InstallEvent {
        if (file == null) return InstallEvent.Failed(InstallError.Network)
        if (!UpdateDownloader.matchesChecksum(file, info.sha256)) {
            file.delete()
            return InstallEvent.Failed(InstallError.Checksum)
        }
        val rejection = validateOverride?.invoke(context, file) ?: UpdateDownloader.validate(context, file)
        if (rejection != ApkRejection.NONE) {
            file.delete()
            return InstallEvent.Failed(InstallError.Rejected(rejection))
        }
        return InstallEvent.Completed(file.absolutePath)
    }

    private fun publish(event: InstallEvent) {
        mutableState.update { installReduce(it, event) }
    }
}
