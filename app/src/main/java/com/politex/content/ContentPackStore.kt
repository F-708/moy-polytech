package com.politex.content

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.io.File

object ContentPackStore {
    private const val PREFS = "content_pack_prefs"
    private const val KEY_ACTIVE_VERSION = "active_version"
    private const val KEY_ACTIVE_GENERATED_AT = "active_generated_at"

    val revision = mutableIntStateOf(0)

    @Volatile
    var activeVersion: Int = 0
        private set

    @Volatile
    var generatedAt: String = ""
        private set

    fun attach(context: Context) {
        val prefs = prefs(context)
        val version = prefs.getInt(KEY_ACTIVE_VERSION, 0)
        if (version > 0 && !isUsablePackDir(packDir(context, version))) {
            prefs
                .edit()
                .remove(KEY_ACTIVE_VERSION)
                .remove(KEY_ACTIVE_GENERATED_AT)
                .apply()
            activeVersion = 0
            generatedAt = ""
        } else {
            activeVersion = version
            generatedAt = prefs.getString(KEY_ACTIVE_GENERATED_AT, "").orEmpty()
        }
    }

    fun contentDir(context: Context): File = File(context.filesDir, "content")

    fun packDir(
        context: Context,
        version: Int,
    ): File = File(contentDir(context), version.toString())

    fun activate(
        context: Context,
        version: Int,
        generatedAt: String,
        previousVersion: Int,
    ) {
        prefs(context)
            .edit()
            .putInt(KEY_ACTIVE_VERSION, version)
            .putString(KEY_ACTIVE_GENERATED_AT, generatedAt)
            .apply()
        activeVersion = version
        this.generatedAt = generatedAt
        prune(context, version, previousVersion)
        revision.intValue++
    }

    fun reset(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_ACTIVE_VERSION)
            .remove(KEY_ACTIVE_GENERATED_AT)
            .apply()
        activeVersion = 0
        generatedAt = ""
        revision.intValue++
    }

    internal fun isUsablePackDir(dir: File): Boolean = dir.isDirectory && ContentPackValidator.REQUIRED_FILES.all { File(dir, it).isFile }

    internal fun prune(
        context: Context,
        keepVersion: Int,
        keepPreviousVersion: Int,
    ) {
        val dir = contentDir(context)
        val files = dir.listFiles() ?: return
        for (entry in files) {
            val version = entry.name.toIntOrNull()
            if (version == null) {
                entry.deleteRecursively()
                continue
            }
            if (version != keepVersion && version != keepPreviousVersion) entry.deleteRecursively()
        }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

fun contentPackStatusText(
    activeVersion: Int,
    generatedAt: String,
): String = if (activeVersion > 0) {
    val date = generatedAt.trim().substringBefore('T').takeIf { it.isNotBlank() }
    if (date != null) "Пакет v$activeVersion от $date" else "Пакет v$activeVersion"
} else {
    "Встроенные данные"
}
