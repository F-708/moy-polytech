package com.politex.ui.screens.materials

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.politex.util.IntakeDecision
import com.politex.util.MaterialFormatKind
import com.politex.util.MaterialFormats
import com.politex.util.PendingMaterial

/**
 * Приём файла извне («Открыть с помощью» / «Поделиться»).
 *
 * Принимаем только форматы из [MaterialFormats] — то, что умеет открыть встроенный
 * вьюер. Чужой формат не возвращается как материал, а оставляет текст в [rejection],
 * который показывает MainActivity: отказ не должен выглядеть как «приложение не отвечает».
 */
object ExternalMaterialIntake {

    const val MAX_FILES_PER_SHARE = 20

    private const val APK_MIME = "application/vnd.android.package-archive"

    private const val TAG = "MaterialIntake"

    var pending by mutableStateOf<PendingMaterial?>(null)
        private set

    var active by mutableStateOf<PendingMaterial?>(null)
        private set

    var warning by mutableStateOf<String?>(null)
        private set

    /** Текст отказа для пользователя: формат не поддерживается. */
    var rejection by mutableStateOf<String?>(null)
        private set

    /** Провайдер закрыл доступ к имени/типу файла — отличимо от «непонятный формат». */
    private var accessDenied = false

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

    /**
     * Экран-владелец активного материала закрылся (ушли с вкладки) — снимаем захват.
     * Без этого `active` остаётся навсегда, `claim()` больше никого не пускает,
     * и следующие шаринги молча теряются.
     */
    fun releaseActive() {
        active = null
    }

    fun consumeWarning(): String? {
        val current = warning
        warning = null
        return current
    }

    fun consumeRejection(): String? {
        val current = rejection
        rejection = null
        return current
    }

    fun fromIntent(
        resolver: ContentResolver,
        intent: Intent,
    ): PendingMaterial? {
        warning = null
        rejection = null
        accessDenied = false
        return try {
            parse(resolver, intent)
        } catch (e: SecurityException) {
            // Нет доступа к файлу — это не «непонятный формат», текст должен отличаться.
            Log.w(TAG, "Провайдер закрыл доступ к файлу: ${e.javaClass.simpleName}")
            rejection = MaterialFormats.NO_ACCESS_MESSAGE
            null
        } catch (e: Exception) {
            Log.w(TAG, "Разбор входящего файла не удался: ${e.javaClass.simpleName}")
            if (rejection == null) rejection = MaterialFormats.NO_ACCESS_MESSAGE
            null
        }
    }

    /** Текст отказа: «нет доступа» важнее, чем «формат не определён». */
    private fun messageOf(decision: IntakeDecision.Reject): String =
        if (accessDenied && !decision.hard) MaterialFormats.NO_ACCESS_MESSAGE else decision.message

