package com.politex.ui.changelog

import android.content.Context

object WhatsNewStore {
    private const val PREFS = "mpk_whats_new_prefs"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"

    fun onLaunch(
        context: Context,
        currentVersionCode: Int,
        entries: List<WhatsNewEntry> = WhatsNewEntries,
    ): List<WhatsNewEntry> {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_LAST_SEEN_VERSION_CODE)) {
            recordSeen(context, currentVersionCode)
            return emptyList()
        }
        return entriesFor(prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, 0), currentVersionCode, entries)
    }

    fun recordSeen(
        context: Context,
        versionCode: Int,
    ) {
        prefs(context)
            .edit()
            .putInt(KEY_LAST_SEEN_VERSION_CODE, versionCode)
            .apply()
    }

    internal fun lastSeen(context: Context): Int? {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_LAST_SEEN_VERSION_CODE)) return null
        return prefs.getInt(KEY_LAST_SEEN_VERSION_CODE, 0)
    }

    internal fun clear(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_LAST_SEEN_VERSION_CODE)
            .apply()
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal fun entriesFor(
    lastSeenVersionCode: Int,
    currentVersionCode: Int,
    entries: List<WhatsNewEntry>,
): List<WhatsNewEntry> {
    if (lastSeenVersionCode >= currentVersionCode) return emptyList()
    return entries
        .filter { it.versionCode > lastSeenVersionCode && it.versionCode <= currentVersionCode }
        .sortedBy { it.versionCode }
}
