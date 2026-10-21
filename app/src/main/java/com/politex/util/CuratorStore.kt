package com.politex.util

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

object CuratorStore {
    private const val PREFS = "mpk_curators"
    private const val KEY_MAP = "map"
    private const val REFRESH_WINDOW_MS = 6L * 60 * 60 * 1000

    data class Entry(
        val teacher: String,
        val updatedAt: Long,
    )

    private val cache = ConcurrentHashMap<String, Entry>()
    private val loadLock = Any()

    @Volatile
    private var loaded = false

    @Volatile
    private var pendingPersist = false

    private var appContext: Context? = null

    val revision = mutableIntStateOf(0)

    fun attach(context: Context) {
        ensureLoaded(context)
    }

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(loadLock) {
            if (loaded) return
            val app = context.applicationContext
            appContext = app
            val json = app
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MAP, "") ?: ""
            for ((group, entry) in parseMap(json)) {
                val current = cache[group]
                if (current == null || entry.updatedAt > current.updatedAt) {
                    cache[group] = entry
                }
            }
            loaded = true
            if (pendingPersist) {
                persistLocked()
                pendingPersist = false
            }
        }
    }

    fun record(
        pairs: Map<String, String>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (pairs.isEmpty()) return
        tryAutoLoad()
        var mutated = false
        var nameChanged = false
        synchronized(loadLock) {
            for ((rawGroup, rawTeacher) in pairs) {
                val group = GroupParser.cleanRawGroupName(rawGroup)
                val teacher = rawTeacher.trim()
                if (group.isBlank() || teacher.isBlank()) continue
                val prev = cache[group]
                when {
                    prev == null || prev.teacher != teacher -> {
                        cache[group] = Entry(teacher, now)
                        mutated = true
                        nameChanged = true
                    }

                    now - prev.updatedAt >= REFRESH_WINDOW_MS -> {
                        cache[group] = Entry(teacher, now)
                        mutated = true
                    }
                }
            }
            if (mutated) {
                if (loaded && appContext != null) {
                    persistLocked()
                } else {
                    pendingPersist = true
                }
            }
        }
        if (nameChanged) revision.intValue++
    }

    fun curatorFor(group: String): String? {
        tryAutoLoad()
        val key = GroupParser.cleanRawGroupName(group)
        if (key.isBlank()) return null
        return cache[key]?.teacher?.takeIf { it.isNotBlank() }
    }

    fun entryFor(group: String): Entry? {
        tryAutoLoad()
        val key = GroupParser.cleanRawGroupName(group)
        if (key.isBlank()) return null
        return cache[key]
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
            appContext = context.applicationContext
            loaded = true
            pendingPersist = false
        }
        revision.intValue++
    }

    internal fun resetInMemory() {
        synchronized(loadLock) {
            cache.clear()
            loaded = false
            pendingPersist = false
            appContext = null
        }
    }

    internal fun snapshot(): Map<String, Entry> = LinkedHashMap(cache)

    internal fun parseMap(json: String): Map<String, Entry> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val result = LinkedHashMap<String, Entry>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = obj.optJSONObject(key) ?: continue
                val teacher = item.optString("t", "").trim()
                val updatedAt = item.optLong("u", 0L)
                if (key.isNotBlank() && teacher.isNotBlank()) {
                    result[key] = Entry(teacher, updatedAt)
                }
            }
            result.toMap()
        }.getOrElse { emptyMap() }
    }

    internal fun serializeMap(map: Map<String, Entry>): String {
        val obj = JSONObject()
        for ((group, entry) in map) {
            if (group.isBlank() || entry.teacher.isBlank()) continue
            obj.put(
                group,
                JSONObject()
                    .put("t", entry.teacher)
                    .put("u", entry.updatedAt),
            )
        }
        return obj.toString()
    }

    private fun persistLocked() {
        val ctx = appContext ?: return
        ctx
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAP, serializeMap(LinkedHashMap(cache)))
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
