package com.politex.util

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max

sealed interface PendingMaterial {
    data class SingleFile(
        val uri: Uri,
    ) : PendingMaterial

    data class Images(
        val uris: List<Uri>,
    ) : PendingMaterial

    data class Text(
        val text: String,
    ) : PendingMaterial
}

object MaterialImporter {

    const val STORAGE_DIR_NAME = "materials"

    const val MAX_INDEXED_CHARS = 200_000

    const val MAX_IMPORT_BYTES = 50L * 1024 * 1024

    const val TOO_LARGE_MESSAGE = "Файл слишком большой — максимум 50 МБ"

    private const val MAX_FILE_NAME_LENGTH = 120

    private val INVISIBLE_CHARS =
        setOf(
            '\u200B',
            '\u200C',
            '\u200D',
            '\u200E',
            '\u200F',
            '\u202A',
            '\u202B',
            '\u202C',
            '\u202D',
            '\u202E',
            '\u2060',
            '\u2066',
            '\u2067',
            '\u2068',
            '\u2069',
            '\uFEFF',
        )

    private val PLAIN_TEXT_EXT = setOf("txt", "md", "markdown", "csv", "log", "json", "xml", "rtf")

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

    fun storageDir(context: Context): File = File(context.filesDir, STORAGE_DIR_NAME).apply { if (!exists()) mkdirs() }

    internal fun sanitizeFileName(raw: String?): String {
        val collapsed = raw.orEmpty().replace(Regex("[\\s\\u00A0]+"), " ")
        val cleaned = collapsed.filterNot { it.isISOControl() || it in INVISIBLE_CHARS }.trim()
        if (cleaned.isEmpty()) return "Файл"
        if (cleaned.length <= MAX_FILE_NAME_LENGTH) return cleaned
        val dot = cleaned.lastIndexOf('.')
        val extension = if (dot > 0) cleaned.substring(dot).takeIf { it.length in 2..10 } else null
        val bodyLength = MAX_FILE_NAME_LENGTH - (extension?.length ?: 0)
        val truncated = cleaned.take(bodyLength.coerceAtLeast(1)) + (extension ?: "")
        return truncated.trim().ifBlank { "Файл" }
    }

    fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String {
        val fromProvider =
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
            }.getOrNull()
        val raw =
            fromProvider?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return sanitizeFileName(raw)
    }

    fun querySizeHint(
        context: Context,
        uri: Uri,
    ): Long? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx) else null
            }
        }.getOrNull()

    suspend fun exceedsSizeLimit(
        context: Context,
        pending: PendingMaterial,
    ): Boolean =
        withContext(Dispatchers.IO) {
            when (pending) {
                is PendingMaterial.Text -> false
                is PendingMaterial.SingleFile -> {
                    val hint = querySizeHint(context, pending.uri)
                    hint != null && hint > MAX_IMPORT_BYTES
                }
                is PendingMaterial.Images ->
                    pending.uris.any { uri ->
                        val hint = querySizeHint(context, uri)
                        hint != null && hint > MAX_IMPORT_BYTES
                    }
            }
        }

    fun extOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase().take(6)

    fun titleOf(fileName: String): String = fileName.substringBeforeLast('.', fileName).ifBlank { fileName }

    fun extFromMime(
        context: Context,
        uri: Uri,
    ): String = when (context.contentResolver.getType(uri)) {
        "application/pdf" -> "pdf"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/msword" -> "doc"
        "text/plain" -> "txt"
        "text/csv" -> "csv"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> ""
    }

    suspend fun copyToStorage(
        context: Context,
        uri: Uri,
        fileName: String,
        digest: MessageDigest? = null,
        maxBytes: Long = MAX_IMPORT_BYTES,
    ): File = withContext(Dispatchers.IO) {
        val target = File(storageDir(context), fileName)
        try {
            val input =
                context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось открыть файл (нет доступа к выбранному документу)")
            input.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException(TOO_LARGE_MESSAGE)
                        out.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)
                    }
                }
            }
            target
        } catch (t: Throwable) {
            runCatching { target.delete() }
            throw t
        }
    }

    internal fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    internal fun newSha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

    suspend fun contentUriSha256(
        context: Context,
        uri: Uri,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val hint = querySizeHint(context, uri)
            if (hint != null && hint > MAX_IMPORT_BYTES) return@withContext null
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val digest = newSha256()
            input.use { source ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMPORT_BYTES) return@withContext null
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().toHexString()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    suspend fun contentHashFor(
        context: Context,
        pending: PendingMaterial,
    ): String? =
        when (pending) {
            is PendingMaterial.SingleFile -> contentUriSha256(context, pending.uri)
            is PendingMaterial.Images -> {
                if (pending.uris.isEmpty()) {
                    null
                } else {
                    val digest = newSha256()
                    var complete = true
                    for (uri in pending.uris) {
                        val hex = contentUriSha256(context, uri)
                        if (hex == null) {
                            complete = false
                            break
                        }
                        digest.update(hex.toByteArray(Charsets.US_ASCII))
                    }
                    if (complete) digest.digest().toHexString() else null
                }
            }
            is PendingMaterial.Text -> null
        }

    suspend fun findExistingByContent(
        context: Context,
        dao: MaterialDao,
        pending: PendingMaterial,
    ): MaterialItemEntity? {
        val hash = contentHashFor(context, pending) ?: return null
        return dao.getItemsByContentHash(hash).firstOrNull { item ->
            when (item.kindEnum) {
                MaterialKind.FILE, MaterialKind.IMAGES ->
                    item.uriPath
                        .split('\n')
                        .filter { it.isNotBlank() }
                        .all { File(it).exists() }
                MaterialKind.TEXT -> true
            }
        }
    }

    suspend fun pdfPageCount(file: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        }.getOrDefault(0)
    }

    suspend fun readTextFile(file: File): String = withContext(Dispatchers.IO) {
        runCatching { decodeSmart(file.readBytes()) }.getOrDefault("")
    }

    internal fun decodeSmart(bytes: ByteArray): String {
        runCatching {
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
        }.getOrNull()?.let { return it.toString() }
        return runCatching {
            String(
                bytes,
                java.nio.charset.Charset
                    .forName("windows-1251"),
            )
        }.getOrDefault(String(bytes))
    }

    suspend fun readDocxText(file: File): String = withContext(Dispatchers.IO) {
        runCatching {
            DocxTextExtractor.toPlainText(
                file.inputStream().use { DocxTextExtractor.extract(it) },
            )
        }.getOrDefault("")
    }

    fun deleteStoredFiles(item: MaterialItemEntity) {
        if (item.uriPath.isBlank()) return
        item.uriPath.split('\n').forEach { path ->
            if (path.isNotBlank()) runCatching { File(path).delete() }
        }
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${max(1, bytes / 1024)} КБ"
        else -> String.format(java.util.Locale.ROOT, "%.1f МБ", bytes / (1024.0 * 1024.0))
    }

    fun storedSize(item: MaterialItemEntity): Long {
        if (item.uriPath.isBlank()) return 0
        return item.uriPath.split('\n').sumOf { p -> if (p.isBlank()) 0 else runCatching { File(p).length() }.getOrDefault(0) }
    }

    suspend fun importMaterial(
        context: Context,
        dao: MaterialDao,
        subjectName: String,
        pending: PendingMaterial,
    ): Long {
        val now = System.currentTimeMillis()
        if (exceedsSizeLimit(context, pending)) throw IOException(TOO_LARGE_MESSAGE)

        return when (pending) {
            is PendingMaterial.Text -> {
                val text = pending.text.trim()
                require(text.isNotEmpty()) { "Пустой текст материала" }
                require(subjectName.isNotBlank()) { "Не выбран предмет" }
                val title =
                    text
                        .lineSequence()
                        .first { it.isNotBlank() }
                        .trim()
                        .take(60)
                        .ifBlank { "Заметка" }
                dao.insertItem(
                    MaterialItemEntity(
                        kind = MaterialKind.TEXT.name,
                        title = title,
                        ext = "txt",
                        subjectName = subjectName,
                        sortOrder = dao.nextItemSortOrder(subjectName),
                        textContent = text.take(MAX_INDEXED_CHARS),
                        createdAt = now,
                    ),
                )
            }

            is PendingMaterial.SingleFile -> {
                val name = queryDisplayName(context, pending.uri)
                val ext = extOf(name).ifBlank { extFromMime(context, pending.uri) }
                val id =
                    dao.insertItem(
                        MaterialItemEntity(
                            kind = MaterialKind.FILE.name,
                            title = titleOf(name),
                            fileName = name,
                            ext = ext,
                            subjectName = subjectName,
                            sortOrder = dao.nextItemSortOrder(subjectName),
                            createdAt = now,
                        ),
                    )
                finalizeFileImport(context, dao, id, pending.uri, ext, name)
            }

            is PendingMaterial.Images -> {
                require(pending.uris.isNotEmpty()) { "Не выбрано ни одного изображения" }
                require(subjectName.isNotBlank()) { "Не выбран предмет" }
                val firstName = queryDisplayName(context, pending.uris.first())
                val ext = extOf(firstName).ifBlank { "jpg" }.let { if (it in IMAGE_EXT) it else "jpg" }
                val title =
                    if (pending.uris.size == 1) {
                        titleOf(firstName)
                    } else {
                        "${titleOf(firstName)} (набор: ${pending.uris.size})"
                    }
                val id =
                    dao.insertItem(
                        MaterialItemEntity(
                            kind = MaterialKind.IMAGES.name,
                            title = title,
                            fileName = firstName,
                            ext = ext,
                            pageCount = pending.uris.size,
                            subjectName = subjectName,
                            sortOrder = dao.nextItemSortOrder(subjectName),
                            createdAt = now,
                        ),
                    )
                val copied = ArrayList<String>(pending.uris.size)
                try {
                    val setDigest = newSha256()
                    pending.uris.forEachIndexed { index, uri ->
                        val fileDigest = newSha256()
                        val file = copyToStorage(context, uri, "$id-$index.$ext", fileDigest)
                        copied.add(file.absolutePath)
                        setDigest.update(fileDigest.digest().toHexString().toByteArray(Charsets.US_ASCII))
                    }
                    val text = extractImageSetSearchText(firstName)
                    dao.updateItem(
                        requireNotNull(dao.getItemById(id)).copy(
                            uriPath = copied.joinToString("\n"),
                            textContent = text,
                            contentHash = setDigest.digest().toHexString(),
                        ),
                    )
                } catch (t: Throwable) {
                    rollback(dao, id, copied.joinToString("\n"))
                    throw t
                }
                id
            }
        }
    }

    private suspend fun finalizeFileImport(
        context: Context,
        dao: MaterialDao,
        itemId: Long,
        uri: Uri,
        ext: String,
        originalName: String,
    ): Long {
        var stored: File? = null
        try {
            val digest = newSha256()
            stored = copyToStorage(context, uri, "$itemId.$ext", digest)
            val pages =
                when (ext) {
                    "pdf" -> pdfPageCount(stored)
                    "docx" -> docxPageCount(stored)
                    else -> 0
                }
            val text =
                when {
                    ext == "docx" -> readDocxText(stored)
                    ext in PLAIN_TEXT_EXT -> readTextFile(stored)
                    else -> ""
                }
            val row = requireNotNull(dao.getItemById(itemId)) { "Строка материала исчезла во время импорта" }
            dao.updateItem(
                row.copy(
                    uriPath = stored.absolutePath,
                    pageCount = pages,
                    textContent = text.take(MAX_INDEXED_CHARS),
                    ext = if (ext.isBlank()) row.ext else ext,
                    title = row.title.ifBlank { titleOf(originalName) },
                    contentHash = digest.digest().toHexString(),
                ),
            )
            return itemId
        } catch (t: Throwable) {
            rollback(dao, itemId, stored?.absolutePath ?: "")
            throw t
        }
    }

    suspend fun docxPageCount(file: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            file.inputStream().use { docxPageCount(it) }
        }.getOrDefault(0)
    }

    internal fun docxPageCount(source: InputStream): Int {
        var breaks = 0
        ZipInputStream(source.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return 0
                if (entry.name == "word/document.xml") {
                    breaks = countPageBreaks(zip)
                    break
                }
                zip.closeEntry()
            }
        }
        return 1 + breaks
    }

    private fun countPageBreaks(stream: InputStream): Int {
        val parser =
            org.xmlpull.v1.XmlPullParserFactory
                .newInstance()
                .apply { isNamespaceAware = false }
                .newPullParser()
        parser.setInput(stream, "UTF-8")
        var count = 0
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (parser.name.substringAfterLast(':')) {
                    "lastRenderedPageBreak" -> count++
                    "br" -> {
                        val type = (0 until parser.attributeCount).firstOrNull { i ->
                            parser.getAttributeName(i).substringAfterLast(':') == "type"
                        }?.let { parser.getAttributeValue(it) }
                        if (type.equals("page", ignoreCase = true)) count++
                    }
                }
            }
            event = parser.next()
        }
        return count
    }

    private fun extractImageSetSearchText(firstName: String): String = titleOf(firstName)

    private suspend fun rollback(
        dao: MaterialDao,
        itemId: Long,
        uriPath: String,
    ) {
        runCatching {
            uriPath.split('\n').forEach { if (it.isNotBlank()) File(it).delete() }
            dao.deleteBookmarksForItem(itemId)
            dao.deleteItemById(itemId)
        }
    }
}
