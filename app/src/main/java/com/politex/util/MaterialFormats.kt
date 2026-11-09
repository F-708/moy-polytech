package com.politex.util

/**
 * Единственный источник истины о форматах, которые «Мой Политех» умеет открывать.
 *
 * Правило продукта: принимаем ровно то, что реально рисует встроенный вьюер —
 * PDF, DOCX, текстовые файлы и изображения. Всё остальное (APK, архивы, аудио,
 * видео, таблицы, старый .doc, презентации) отклоняется на входе с понятным
 * сообщением, а не после импорта.
 *
 * Приоритет определения формата: сигнатура (magic bytes) → расширение из
 * DISPLAY_NAME/lastPathSegment → заявленный MIME (intent.type / EXTRA_MIME_TYPES /
 * resolver.getType). Сигнатура доступна только там, где можно прочитать файл
 * (импортёр), метаданные — везде.
 */
enum class MaterialFormatKind { PDF, DOCX, TEXT, IMAGE }

/** Решение гейта: либо принимаем (с нормализованным расширением), либо отказываем с текстом. */
sealed interface IntakeDecision {
    data class Accept(
        val kind: MaterialFormatKind,
        val ext: String,
    ) : IntakeDecision

    /**
     * @param message текст для пользователя (Toast/баннер)
     * @param hard true  — формат опознан как чужой (расширение, MIME или сигнатура);
     *             false — метаданных не хватило, формат просто не определён.
     */
    data class Reject(
        val message: String,
        val hard: Boolean = true,
    ) : IntakeDecision
}

object MaterialFormats {

    val TEXT_EXT: Set<String> = setOf("txt", "md", "markdown", "csv", "log", "json", "xml")

    val IMAGE_EXT: Set<String> = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    val DOC_EXT: Set<String> = setOf("pdf", "docx")

    val SUPPORTED_EXT: Set<String> = TEXT_EXT + IMAGE_EXT + DOC_EXT

    val MIME_TO_EXT: Map<String, String> =
        linkedMapOf(
            "application/pdf" to "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
            "text/plain" to "txt",
            "text/markdown" to "md",
            "text/csv" to "csv",
            "application/json" to "json",
            "application/xml" to "xml",
            "text/xml" to "xml",
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/bmp" to "bmp",
            "image/gif" to "gif",
            "image/heic" to "heic",
            "image/heif" to "heif",
        )

    /** MIME-набор для OpenDocument/OpenMultipleDocuments внутри приложения (без wildcard-типа и octet-stream). */
    val SUPPORTED_FILE_MIME_TYPES: Array<String>
        get() = MIME_TO_EXT.keys.toTypedArray()

    // ── Тексты отказов (короткие, по-русски, всегда говорят что МОЖНО) ──

    const val UNSUPPORTED_MESSAGE =
        "Формат не поддерживается. Подойдут документы и изображения."
    const val ARCHIVE_MESSAGE =
        "Архив не добавляется. Распакуйте его и добавьте нужный файл."
    const val AUDIO_VIDEO_MESSAGE =
        "Аудио и видео не добавляются. Только документы и изображения."
    const val TABLE_MESSAGE =
        "Таблицы не открываются. Сохраните лист как PDF или CSV."
    const val OLD_WORD_MESSAGE =
        "Формат .doc не читается. Сохраните файл как .docx и добавьте снова."
    const val APK_MESSAGE =
        "Установочные пакеты не добавляются."
    const val EMPTY_MESSAGE = "Файл пустой."
    const val NO_ACCESS_MESSAGE =
        "Не удалось прочитать файл. Добавьте его через «Файл»."

    private val ARCHIVE_EXT =
        setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso", "jar")
    private val APK_EXT = setOf("apk", "apks", "xapk", "aab")
    private val AUDIO_VIDEO_EXT =
        setOf(
            "mp3", "m4a", "m4v", "aac", "ogg", "oga", "opus", "wav", "flac", "amr",
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "wmv", "flv",
        )
    private val TABLE_EXT = setOf("xls", "xlsx", "xlsm", "xlsb", "ods", "numbers")
    private val OLD_WORD_EXT = setOf("doc", "dot")

    /** Форматы, которые встроенный вьюер точно не открывает, но которые не попали
     *  в остальные группы (презентации, RTF, SVG, исполняемые файлы). */
    private val OTHER_UNSUPPORTED_EXT =
        setOf(
            "rtf", "svg", "htm", "html", "odt", "odp", "ppt", "pptx", "pages", "key",
            "exe", "msi", "bat", "sh", "dmg", "ttf", "otf", "woff", "woff2",
        )

    private val KNOWN_UNSUPPORTED_EXT: Set<String> =
        ARCHIVE_EXT + APK_EXT + AUDIO_VIDEO_EXT + TABLE_EXT + OLD_WORD_EXT + OTHER_UNSUPPORTED_EXT

