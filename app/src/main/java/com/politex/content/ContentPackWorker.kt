package com.politex.content

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.politex.AppGraph
import com.politex.BuildConfig
import com.politex.ui.util.TeachersCache
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class ContentPackWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {
    override fun doWork(): Result {
        val context = applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val response = fetchVersionJson(prefs.getString(KEY_ETAG, null)) ?: return Result.retry()
        if (response.status == HTTP_NOT_MODIFIED) return Result.success()
        if (response.status != HTTP_OK) return Result.success()
        response.etag
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { prefs.edit().putString(KEY_ETAG, it).apply() }

        val manifest = ContentPackValidator.parseManifest(response.body) ?: return Result.success()
        if (!ContentPackValidator.shouldApply(manifest.packVersion, ContentPackStore.activeVersion)) return Result.success()
        if (!ContentPackValidator.minAppVersionAccepted(manifest.minAppVersion, BuildConfig.VERSION_CODE)) return Result.success()

        val zip = download(context, resolvePackUrl(contentBaseUrl(), manifest.url), "pack-${manifest.packVersion}.zip")
            ?: return Result.retry()
        if (!ContentPackValidator.sizeAccepted(zip, manifest.sizeBytes)) {
            zip.delete()
            return Result.success()
        }
        if (!ContentPackValidator.matchesChecksum(zip, manifest.sha256)) {
            zip.delete()
            return Result.success()
        }

        val staging = File(ContentPackStore.contentDir(context), "staging-${manifest.packVersion}")
        val extraction = ContentPackValidator.extract(zip, staging)
        zip.delete()
        if (extraction != ContentPackRejection.NONE) {
            staging.deleteRecursively()
            return Result.success()
        }
        if (ContentPackValidator.validateContent(staging) != ContentPackRejection.NONE) {
            staging.deleteRecursively()
            return Result.success()
        }

        val target = ContentPackStore.packDir(context, manifest.packVersion)
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!staging.renameTo(target)) {
            staging.deleteRecursively()
            return Result.retry()
        }

        val previous = ContentPackStore.activeVersion
        ContentPackStore.activate(context, manifest.packVersion, manifest.generatedAt, previous)
        invalidateCaches(context)
        return Result.success()
    }

    private fun fetchVersionJson(etag: String?): VersionResponse? =
        runCatching {
            val request =
                Request
                    .Builder()
                    .url(versionJsonUrl())
                    .header("User-Agent", USER_AGENT)
                    .apply { if (!etag.isNullOrBlank()) header("If-None-Match", etag) }
                    .build()
            client.newCall(request).execute().use { response ->
                VersionResponse(
                    status = response.code,
                    body = response.body?.string(),
                    etag = response.header("ETag"),
                )
            }
        }.getOrNull()

    private fun download(
        context: Context,
        url: String,
        fileName: String,
    ): File? {
        val dir = File(context.cacheDir, "content-packs").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, fileName)
        val complete = runCatching { fetchToFile(url, target) }.getOrDefault(false)
        if (!complete || target.length() <= 0L) {
            target.delete()
            return null
        }
        return target
    }

    private fun fetchToFile(
        url: String,
        target: File,
    ): Boolean {
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            target.outputStream().use { output ->
                val input = body.byteStream()
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > ContentPackValidator.MAX_PACK_BYTES) return false
                    output.write(buffer, 0, read)
                }
            }
            return true
        }
    }

    companion object {
        const val WORK_NAME = "content_pack_check"
        const val ONCE_WORK_NAME = "content_pack_check_once"
        private const val PREFS = "content_pack_prefs"
        private const val KEY_ETAG = "version_etag"
        private const val VERSION_JSON = "version.json"
        private const val USER_AGENT = "MoyPolitech"
        private const val CHECK_INTERVAL_HOURS = 12L
        private const val DOWNLOAD_BUFFER_SIZE = 8192
        private const val HTTP_OK = 200
        private const val HTTP_NOT_MODIFIED = 304

        private val client by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.MINUTES)
                .build()
        }

        fun schedule(context: Context) {
            val request =
                PeriodicWorkRequestBuilder<ContentPackWorker>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun checkOnce(context: Context) {
            runCatching {
                val request =
                    OneTimeWorkRequestBuilder<ContentPackWorker>()
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        ).build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONCE_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        fun contentBaseUrl(): String =
            "https://raw.githubusercontent.com/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/main/content/"

        fun versionJsonUrl(): String = contentBaseUrl() + VERSION_JSON

        private fun invalidateCaches(context: Context) {
            runCatching { AppGraph.teachersRepository.invalidateCache() }
            runCatching { TeachersCache.invalidate() }
            runCatching { AppGraph.scheduleRepository.resetCatalog() }
            runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
        }
    }
}

internal data class VersionResponse(
    val status: Int,
    val body: String?,
    val etag: String?,
)

internal fun resolvePackUrl(
    baseUrl: String,
    url: String,
): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val relative = trimmed.removePrefix("./").removePrefix("/")
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return base + relative
}
