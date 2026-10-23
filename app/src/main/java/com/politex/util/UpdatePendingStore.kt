package com.politex.util

import android.content.Context
import com.politex.update.UpdateAsset
import com.politex.update.UpdateInfo

object UpdatePendingStore {
    private const val PREFS = "mpk_update_prefs"
    private const val KEY_TAG = "pending_tag"
    private const val KEY_VERSION = "pending_version_name"
    private const val KEY_CODE = "pending_version_code"
    private const val KEY_NOTES = "pending_notes"
    private const val KEY_ASSET_NAME = "pending_asset_name"
    private const val KEY_ASSET_URL = "pending_asset_url"
    private const val KEY_ASSET_SIZE = "pending_asset_size"
    private const val KEY_SHA = "pending_sha256"

    fun save(
        context: Context,
        info: UpdateInfo,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_TAG, info.tagName)
            .putString(KEY_VERSION, info.versionName)
            .putInt(KEY_CODE, info.versionCode)
            .putString(KEY_NOTES, info.notes)
            .putString(KEY_ASSET_NAME, info.asset.name)
            .putString(KEY_ASSET_URL, info.asset.url)
            .putLong(KEY_ASSET_SIZE, info.asset.size)
            .putString(KEY_SHA, info.sha256)
            .apply()
    }

    fun load(context: Context): UpdateInfo? {
        val store = prefs(context)
        val version = store.getString(KEY_VERSION, null)?.takeIf { it.isNotBlank() } ?: return null
        val code = store.getInt(KEY_CODE, 0)
        if (code <= 0) return null
        val url = store.getString(KEY_ASSET_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        return UpdateInfo(
            tagName = store.getString(KEY_TAG, null)?.takeIf { it.isNotBlank() } ?: version,
            versionName = version,
            versionCode = code,
            notes = store.getString(KEY_NOTES, null).orEmpty(),
            asset =
            UpdateAsset(
                name = store.getString(KEY_ASSET_NAME, null)?.takeIf { it.isNotBlank() } ?: "update.apk",
                url = url,
                size = store.getLong(KEY_ASSET_SIZE, 0L),
            ),
            sha256 = store.getString(KEY_SHA, null)?.takeIf { it.isNotBlank() },
        )
    }

    fun clear(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_TAG)
            .remove(KEY_VERSION)
            .remove(KEY_CODE)
            .remove(KEY_NOTES)
            .remove(KEY_ASSET_NAME)
            .remove(KEY_ASSET_URL)
            .remove(KEY_ASSET_SIZE)
            .remove(KEY_SHA)
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
