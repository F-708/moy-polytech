package com.politex.update

import android.content.Context
import com.politex.BuildConfig
import com.politex.util.UpdateChannel
import com.politex.util.UpdateChannelStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface ManualCheckResult {
    data class UpdateFound(
        val info: UpdateInfo,
    ) : ManualCheckResult

    data object Latest : ManualCheckResult

    data object NetworkError : ManualCheckResult

    data object Disabled : ManualCheckResult
}

internal sealed interface FetchOutcome {
    data class Update(
        val info: UpdateInfo,
    ) : FetchOutcome

    data object UpToDate : FetchOutcome

    data object Failure : FetchOutcome
}

internal data class RawMetaResponse(
    val status: Int,
    val body: String?,
    val etag: String?,
)

internal data class MetaEvaluation(
    val info: UpdateInfo?,
    val storeEtag: String?,
    val storeBody: String?,
    val fallbackToApi: Boolean,
)

object UpdateCheckController {
    private const val PREFS = "mpk_update_prefs"
    private const val KEY_ETAG = "meta_etag"
    private const val KEY_CACHE = "meta_cache"
    private const val HTTP_OK = 200
    private const val HTTP_NOT_MODIFIED = 304
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_TOO_MANY_REQUESTS = 429

    internal var checkOverride: ((Context) -> ManualCheckResult)? = null

    private val client by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun checkNow(context: Context): ManualCheckResult {
        checkOverride?.let { return it(context) }
        if (!updatesEnabled()) return ManualCheckResult.Disabled
        return when (val outcome = fetchBest(context)) {
            is FetchOutcome.Update -> ManualCheckResult.UpdateFound(outcome.info)
            FetchOutcome.UpToDate -> ManualCheckResult.Latest
            FetchOutcome.Failure -> ManualCheckResult.NetworkError
        }
    }

