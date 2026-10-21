package com.politex.util

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

object SubjectRenameStore {
    private const val PREFS = "mpk_subject_renames"
    private const val KEY_MAP = "map"
    const val RENAMED_PREFIX = "*"

    private val cache = ConcurrentHashMap<String, String>()
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    val revision = mutableIntStateOf(0)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val json =
                context
                    .applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_MAP, "") ?: ""
            cache.clear()
            cache.putAll(parseMap(json))
            loaded = true
        }
    }

    fun customName(
        context: Context,
        original: String,
    ): String? {
        ensureLoaded(context)
        return customNameCached(original)
    }

    fun customNameCached(original: String): String? {
        tryAutoLoad()
        return cache[original]?.takeIf { it.isNotBlank() }
    }

    fun displayName(
        context: Context,
        original: String,
    ): String {
        ensureLoaded(context)
        return displayNameCached(original)
    }

    fun displayNameCached(original: String): String {
        val custom = customNameCached(original) ?: return original
        return RENAMED_PREFIX + custom
    }

    fun setCustomName(
        context: Context,
        original: String,
        custom: String,
    ) {
        ensureLoaded(context)
        val trimmed = custom.trim()
        val before = customNameCached(original)
        synchronized(loadLock) {
            if (trimmed.isBlank() || trimmed == original) {
                cache.remove(original)
            } else {
                cache[original] = trimmed
            }
            persistLocked(context)
        }
        revision.intValue++
        if (before != customNameCached(original)) {
            com.politex.widget.WidgetUpdateHelper.refreshScheduleWidgets(context.applicationContext)
        }
    }

    fun reset(
        context: Context,
        original: String,
    ) {
        setCustomName(context, original, "")
    }

    fun allRenames(context: Context): Map<String, String> {
        ensureLoaded(context)
        return LinkedHashMap(cache)
    }

    internal fun clearAll(context: Context) {
        context
            .applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        synchronized(loadLock) {
            cache.clear()
            loaded = true
        }
        revision.intValue++
        com.politex.widget.WidgetUpdateHelper.refreshScheduleWidgets(context.applicationContext)
    }

    internal fun parseMap(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val result = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key, "").trim()
                if (key.isNotBlank() && value.isNotBlank()) {
                    result[key] = value
                }
            }
            result.toMap()
        }.getOrElse { emptyMap() }
    }

    internal fun serializeMap(map: Map<String, String>): String {
        val obj = JSONObject()
        for ((key, value) in map) {
            if (key.isNotBlank() && value.isNotBlank()) {
                obj.put(key, value)
            }
        }
        return obj.toString()
    }

    private fun persistLocked(context: Context) {
        context
            .applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAP, serializeMap(cache))
            .apply()
    }

    private fun tryAutoLoad() {
        if (loaded) return
        val context = currentAppContext() ?: return
        ensureLoaded(context)
    }

    private fun currentAppContext(): Context? =
        runCatching {
            Class
                .forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.getOrNull()
}
