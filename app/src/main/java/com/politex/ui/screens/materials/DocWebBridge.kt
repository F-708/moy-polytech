package com.politex.ui.screens.materials

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.webkit.WebViewCompat
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile

internal const val ASSET_HOST = "appassets.androidplatform.net"

internal object ViewerPolicy {
    const val CSP =
        "default-src 'none'; " +
            "script-src 'self' 'wasm-unsafe-eval'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: blob:; " +
            "font-src 'self' data: blob:; " +
            "media-src 'self' data: blob:; " +
            "connect-src 'self'; " +
            "worker-src 'self'; " +
            "frame-src 'none'; " +
            "object-src 'none'; " +
            "form-action 'none'; " +
            "base-uri 'none'"
}

internal const val RANGE_THRESHOLD_BYTES = 16L * 1024 * 1024

internal fun useRanges(total: Long): Boolean = total >= RANGE_THRESHOLD_BYTES

internal fun parseRange(header: String, total: Long): Pair<Long, Long>? {
    if (total <= 0) return null
    val spec = header.substringAfter("bytes=", "").substringBefore(',').trim()
    if (spec.isEmpty()) return null
    val start = spec.substringBefore('-').trim().toLongOrNull() ?: return null
    val end = spec.substringAfter('-').trim().toLongOrNull() ?: (total - 1)
    if (start < 0 || start > end || start >= total) return null
    return start to minOf(end, total - 1)
}

internal fun documentMime(ext: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

internal class LimitedInputStream(
    private val source: InputStream,
    private var remaining: Long,
    private val alsoClose: Closeable,
) : InputStream() {
    override fun read(): Int {
        if (remaining <= 0) return -1
        return source.read().also { if (it >= 0) remaining-- }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val n = source.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (n > 0) remaining -= n
        return n
    }

    override fun available(): Int = minOf(source.available().toLong(), remaining).toInt()

    override fun close() {
        runCatching { source.close() }
        runCatching { alsoClose.close() }
    }
}

internal fun fileSlice(file: File, start: Long, end: Long): InputStream {
    val raf = RandomAccessFile(file, "r")
    raf.seek(start)
    val input = object : InputStream() {
        override fun read(): Int = raf.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
    }
    return LimitedInputStream(input, end - start + 1, raf)
}

internal const val PDFJS_MIN_CHROMIUM = 125
internal const val DOCX_PREVIEW_MIN_CHROMIUM = 80
internal const val PLAUSIBLE_CHROMIUM_MAJOR = 30
internal val CHROME_TOKEN = Regex("""Chrome/(\d+)""")

internal val LOCKED_WEBVIEW_PACKAGES = setOf("com.huawei.webview")

internal fun chromiumMajor(userAgent: String?, packageVersionName: () -> String?): Int? {
    val fromUa = userAgent
        ?.let { CHROME_TOKEN.find(it) }
        ?.groupValues?.get(1)
        ?.toIntOrNull()
        ?.takeIf { it >= PLAUSIBLE_CHROMIUM_MAJOR }
    return fromUa ?: packageVersionName()
        ?.substringBefore('.')
        ?.toIntOrNull()
        ?.takeIf { it >= PLAUSIBLE_CHROMIUM_MAJOR }
}

internal fun webViewEngineInfo(context: Context): Pair<Int?, Boolean> {
    val pkg = runCatching { WebViewCompat.getCurrentWebViewPackage(context) }.getOrNull()
    return chromiumMajor(null) { pkg?.versionName } to (pkg?.packageName in LOCKED_WEBVIEW_PACKAGES)
}

internal fun pdfRouteWeb(fileExists: Boolean, pageCount: Int, major: Int?, locked: Boolean): Boolean =
    fileExists && pageCount > 0 && !locked && major != null && major >= PDFJS_MIN_CHROMIUM

internal fun docxRouteWeb(textEdited: Boolean, fileExists: Boolean, major: Int?, locked: Boolean): Boolean {
    if (textEdited || !fileExists) return false
    return if (major == null) !locked else major >= DOCX_PREVIEW_MIN_CHROMIUM
}

internal fun pageToPort(zeroBased: Int): Int = zeroBased + 1

internal fun pageFromPort(oneBased: Int): Int = oneBased - 1

internal object PortCommand {
    fun query(q: String) = "q$q"
    fun next() = "n"
    fun prev() = "p"
    fun clear() = "c"

    fun goToPage(n: Int) = "g$n"

    fun nightMode(on: Boolean) = if (on) "i1" else "i0"
}

internal sealed interface PortMessage {

    data class Page(val n: Int, val of: Int) : PortMessage

    data class SearchCount(val at: Int, val total: Int, val done: Boolean) : PortMessage

    data object Locked : PortMessage
}

internal fun parsePortMessage(data: String?): PortMessage? {
    if (data == "locked") return PortMessage.Locked
    val said = data?.split(" ") ?: return null
    if (said.size != 3) return null

    if (said[0] == "page") {
        val n = said[1].toIntOrNull() ?: return null
        val of = said[2].toIntOrNull() ?: return null
        if (n < 1 || of < 1 || n > of) return null
        return PortMessage.Page(n, of)
    }

    val at = said[0].toIntOrNull() ?: return null
    val total = said[1].toIntOrNull() ?: return null
    if (at < 0 || total < 0) return null
    return PortMessage.SearchCount(at, total, said[2] == "1")
}