    internal fun updatesEnabled(): Boolean = AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR) &&
        AppUpdateChecker.isConfigured(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO)

    internal fun evaluateMeta(
        response: RawMetaResponse,
        cachedBody: String?,
        channel: UpdateChannel,
        currentVersionCode: Int,
    ): MetaEvaluation = evaluateMeta(response.status, response.body, response.etag, cachedBody, channel, currentVersionCode)

    internal fun evaluateMeta(
        status: Int,
        body: String?,
        responseEtag: String?,
        cachedBody: String?,
        channel: UpdateChannel,
        currentVersionCode: Int,
    ): MetaEvaluation = when (status) {
        HTTP_NOT_MODIFIED -> {
            val cachedMeta = UpdateMetaSource.parse(cachedBody)
            if (cachedMeta == null) {
                MetaEvaluation(null, null, null, true)
            } else {
                val info = UpdateMetaSource.selectFromMeta(cachedMeta, channel, currentVersionCode)
                MetaEvaluation(info, null, null, false)
            }
        }
        HTTP_OK -> {
            val text = body.orEmpty()
            val meta = UpdateMetaSource.parse(text)
            if (meta == null) {
                MetaEvaluation(null, null, null, true)
            } else {
                val info = UpdateMetaSource.selectFromMeta(meta, channel, currentVersionCode)
                val etag = responseEtag?.trim()?.takeIf { it.isNotBlank() }
                MetaEvaluation(info, etag, etag?.let { text }, false)
            }
        }
        else -> MetaEvaluation(null, null, null, true)
    }

    private fun fetchBest(context: Context): FetchOutcome {
        val channel = UpdateChannelStore.channel(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val evaluation =
            runCatching { fetchRawMeta(prefs.getString(KEY_ETAG, null)) }
                .getOrNull()
                ?.let { response ->
                    evaluateMeta(
                        response = response,
                        cachedBody = prefs.getString(KEY_CACHE, null),
                        channel = channel,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                    )
                }
        if (evaluation != null) {
            if (evaluation.storeEtag != null && evaluation.storeBody != null) {
                prefs
                    .edit()
                    .putString(KEY_ETAG, evaluation.storeEtag)
                    .putString(KEY_CACHE, evaluation.storeBody)
                    .apply()
            }
            if (!evaluation.fallbackToApi) {
                return evaluation.info?.let { FetchOutcome.Update(it) } ?: FetchOutcome.UpToDate
            }
        }
        return apiFallback(channel)
    }

    private fun fetchRawMeta(etag: String?): RawMetaResponse {
        val request =
            Request
                .Builder()
                .url(AppUpdateChecker.rawMetaUrl(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO))
                .header("User-Agent", AppUpdateChecker.USER_AGENT)
                .apply { if (!etag.isNullOrBlank()) header("If-None-Match", etag) }
                .build()
        client.newCall(request).execute().use { response ->
            return RawMetaResponse(
                status = response.code,
                body = response.body?.string(),
                etag = response.header("ETag"),
            )
        }
    }

    private fun apiFallback(channel: UpdateChannel): FetchOutcome = runCatching {
        if (channel == UpdateChannel.STABLE) fetchLatestRelease() else fetchReleaseList(channel)
    }.getOrElse { FetchOutcome.Failure }

    private fun fetchLatestRelease(): FetchOutcome {
        val request =
            Request
                .Builder()
                .url(AppUpdateChecker.latestReleaseUrl(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO))
                .header("Accept", AppUpdateChecker.ACCEPT_HEADER)
                .header("User-Agent", AppUpdateChecker.USER_AGENT)
                .build()
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS) return FetchOutcome.Failure
            if (response.code != HTTP_OK) return FetchOutcome.UpToDate
            val info =
                UpdateMetaSource.selectFromLatestRelease(
                    json = response.body?.string(),
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    flavor = BuildConfig.FLAVOR,
                )
            return info?.let { FetchOutcome.Update(it) } ?: FetchOutcome.UpToDate
        }
    }

    private fun fetchReleaseList(channel: UpdateChannel): FetchOutcome {
        val request =
            Request
                .Builder()
                .url(AppUpdateChecker.releasesUrl(BuildConfig.GITHUB_OWNER, BuildConfig.GITHUB_REPO))
                .header("Accept", AppUpdateChecker.ACCEPT_HEADER)
                .header("User-Agent", AppUpdateChecker.USER_AGENT)
                .build()
        client.newCall(request).execute().use { response ->
            if (response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS) return FetchOutcome.Failure
            if (response.code != HTTP_OK) return FetchOutcome.UpToDate
            val info =
                UpdateMetaSource.selectFromReleases(
                    json = response.body?.string(),
                    channel = channel,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    flavor = BuildConfig.FLAVOR,
                )
            return info?.let { FetchOutcome.Update(it) } ?: FetchOutcome.UpToDate
        }
    }
}

internal fun channelChangeRequiresCheck(
    previous: UpdateChannel,
    selected: UpdateChannel,
): Boolean = previous != selected

internal fun channelLabel(channel: UpdateChannel): String = when (channel) {
    UpdateChannel.STABLE -> "стабильный"
    UpdateChannel.PREVIEW -> "превью"
}

internal fun defaultUpdateStatus(
    channel: UpdateChannel,
    versionName: String,
): String = "Установлена $versionName · канал ${channelLabel(channel)}"

internal fun manualResultText(
    result: ManualCheckResult,
    channel: UpdateChannel,
    versionName: String,
): String = when (result) {
    is ManualCheckResult.UpdateFound -> "Найдено обновление ${result.info.versionName}"
    ManualCheckResult.Latest -> "Вы уже используете последнюю версию"
    ManualCheckResult.NetworkError -> "Нет соединения"
    ManualCheckResult.Disabled -> defaultUpdateStatus(channel, versionName)
}

internal fun manualResultIsError(result: ManualCheckResult): Boolean = result == ManualCheckResult.NetworkError

internal fun manualResultIsFound(result: ManualCheckResult): Boolean = result is ManualCheckResult.UpdateFound
