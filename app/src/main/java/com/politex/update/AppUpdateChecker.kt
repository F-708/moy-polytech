package com.politex.update

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class UpdateAsset(
    val name: String,
    val url: String,
    val size: Long,
)

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val asset: UpdateAsset,
    val sha256: String? = null,
)

object AppUpdateChecker {
    internal const val USER_AGENT = "MoyPolitech"
    internal const val ACCEPT_HEADER = "application/vnd.github+json"
    internal const val OWNER_PLACEHOLDER = "OWNER_PLACEHOLDER"
    internal const val REPO_PLACEHOLDER = "REPO_PLACEHOLDER"

    internal val UPDATE_ENABLED_FLAVORS = setOf("standard")

    private val SKIP_ASSET_MARKERS = listOf("sha256", "sha512", "checksum", "source", "symbols")

    internal fun latestReleaseUrl(
        owner: String,
        repo: String,
    ): String = "https://api.github.com/repos/${owner.trim()}/${repo.trim()}/releases/latest"

    internal fun releasesUrl(
        owner: String,
        repo: String,
        perPage: Int = 8,
    ): String = "https://api.github.com/repos/${owner.trim()}/${repo.trim()}/releases?per_page=$perPage"

    internal fun rawMetaUrl(
        owner: String,
        repo: String,
    ): String = "https://raw.githubusercontent.com/${owner.trim()}/${repo.trim()}/main/update-meta.json"

    internal fun isConfigured(
        owner: String?,
        repo: String?,
    ): Boolean {
        if (owner.isNullOrBlank() || repo.isNullOrBlank()) return false
        return owner.trim() != OWNER_PLACEHOLDER && repo.trim() != REPO_PLACEHOLDER
    }

    internal fun parseLatestRelease(
        json: String?,
        currentVersionName: String,
        flavor: String,
    ): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.has("message")) return null
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tagName = root.optString("tag_name").trim()
        val version = UpdateVersions.normalize(tagName) ?: return null
        if (!UpdateVersions.isNewer(version, currentVersionName)) return null
        val code = versionCodeOf(tagName) ?: return null
        val asset = selectApkAsset(root.optJSONArray("assets"), flavor) ?: return null
        val notes = (root.opt("body") as? String)?.trim().orEmpty()
        return UpdateInfo(
            tagName = tagName,
            versionName = version.substringBefore('+'),
            versionCode = code,
            notes = notes,
            asset = asset,
        )
    }

    internal fun selectApkAsset(
        assets: JSONArray?,
        flavor: String,
    ): UpdateAsset? {
        if (assets == null) return null
        val flavorMarker = flavor.lowercase(Locale.ROOT)
        var fallback: UpdateAsset? = null
        for (index in 0 until assets.length()) {
            val entry = assets.optJSONObject(index) ?: continue
            val name = entry.optString("name").trim()
            val url = entry.optString("browser_download_url").trim()
            val lower = name.lowercase(Locale.ROOT)
            if (!lower.endsWith(".apk") || url.isBlank()) continue
            if (SKIP_ASSET_MARKERS.any { lower.contains(it) }) continue
            val candidate =
                UpdateAsset(
                    name = name,
                    url = url,
                    size = entry.optLong("size", 0L),
                )
            if (flavorMarker.isNotBlank() && lower.contains(flavorMarker)) return candidate
            if (fallback == null) fallback = candidate
        }
        return fallback
    }

    internal fun parseResponse(
        statusCode: Int,
        json: String?,
        currentVersionName: String,
        flavor: String,
    ): UpdateInfo? {
        if (statusCode != HTTP_OK) return null
        return parseLatestRelease(json, currentVersionName, flavor)
    }

    private const val HTTP_OK = 200
}