    private val ARCHIVE_MIME =
        setOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-tar",
            "application/java-archive",
        )
    private val TABLE_MIME =
        setOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.spreadsheet",
        )
    private const val APK_MIME = "application/vnd.android.package-archive"
    private const val OLD_WORD_MIME = "application/msword"

    /**
     * Нормализованное расширение имени файла: только [a-z0-9], не длиннее 12 символов.
     * Без обрезки до 6 символов — иначе `.markdown` превращался в `markdo` и файл
     * переставал открываться.
     */
    fun extOf(fileName: String?): String {
        val name = fileName.orEmpty()
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        val raw = name.substring(dot + 1).lowercase()
        return raw.filter { it in 'a'..'z' || it in '0'..'9' }.take(12)
    }

    fun kindOf(ext: String?): MaterialFormatKind? =
        when (ext?.lowercase()) {
            "pdf" -> MaterialFormatKind.PDF
            "docx" -> MaterialFormatKind.DOCX
            in TEXT_EXT -> MaterialFormatKind.TEXT
            in IMAGE_EXT -> MaterialFormatKind.IMAGE
            else -> null
        }

    fun extForMime(mime: String?): String? = mime?.trim()?.lowercase()?.let { MIME_TO_EXT[it] }

    /**
     * Решает, принимать ли файл.
     *
     * @param displayName имя из DISPLAY_NAME или последний сегмент Uri (может быть null/без расширения)
     * @param mime заявленный тип: intent.type, EXTRA_MIME_TYPES, resolver.getType — может врать
     * @param head первые ~8 КиБ содержимого; null — метаданные без чтения файла
     */
    fun decide(
        displayName: String?,
        mime: String?,
        head: ByteArray? = null,
    ): IntakeDecision {
        val ext = extOf(displayName)
        val declared = mime?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "*/*" }

        if (head != null) {
            decideBySignature(head, ext, declared)?.let { return it }
        }

        kindOf(ext)?.let { return IntakeDecision.Accept(it, ext) }
        if (ext in KNOWN_UNSUPPORTED_EXT) return IntakeDecision.Reject(messageForBadExt(ext))

        if (declared != null) {
            extForMime(declared)?.let { fromMime ->
                kindOf(fromMime)?.let { return IntakeDecision.Accept(it, fromMime) }
            }
            // Провайдер не дал ни имени, ни точного типа, но это заведомо картинка:
            // пусть решает декодер, а не список расширений.
            if (declared == "image/*") return IntakeDecision.Accept(MaterialFormatKind.IMAGE, "jpg")
            if (declared in ARCHIVE_MIME) return IntakeDecision.Reject(ARCHIVE_MESSAGE)
            if (declared == APK_MIME) return IntakeDecision.Reject(APK_MESSAGE)
            if (declared in TABLE_MIME) return IntakeDecision.Reject(TABLE_MESSAGE)
            if (declared == OLD_WORD_MIME) return IntakeDecision.Reject(OLD_WORD_MESSAGE)
            if (declared.startsWith("audio/") || declared.startsWith("video/")) {
                return IntakeDecision.Reject(AUDIO_VIDEO_MESSAGE)
            }
        }

        return IntakeDecision.Reject(UNSUPPORTED_MESSAGE, hard = false)
    }

    private fun messageForBadExt(ext: String): String =
        when (ext) {
            in ARCHIVE_EXT -> ARCHIVE_MESSAGE
            in APK_EXT -> APK_MESSAGE
            in AUDIO_VIDEO_EXT -> AUDIO_VIDEO_MESSAGE
            in TABLE_EXT -> TABLE_MESSAGE
            in OLD_WORD_EXT -> OLD_WORD_MESSAGE
            else -> UNSUPPORTED_MESSAGE
        }

    // ── Сигнатуры ────────────────────────────────────────────────────────────

    private fun decideBySignature(
        head: ByteArray,
        ext: String,
        mime: String?,
    ): IntakeDecision? {
        if (head.isEmpty()) return IntakeDecision.Reject(EMPTY_MESSAGE)

        if (head.pdfHeaderOffset() >= 0) return IntakeDecision.Accept(MaterialFormatKind.PDF, "pdf")

        if (head.startsWithBytes(0x89, 0x50, 0x4E, 0x47)) return IntakeDecision.Accept(MaterialFormatKind.IMAGE, "png")
        if (head.startsWithBytes(0xFF, 0xD8, 0xFF)) return IntakeDecision.Accept(MaterialFormatKind.IMAGE, "jpg")
        if (head.startsWithAscii("GIF8")) return IntakeDecision.Accept(MaterialFormatKind.IMAGE, "gif")
        // "BM" — только два байта, поэтому верим им лишь когда имя тоже про BMP:
        // иначе любой текст, начинающийся с "BM", превращался в «картинку».
        if (head.startsWithAscii("BM") && (ext.isEmpty() || ext == "bmp")) {
            return IntakeDecision.Accept(MaterialFormatKind.IMAGE, "bmp")
        }
        if (head.riffForm("WEBP")) return IntakeDecision.Accept(MaterialFormatKind.IMAGE, "webp")
        if (head.isoBmffBrand(HEIF_BRANDS)) return IntakeDecision.Accept(MaterialFormatKind.IMAGE, "heic")

        // ZIP-контейнеры: docx, apk, odt/ods/odp, pptx, xlsx, обычный архив.
        // Отличить их можно только по оглавлению архива, поэтому решает имя/тип.
        if (head.startsWithBytes(0x50, 0x4B, 0x03, 0x04) || head.startsWithBytes(0x50, 0x4B, 0x05, 0x06)) {
            return when (ext) {
                "docx" -> IntakeDecision.Accept(MaterialFormatKind.DOCX, "docx")
                in APK_EXT -> IntakeDecision.Reject(APK_MESSAGE)
                else -> IntakeDecision.Reject(ARCHIVE_MESSAGE)
            }
        }

        // OLE2: Word 97 (.doc) и Excel 97 (.xls)
        if (head.startsWithBytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return if (ext.startsWith("xls") || mime in TABLE_MIME) {
                IntakeDecision.Reject(TABLE_MESSAGE)
            } else {
                IntakeDecision.Reject(OLD_WORD_MESSAGE)
            }
        }

        if (head.startsWithAscii("ID3") ||
            head.startsWithBytes(0xFF, 0xFB) ||
            head.startsWithBytes(0xFF, 0xF3) ||
            head.startsWithBytes(0xFF, 0xF2) ||
            head.startsWithAscii("OggS") ||
            head.startsWithAscii("fLaC") ||
            head.startsWithAscii("#!AMR") ||
            head.riffForm("WAVE") ||
            head.riffForm("AVI ") ||
            head.startsWithBytes(0x1A, 0x45, 0xDF, 0xA3) ||
            head.isoBmffBrand(VIDEO_BRANDS)
        ) {
            return IntakeDecision.Reject(AUDIO_VIDEO_MESSAGE)
        }

        if (head.startsWithBytes(0x7F, 0x45, 0x4C, 0x46) || head.startsWithAscii("dex\n")) {
            return IntakeDecision.Reject(APK_MESSAGE)
        }

        return null
    }

    private val HEIF_BRANDS =
        arrayOf("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1")

    private val VIDEO_BRANDS =
        arrayOf("isom", "iso2", "mp41", "mp42", "avc1", "M4V ", "qt  ", "3gp4", "3gp5", "M4A ")

    private fun ByteArray.startsWithBytes(vararg expected: Int): Boolean {
        if (size < expected.size) return false
        for (i in expected.indices) {
            if ((this[i].toInt() and 0xFF) != expected[i]) return false
        }
        return true
    }

    private fun ByteArray.startsWithAscii(text: String): Boolean {
        if (size < text.length) return false
        for (i in text.indices) {
            if ((this[i].toInt() and 0xFF) != text[i].code) return false
        }
        return true
    }

    private fun ByteArray.asciiAt(offset: Int, length: Int): String? {
        if (size < offset + length) return null
        return String(this, offset, length, Charsets.US_ASCII)
    }

    /** RIFF-контейнер (`RIFF....FORM`): WAVE, AVI, WEBP. */
    private fun ByteArray.riffForm(form: String): Boolean {
        if (!startsWithAscii("RIFF")) return false
        return asciiAt(8, 4)?.equals(form, ignoreCase = false) == true
    }

    /** ISO BMFF: `....ftypBRND` — HEIC/HEIF, MP4, M4A, MOV. */
    private fun ByteArray.isoBmffBrand(brands: Array<String>): Boolean {
        if (asciiAt(4, 4) != "ftyp") return false
        val brand = asciiAt(8, 4) ?: return false
        return brands.any { it == brand }
    }

    /** `%PDF` в первых 1024 байтах: некоторые генераторы пишут перед заголовком мусор. */
    private fun ByteArray.pdfHeaderOffset(): Int {
        val marker = "%PDF".toByteArray(Charsets.US_ASCII)
        val last = minOf(size, PDF_HEADER_WINDOW) - marker.size
        for (start in 0..last) {
            var matched = true
            for (i in marker.indices) {
                if ((this[start + i].toInt() and 0xFF) != (marker[i].toInt() and 0xFF)) {
                    matched = false
                    break
                }
            }
            if (matched) return start
        }
        return -1
    }

    private const val PDF_HEADER_WINDOW = 1024
}