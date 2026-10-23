package com.politex.update

import com.politex.util.UpdateChannel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class UpdateMeta(
    val channel: String,
    val version: String,
    val versionCode: Int,
    val apk: String,
    val sha256: String,
    val publishedAt: String,
    val minVersionCode: Int,
)

internal object UpdateMetaSource {
    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_PREVIEW = "preview"

    fun parse(json: String?): UpdateMeta? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.has("message")) return null
        val channel = root.optString("channel").trim().lowercase(Locale.ROOT)
        if (channel != CHANNEL_STABLE && channel != CHANNEL_PREVIEW) return null
        val version = UpdateVersions.normalize(root.optString("version")) ?: return null
        val explicitCode = root.optInt("version_code", -1)
        val code = if (explicitCode > 0) explicitCode else versionCodeOf(version) ?: return null
        val apk = root.optString("apk").trim()
        if (apk.isBlank() || !apk.startsWith("https://")) return null
        return UpdateMeta(
            channel = channel,
            version = version.substringBefore('+'),
            versionCode = code,
            apk = apk,
            sha256 = root.optString("sha256").trim().lowercase(Locale.ROOT),
            publishedAt = root.optString("published_at").trim(),
            minVersionCode = root.optInt("min_version_code", 0),
        )
    }

    fun metaMatchesChannel(
        metaChannel: String,
        channel: UpdateChannel,
    ): Boolean = channel == UpdateChannel.PREVIEW || metaChannel == CHANNEL_STABLE

    fun selectFromMeta(
        meta: UpdateMeta?,
        channel: UpdateChannel,
        currentVersionCode: Int,
    ): UpdateInfo? {
        if (meta == null) return null
        if (!metaMatchesChannel(meta.channel, channel)) return null
        if (meta.versionCode <= currentVersionCode) return null
        return UpdateInfo(
            tagName = meta.version,
            versionName = meta.version,
            versionCode = meta.versionCode,
            notes = "",
            asset =
            UpdateAsset(
                name = meta.apk.substringAfterLast('/').ifBlank { "update.apk" },
                url = meta.apk,
                size = 0L,
            ),
            sha256 = meta.sha256.takeIf { it.isNotBlank() },
        )
    }

    fun selectFromLatestRelease(
        json: String?,
        currentVersionCode: Int,
        flavor: String,
    ): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.has("message")) return null
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        return releaseInfo(
            root = root,
            channel = UpdateChannel.STABLE,
            currentVersionCode = currentVersionCode,
            flavor = flavor,
        )
    }

    fun selectFromReleases(
        json: String?,
        channel: UpdateChannel,
        currentVersionCode: Int,
        flavor: String,
    ): UpdateInfo? {
        if (json.isNullOrBlank()) return null
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
        var best: UpdateInfo? = null
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            if (entry.optBoolean("draft")) continue
            if (channel == UpdateChannel.STABLE && entry.optBoolean("prerelease")) continue
            val candidate =
                releaseInfo(
                    root = entry,
                    channel = channel,
                    currentVersionCode = currentVersionCode,
                    flavor = flavor,
                ) ?: continue
            if (best == null || candidate.versionCode > best.versionCode) best = candidate
        }
        return best
    }

    private fun releaseInfo(
        root: JSONObject,
        channel: UpdateChannel,
        currentVersionCode: Int,
        flavor: String,
    ): UpdateInfo? {
        val tag = root.optString("tag_name").trim()
        val code = versionCodeOf(tag) ?: return null
        if (code <= currentVersionCode) return null
        if (channel == UpdateChannel.STABLE && isPreviewTag(tag)) return null
        val asset = AppUpdateChecker.selectApkAsset(root.optJSONArray("assets"), flavor) ?: return null
        val notes = (root.opt("body") as? String)?.trim().orEmpty()
        return UpdateInfo(
            tagName = tag,
            versionName = UpdateVersions.normalize(tag)?.substringBefore('+') ?: tag,
            versionCode = code,
            notes = notes,
            asset = asset,
        )
    }
}
