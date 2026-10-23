package com.politex.ui.screens.materials

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.politex.util.PendingMaterial

object ExternalMaterialIntake {

    const val MAX_FILES_PER_SHARE = 20

    private const val APK_MIME = "application/vnd.android.package-archive"

    var pending by mutableStateOf<PendingMaterial?>(null)
        private set

    var active by mutableStateOf<PendingMaterial?>(null)
        private set

    var warning by mutableStateOf<String?>(null)
        private set

    val hasWork: Boolean
        get() = pending != null || active != null

    fun offer(material: PendingMaterial) {
        pending = material
    }

    fun claim() {
        if (active != null) return
        val next = pending ?: return
        active = next
        pending = null
    }

    fun finish(material: PendingMaterial) {
        if (active === material) active = null
    }

    fun consumeWarning(): String? {
        val current = warning
        warning = null
        return current
    }

    fun fromIntent(
        resolver: ContentResolver,
        intent: Intent,
    ): PendingMaterial? {
        warning = null
        return try {
            parse(resolver, intent)
        } catch (_: Exception) {
            warning = null
            null
        }
    }

    private fun parse(
        resolver: ContentResolver,
        intent: Intent,
    ): PendingMaterial? {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> {
                val uri = intent.data ?: intent.streamUri() ?: intent.clipDataUri() ?: return null
                if (uri.scheme != "content") return null
                if (intent.isApk(resolver, uri)) return null
                return if (intent.isImage(resolver, uri)) {
                    PendingMaterial.Images(listOf(uri))
                } else {
                    PendingMaterial.SingleFile(uri)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.streamUris().orEmpty().filter { it.scheme == "content" }
                if (uris.isEmpty()) return null
                if (uris.any { intent.isApk(resolver, it) }) return null
                val limited = uris.take(MAX_FILES_PER_SHARE)
                if (uris.size > limited.size) {
                    warning = "Можно добавить не больше $MAX_FILES_PER_SHARE файлов за раз — лишние отброшены"
                }
                return if (intent.isImage(resolver, limited.first())) {
                    PendingMaterial.Images(limited)
                } else {
                    PendingMaterial.SingleFile(limited.first())
                }
            }
            else -> return null
        }
    }

    private fun Intent.isApk(
        resolver: ContentResolver,
        uri: Uri,
    ): Boolean {
        if (effectiveMime(resolver, uri) == APK_MIME) return true
        if (uri.lastPathSegment?.endsWith(".apk", ignoreCase = true) == true) return true
        val displayName =
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull()
        return displayName?.trim()?.endsWith(".apk", ignoreCase = true) == true
    }

    private fun Intent.isImage(
        resolver: ContentResolver,
        uri: Uri,
    ): Boolean = effectiveMime(resolver, uri)?.startsWith("image/") == true

    private fun Intent.effectiveMime(
        resolver: ContentResolver,
        uri: Uri,
    ): String? {
        val declared = type
        if (!declared.isNullOrBlank() && declared != "*/*") return declared
        val fromExtras =
            getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
                ?.firstOrNull { it.isNotBlank() && it != "*/*" }
        if (fromExtras != null) return fromExtras
        return runCatching { resolver.getType(uri) }.getOrNull()
    }

    private fun Intent.streamUri(): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
    }.getOrNull()

    private fun Intent.streamUris(): List<Uri>? = runCatching {
        val raw: List<Parcelable>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            }
        raw?.filterIsInstance<Uri>()
    }.getOrNull()

    private fun Intent.clipDataUri(): Uri? = clipData
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.uri
}
