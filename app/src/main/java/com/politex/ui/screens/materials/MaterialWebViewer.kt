package com.politex.ui.screens.materials

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import androidx.compose.ui.text.style.TextOverflow

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MaterialWebViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    onFallbackNative: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isPdf = item.ext == "pdf"

    val canBookmark = isPdf || item.ext == "docx"

    var pageAt by remember(item.id) { mutableIntStateOf(0) }
    var pageTotal by remember(item.id) { mutableIntStateOf(0) }
    var showBookmarkList by remember(item.id) { mutableStateOf(false) }
    var showAddBookmark by remember(item.id) { mutableStateOf(false) }
    var showPageJump by remember(item.id) { mutableStateOf(false) }
    var rendererGone by remember(item.id) { mutableStateOf(false) }

    val bookmarksFlow: Flow<List<MaterialBookmarkEntity>> =
        remember(item.id) { if (canBookmark) dao.getBookmarksFlow(item.id) else flowOf(emptyList()) }
    val bookmarks by bookmarksFlow.collectAsState(initial = emptyList())

    LaunchedEffect(rendererGone) { if (rendererGone) onFallbackNative() }

    val portRef = remember(item.id) { arrayOfNulls<WebMessagePortCompat>(1) }
    val webRef = remember(item.id) { arrayOfNulls<WebView>(1) }

    fun send(cmd: String) {
        runCatching { portRef[0]?.postMessage(WebMessageCompat(cmd)) }
    }

    fun jumpTo(page0: Int) {
        send(PortCommand.goToPage(pageToPort(page0)))
    }

    fun handlePort(data: String?) {
        when (val said = parsePortMessage(data)) {
            is PortMessage.Page -> {
                pageAt = pageFromPort(said.n)
                pageTotal = said.of
                scope.launch { dao.setLastPage(item.id, pageFromPort(said.n)) }
            }

            PortMessage.Locked -> Unit
            is PortMessage.SearchCount -> Unit
            null -> Unit
        }
    }

    fun openPort(w: WebView) {
        if (portRef[0] != null) return
        val ends = WebViewCompat.createWebMessageChannel(w)
        ends[0].setWebMessageCallback(
            Handler(Looper.getMainLooper()),
            object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                    handlePort(message?.data)
                }
            },
        )
        portRef[0] = ends[0]

        WebViewCompat.postWebMessage(w, WebMessageCompat("vw-search-port", arrayOf(ends[1])), Uri.parse("https://$ASSET_HOST"))
    }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        MaterialViewerTopBar(title = item.title, testTag = if (isPdf) "web_pdf_top_bar" else "web_docx_top_bar", onBack = onBack) {
            if (pageTotal > 0) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    modifier =
                        Modifier
                            .heightIn(min = 44.dp)
                            .bouncyClickable(onClick = { showPageJump = true })
                            .testTag("web_page_indicator"),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            "${pageAt + 1}/$pageTotal",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                        )
                    }
                }
            }
            if (canBookmark) {
                ViewerAction(
                    icon = Icons.Default.BookmarkAdd,
                    description = "Закладка на этой странице",
                    testTag = "web_add_bookmark_btn",
                ) { showAddBookmark = true }
                ViewerAction(
                    icon = Icons.Default.Bookmark,
                    description = "Закладки",
                    testTag = "web_bookmarks_btn",
                    active = showBookmarkList,
                ) { showBookmarkList = !showBookmarkList }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            key(item.id) {
                AndroidView(
                    factory = { ctx -> createDocWebView(ctx, item, isPdf, canBookmark, webRef, ::openPort) { rendererGone = true } },
                    onRelease = { v ->
                        runCatching { portRef[0]?.close() }
                        portRef[0] = null
                        runCatching { (v.parent as? ViewGroup)?.removeView(v) }
                        runCatching { v.destroy() }
                        webRef[0] = null
                    },
                    modifier = Modifier.fillMaxSize().testTag(if (isPdf) "web_pdf_view" else "web_docx_view"),
                )
            }

            if (showBookmarkList && canBookmark) {
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                    BookmarkPanel(
                        bookmarks = bookmarks,
                        onJump = { page ->
                            showBookmarkList = false
                            jumpTo(page)
                        },
                        onDelete = { id -> scope.launch { dao.deleteBookmarkById(id) } },
                    )
                }
            }
        }
    }

    if (showPageJump && pageTotal > 0) {
        PageJumpDialog(
            pageCount = pageTotal,
            onDismiss = { showPageJump = false },
            onJump = { page0 ->
                showPageJump = false
                jumpTo(page0)
            },
        )
    }

    if (showAddBookmark) {
        AddBookmarkDialog(
            page = pageAt,
            onDismiss = { showAddBookmark = false },
            onConfirm = { label ->
                showAddBookmark = false
                scope.launch { dao.insertBookmark(MaterialBookmarkEntity(itemId = item.id, page = pageAt, label = label)) }
            },
        )
    }
}