    private fun parse(
        resolver: ContentResolver,
        intent: Intent,
    ): PendingMaterial? =
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> parseSingle(resolver, intent)
            Intent.ACTION_SEND_MULTIPLE -> parseMultiple(resolver, intent)
            else -> null
        }

    private fun parseSingle(
        resolver: ContentResolver,
        intent: Intent,
    ): PendingMaterial? {
        val uri = intent.data ?: intent.streamUri() ?: intent.clipDataUri() ?: return null
        if (uri.scheme != "content") return null

        val meta = metaOf(resolver, intent, uri)
        // Установочные пакеты не принимаем ни в каком виде — отдельная явная проверка
        // поверх общего вайтлиста (имя, MIME, путь).
        if (meta.isApk()) {
            rejection = MaterialFormats.APK_MESSAGE
            return null
        }

        return when (val decision = MaterialFormats.decide(meta.name, meta.mime)) {
            is IntakeDecision.Reject -> {
                rejection = messageOf(decision)
                null
            }

            is IntakeDecision.Accept ->
                if (decision.kind == MaterialFormatKind.IMAGE) {
                    PendingMaterial.Images(listOf(uri))
                } else {
                    PendingMaterial.SingleFile(uri)
                }
        }
    }

    private fun parseMultiple(
        resolver: ContentResolver,
        intent: Intent,
    ): PendingMaterial? {
        val uris = intent.streamUris().orEmpty().filter { it.scheme == "content" }
        if (uris.isEmpty()) return null

        val limited = uris.take(MAX_FILES_PER_SHARE)
        val notes = ArrayList<String>(2)
        if (uris.size > limited.size) {
            notes += "Не больше $MAX_FILES_PER_SHARE файлов за раз"
        }

        var firstRejection: String? = null
        val accepted = ArrayList<Pair<Uri, MaterialFormatKind>>(limited.size)
        limited.forEach { uri ->
            val meta = metaOf(resolver, intent, uri)
            if (meta.isApk()) {
                if (firstRejection == null) firstRejection = MaterialFormats.APK_MESSAGE
                return@forEach
            }
            when (val decision = MaterialFormats.decide(meta.name, meta.mime)) {
                is IntakeDecision.Accept -> accepted += uri to decision.kind
                is IntakeDecision.Reject -> if (firstRejection == null) firstRejection = messageOf(decision)
            }
        }

        if (accepted.isEmpty()) {
            rejection = firstRejection ?: MaterialFormats.UNSUPPORTED_MESSAGE
            return null
        }
        if (accepted.size < limited.size) {
            notes += "Часть файлов не поддерживается"
        }

        val images = accepted.filter { it.second == MaterialFormatKind.IMAGE }
        val documents = accepted.filter { it.second != MaterialFormatKind.IMAGE }

        val material =
            if (images.isNotEmpty()) {
                if (documents.isNotEmpty()) {
                    notes += "Документы в наборе изображений не добавляются"
                }
                PendingMaterial.Images(images.map { it.first })
            } else {
                if (documents.size > 1) {
                    notes += "Добавлен только первый документ из набора"
                }
                PendingMaterial.SingleFile(documents.first().first)
            }

        if (notes.isNotEmpty()) warning = notes.joinToString(" ")
        return material
    }

    private data class UriMeta(
        val uri: Uri,
        val name: String?,
        val mime: String?,
    )

    /**
     * Имя для определения расширения: сначала DISPLAY_NAME, затем последний сегмент Uri.
     * Многие провайдеры (Telegram, WhatsApp, часть файловых менеджеров) на запрос
     * DISPLAY_NAME не отвечают, но имя файла в пути у них есть — без этого fallback'а
     * нормальный .docx с типом octet-stream отклонялся как «неопознанный».
     */
    private fun metaOf(
        resolver: ContentResolver,
        intent: Intent,
        uri: Uri,
    ): UriMeta {
        val fromProvider = displayName(resolver, uri)?.takeIf { it.isNotBlank() }
        val fromPath = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return UriMeta(uri, fromProvider ?: fromPath, intent.effectiveMime(resolver, uri))
    }

    private fun UriMeta.isApk(): Boolean =
        mime == APK_MIME ||
            name?.trim()?.endsWith(".apk", ignoreCase = true) == true ||
            uri.lastPathSegment?.endsWith(".apk", ignoreCase = true) == true

    private fun displayName(
        resolver: ContentResolver,
        uri: Uri,
    ): String? =
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        } catch (e: SecurityException) {
            accessDenied = true
            Log.w(TAG, "Провайдер закрыл доступ к имени файла: ${e.javaClass.simpleName}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Имя файла недоступно: ${e.javaClass.simpleName}")
            null
        }

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
        return try {
            resolver.getType(uri)
        } catch (e: SecurityException) {
            accessDenied = true
            Log.w(TAG, "Провайдер закрыл доступ к типу файла: ${e.javaClass.simpleName}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Тип файла недоступен: ${e.javaClass.simpleName}")
            null
        }
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