private fun createDocWebView(
    ctx: android.content.Context,
    item: MaterialItemEntity,
    isPdf: Boolean,
    canBookmark: Boolean,
    webRef: Array<WebView?>,
    openPort: (WebView) -> Unit,
    onRendererGone: () -> Unit,
): WebView {
    val file = File(item.uriPath)
    val mime = documentMime(item.ext)

    return WebView(ctx).apply {

        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = false
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            loadWithOverviewMode = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false

            @Suppress("DEPRECATION")
            setAllowFileAccessFromFileURLs(false)

            @Suppress("DEPRECATION")
            setAllowUniversalAccessFromFileURLs(false)

            if (isPdf) minimumFontSize = 1
        }
        CookieManager.getInstance().also { cm ->
            cm.setAcceptCookie(false)
            cm.setAcceptThirdPartyCookies(this, false)
        }

        val assetLoader =
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .build()

        webViewClient =
            object : WebViewClientCompat() {
                override fun onPageFinished(view: WebView, url: String) {

                    if (canBookmark) openPort(view)
                }

                override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): WebResourceResponse? {
                    val url = request.url
                    if (url.scheme == "https" && url.host == ASSET_HOST) {
                        val path = url.path ?: return webNotFound()
                        if (path == "/doc/file." + item.ext) return docResponse(file, mime, request.requestHeaders["Range"])

                        if (path.startsWith("/assets/viewer/") && ".." !in path) {
                            return assetLoader.shouldInterceptRequest(url)?.withViewerPolicy() ?: webNotFound()
                        }
                        return webNotFound()
                    }

                    return if (url.scheme == "data" || url.scheme == "blob") null else webNotFound()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                    val url = request.url
                    if (isViewerPage(url)) return false

                    if (url.scheme == "http" || url.scheme == "https" || url.scheme == "mailto" || url.scheme == "tel") {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    return true
                }

                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {

                    if (webRef[0] === view) {
                        webRef[0] = null
                        runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                        view.destroy()
                        onRendererGone()
                    }
                    return true
                }
            }

        webRef[0] = this
        loadUrl(buildDocUrl(item, file))
    }
}

private fun buildDocUrl(item: MaterialItemEntity, file: File): String {
    val total = file.length()
    val ranged = if (useRanges(total)) 1 else 0

    val resume = if (item.ext == "pdf" && item.lastPage > 0) pageToPort(item.lastPage) else 0
    return "https://$ASSET_HOST/assets/viewer/" + (if (item.ext == "pdf") "pdf.html" else "docx.html") +
        "?name=${Uri.encode(item.fileName.ifBlank { item.title })}&ext=${Uri.encode(item.ext)}&ranged=$ranged" +
        (if (total > 0) "&length=$total" else "") +
        "&night=0" +
        (if (resume > 1) "&resume=$resume" else "")
}

private fun docResponse(file: File, mime: String, range: String?): WebResourceResponse =
    try {
        val total = file.length()
        val rangeable = useRanges(total)
        val span = if (rangeable) range?.let { parseRange(it, total) } else null
        val nosniff = "X-Content-Type-Options" to "nosniff"
        if (span == null) {
            val headers = mutableMapOf(nosniff)
            if (rangeable) headers["Accept-Ranges"] = "bytes"
            if (total >= 0) headers["Content-Length"] = total.toString()
            WebResourceResponse(mime, null, 200, "OK", headers, file.inputStream())
        } else {
            val (start, end) = span
            WebResourceResponse(
                mime,
                null,
                206,
                "Partial Content",
                mapOf(
                    nosniff,
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes $start-$end/$total",
                    "Content-Length" to (end - start + 1).toString(),
                ),
                fileSlice(file, start, end),
            )
        }
    } catch (e: Exception) {
        webNotFound()
    }

private fun WebResourceResponse.withViewerPolicy(): WebResourceResponse =
    apply { responseHeaders = responseHeaders.orEmpty() + ("Content-Security-Policy" to ViewerPolicy.CSP) }

private fun webNotFound(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, ByteArrayInputStream(ByteArray(0)))

private fun isViewerPage(url: Uri): Boolean {
    val path = url.path ?: return false
    return url.scheme == "https" && url.host == ASSET_HOST &&
        path.startsWith("/assets/viewer/") && path.endsWith(".html") && ".." !in path
}
