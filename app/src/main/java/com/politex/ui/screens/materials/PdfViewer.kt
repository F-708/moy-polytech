package com.politex.ui.screens.materials

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialBookmarkEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@Composable
private fun CorruptedPdfPlaque(
    fileName: String,
    onBack: () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, ColorBorderLight),
            color = ColorBgMain,
            modifier =
            Modifier
                .fillMaxWidth()
                .testTag("corrupted_pdf_plaque"),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Файл не открывается",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text =
                    "Документ повреждён или защищён паролем. Встроенный просмотрщик читает PDF — " +
                        "попробуйте скачать файл заново или открыть его в другом приложении.",
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ColorTextBody),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(onClick = onBack)
                        .testTag("corrupted_pdf_back"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = "НАЗАД",
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = Tokens.ButtonText, color = Color.White),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MaterialPdfViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val file = remember(item.uriPath) { File(item.uriPath) }

    val handleState by produceState<Result<Pair<ParcelFileDescriptor, PdfRenderer>>?>(
        initialValue = null,
        key1 = file,
    ) {
        value = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pfd to PdfRenderer(pfd)
        }
    }
    val handle = handleState

    DisposableEffect(handle) {
        onDispose {
            handle?.getOrNull()?.let { (pfd, renderer) ->
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }
    }

    if (handle == null) {
        Box(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight))
        return
    }

    val opened = handle.getOrNull()
    if (opened == null) {
        Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
            PdfReaderBar(
                title = item.title,
                currentPage = 0,
                pageCount = 0,
                bookmarkListActive = false,
                drawActive = false,
                docZoomPercent = 100,
                onZoomReset = {},
                onBack = onBack,
                onPageClick = {},
                onBookmarks = {},
                onDrawToggle = {},
            )

            CorruptedPdfPlaque(
                fileName = item.fileName.ifBlank { file.name },
                onBack = onBack,
            )
        }
        return
    }

    val renderer = opened.second
    val pageCount = renderer.pageCount
    val bookmarks by dao.getBookmarksFlow(item.id).collectAsState(initial = emptyList())
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = item.lastPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        )
    val renderLock = remember(renderer) { Mutex() }
    val pageCache = remember(renderer) { newPageBitmapCache() }

    var showBookmarkList by remember { mutableStateOf(false) }
    var bookmarkDraftPage by remember { mutableStateOf<Int?>(null) }
    var showPageJump by remember { mutableStateOf(false) }

    var drawMode by remember { mutableStateOf(false) }
    var drawColor by remember { mutableIntStateOf(ANNOTATION_COLORS.first()) }
    var drawWidth by remember { mutableFloatStateOf(4f) }

    var eraserActive by remember { mutableStateOf(false) }

    val zoomState = remember(item.id) { mutableFloatStateOf(DocZoom.DEFAULT) }
    val panState = remember(item.id) { mutableStateOf(Offset.Zero) }
    var zoomJob by remember(item.id) { mutableStateOf<Job?>(null) }
    var viewportSize by remember(item.id) { mutableStateOf(IntSize.Zero) }

    fun animateZoomTo(
        target: Float,
        focal: Offset?,
    ) {
        zoomJob?.cancel()
        val z0 = zoomState.floatValue
        val t = target.coerceIn(DocZoom.MIN, DocZoom.MAX)
        if (z0 == t) return
        val p0 = panState.value
        val w = viewportSize.width.toFloat()
        val h = viewportSize.height.toFloat()
        val center = Offset(w / 2f, h / 2f)
        zoomJob =
            scope.launch {
                Animatable(z0).animateTo(t, tween(ZOOM_RESET_ANIM_MS)) {
                    val k = if (z0 > 0f) value / z0 else 1f
                    val p = if (focal != null) p0 * k + (focal - center) * (1f - k) else p0 * k
                    zoomState.floatValue = value
                    panState.value = clampPan(p, value, w, h)
                }
            }
    }

    val annotations by dao.getAnnotationsFlow(item.id).collectAsState(initial = emptyList())

    val pageStrokes = remember(item.id) { mutableStateMapOf<Int, List<Stroke>>() }
    val dirtyPages = remember(item.id) { mutableStateListOf<Int>() }
    var saveVersion by remember { mutableIntStateOf(0) }

    val undoSession = remember(item.id) { AnnotationUndoSession() }

    LaunchedEffect(annotations) {
        annotations.forEach { if (it.pageIndex !in dirtyPages) pageStrokes[it.pageIndex] = Stroke.parse(it.strokesJson) }
    }

    suspend fun persistPages(pages: List<Int>) {
        pages.forEach { p ->
            dao.upsertAnnotation(
                MaterialAnnotationEntity(
                    itemId = item.id,
                    pageIndex = p,
                    strokesJson = Stroke.serialize(pageStrokes[p].orEmpty()),
                ),
            )
        }
    }

    fun markDirty(page: Int) {
        if (page !in dirtyPages) dirtyPages += page
        saveVersion++
    }

    LaunchedEffect(saveVersion) {
        if (saveVersion == 0) return@LaunchedEffect
        delay(500)
        val pages = dirtyPages.toList()
        dirtyPages.clear()
        persistPages(pages)
    }

    var flushing by remember { mutableStateOf(false) }
    val flushAndBack: () -> Unit = {
        if (!flushing) {
            flushing = true
            val pages = dirtyPages.toList()
            dirtyPages.clear()
            scope.launch {
                try {
                    if (pages.isNotEmpty()) withContext(Dispatchers.IO) { persistPages(pages) }
                } finally {
                    onBack()
                }
            }
        }
    }
    BackHandler(enabled = dirtyPages.isNotEmpty() || flushing) { flushAndBack() }

    LaunchedEffect(listState, item.id) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page -> dao.setLastPage(item.id, page) }
    }

    val currentPage = listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
        PdfReaderBar(
            title = item.title,
            currentPage = currentPage,
            pageCount = pageCount,
            bookmarkListActive = showBookmarkList,
            drawActive = drawMode,
            docZoomPercent = (zoomState.floatValue * 100).roundToInt(),
            onZoomReset = { animateZoomTo(DocZoom.DEFAULT, null) },
            onBack = flushAndBack,
            onPageClick = { showPageJump = true },
            onBookmarks = { showBookmarkList = !showBookmarkList },
            onDrawToggle = {
                drawMode = !drawMode
                if (drawMode) showBookmarkList = false else eraserActive = false
            },
        )

        if (drawMode) {
            AnnotationToolbar(
                color = drawColor,
                widthDp = drawWidth,
                eraserActive = eraserActive,
                onEraserToggle = { eraserActive = !eraserActive },
                onColor = { drawColor = it },
                onWidth = { drawWidth = it },
                onUndo = {
                    val action = undoSession.pop()
                    if (action != null) {
                        val updated = applyUndo(action, pageStrokes.toMap())
                        updated.forEach { (p, list) ->
                            if (pageStrokes[p] != list) {
                                pageStrokes[p] = list
                                markDirty(p)
                            }
                        }
                    }
                },
                onClear = {
                    val snapshot = pageStrokes.toMap().filterValues { it.isNotEmpty() }
                    if (snapshot.isNotEmpty()) {
                        undoSession.record(AnnotationAction.ClearAll(snapshot))
                        snapshot.keys.forEach { p ->
                            pageStrokes[p] = emptyList()
                            markDirty(p)
                        }
                    }
                },
                onExit = { drawMode = false },
            )
        }

        if (showBookmarkList) {
            BookmarkPanel(
                bookmarks = bookmarks,
                onJump = { page ->
                    showBookmarkList = false
                    scope.jumpToPage(listState, page, pageCount)
                },
                onDelete = { id -> scope.launch { dao.deleteBookmarkById(id) } },
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val density = LocalDensity.current

            val renderWidthPx = with(density) { maxWidth.roundToPx() }.coerceIn(320, 2800)
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val maxHDp = maxHeight
            val center = Offset(boxW / 2f, boxH / 2f)

            val drawTransform: (Offset, Float) -> Unit = { panChange, zoomChange ->
                val z = zoomState.floatValue
                val next =
                    applyLive(
                        LiveTransform(z, panState.value),
                        zoomChange,
                        panChange * z,
                        center,
                        center,
                        DocZoom.MIN,
                        DocZoom.MAX,
                    )
                zoomState.floatValue = next.zoom
                panState.value = clampPan(next.pan, next.zoom, boxW, boxH)
            }

            LaunchedEffect(renderer, renderWidthPx, pageCount) {
                snapshotFlow { listState.firstVisibleItemIndex }
                    .collectLatest { first ->
                        neighborPages(first, pageCount, PREFETCH_RANGE)
                            .sortedBy { abs(it - first) }
                            .forEach { p ->
                                val key = pageCacheKey(p, renderWidthPx)
                                if (pageCache.get(key) == null) {
                                    val bmp =
                                        withContext(Dispatchers.IO) {
                                            renderLock.withLock { renderPageBitmap(renderer, p, renderWidthPx) }
                                        }
                                    if (bmp != null) pageCache.put(key, bmp)
                                }
                            }
                    }
            }

            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(zoomState, panState) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            zoomJob?.cancel()
                            if (drawMode) return@awaitEachGesture
                            var handled = false
                            do {
                                val event = awaitPointerEvent()
                                if (!handled && event.changes.count { it.pressed } >= 2) handled = true
                                if (!handled) continue
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val next =
                                        applyLive(
                                            LiveTransform(zoomState.floatValue, panState.value),
                                            zoomChange,
                                            panChange,
                                            centroid,
                                            c,
                                            DocZoom.MIN,
                                            DocZoom.MAX,
                                        )
                                    zoomState.floatValue = next.zoom
                                    panState.value = clampPan(next.pan, next.zoom, size.width.toFloat(), size.height.toFloat())
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(zoomState, panState) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                if (zoomState.floatValue > 1.05f) {
                                    animateZoomTo(DocZoom.DEFAULT, null)
                                } else {
                                    animateZoomTo(DOUBLE_TAP_ZOOM, tap)
                                }
                            },
                        )
                    }
                    .graphicsLayer {
                        transformOrigin = if (zoomState.floatValue < 1f) TransformOrigin(0.5f, 0f) else TransformOrigin.Center
                        scaleX = zoomState.floatValue
                        scaleY = zoomState.floatValue
                        translationX = panState.value.x
                        translationY = panState.value.y
                    },
            ) {
                val zoomOutFactor = zoomOutHeightFactor(DocZoom.MIN)
                LazyColumn(
                    state = listState,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .requiredHeight(maxHDp * zoomOutFactor)
                        .testTag("pdf_page_list"),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp + maxHDp * (zoomOutFactor - 1f)),
                ) {
                    items(count = pageCount, key = { it }) { index ->
                        PdfPageRow(
                            renderer = renderer,
                            pageIndex = index,
                            cache = pageCache,
                            renderLock = renderLock,
                            renderWidthPx = renderWidthPx,
                            zoomState = zoomState,
                            bookmarked = bookmarks.any { it.page == index },
                            onBookmarkClick = { bookmarked ->
                                if (bookmarked) {
                                    scope.launch {
                                        bookmarks.filter { it.page == index }.forEach { dao.deleteBookmarkById(it.id) }
                                    }
                                } else {
                                    bookmarkDraftPage = index
                                }
                            },
                            strokes = pageStrokes[index].orEmpty(),
                            drawMode = drawMode,
                            drawColor = drawColor,
                            drawWidthDp = drawWidth,
                            eraserActive = eraserActive,
                            onTransform = drawTransform,
                            onStrokeFinished = { stroke ->
                                pageStrokes[index] = pageStrokes[index].orEmpty() + stroke
                                undoSession.record(AnnotationAction.Add(index, stroke))
                                markDirty(index)
                            },
                            onStrokesErased = { erased, remaining ->
                                pageStrokes[index] = remaining
                                erased.forEach { (i, st) -> undoSession.record(AnnotationAction.Erase(index, i, st)) }
                                markDirty(index)
                            },
                        )
                    }
                }
            }
        }
    }

    bookmarkDraftPage?.let { page ->
        AddBookmarkDialog(
            page = page,
            onDismiss = { bookmarkDraftPage = null },
            onConfirm = { label ->
                bookmarkDraftPage = null
                scope.launch {
                    dao.insertBookmark(MaterialBookmarkEntity(itemId = item.id, page = page, label = label))
                }
            },
        )
    }

    if (showPageJump) {
        PageJumpDialog(
            pageCount = pageCount,
            onDismiss = { showPageJump = false },
            onJump = { page ->
                showPageJump = false
                scope.jumpToPage(listState, page, pageCount)
            },
        )
    }
}

private fun CoroutineScope.jumpToPage(
    state: LazyListState,
    page: Int,
    pageCount: Int,
) {
    val target = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    launch { state.scrollToItem(target) }
}

internal object DocZoom {
    const val MIN = 0.5f
    const val MAX = 4f
    const val DEFAULT = 1f

    fun apply(current: Float, multiplier: Float): Float = (current * multiplier).coerceIn(MIN, MAX)
}

internal data class LiveTransform(
    val zoom: Float,
    val pan: Offset,
)

internal fun applyLive(
    t: LiveTransform,
    zoomChange: Float,
    panChange: Offset,
    centroid: Offset,
    center: Offset,
    minZoom: Float,
    maxZoom: Float,
): LiveTransform {
    val newZoom = (t.zoom * zoomChange).coerceIn(minZoom, maxZoom)
    val k = if (t.zoom > 0f) newZoom / t.zoom else 1f
    val pan = t.pan * k + (centroid - center) * (1f - k) + panChange * k
    return LiveTransform(newZoom, pan)
}

internal fun clampPan(
    pan: Offset,
    scale: Float,
    widthPx: Float,
    heightPx: Float,
): Offset {
    val maxX = ((scale - 1f) * widthPx / 2f).coerceAtLeast(0f)
    val maxY = ((scale - 1f) * heightPx / 2f).coerceAtLeast(0f)

    val x = if (maxX == 0f) 0f else pan.x.coerceIn(-maxX, maxX)
    val y = if (maxY == 0f) 0f else pan.y.coerceIn(-maxY, maxY)
    return Offset(x, y)
}

internal const val PREFETCH_RANGE = 3

internal fun zoomOutHeightFactor(minZoom: Float): Float = if (minZoom > 0f && minZoom < 1f) 1f / minZoom else 1f

internal fun neighborPages(
    firstVisible: Int,
    total: Int,
    range: Int,
): List<Int> {
    if (total <= 0 || range < 0) return emptyList()
    val from = (firstVisible - range).coerceIn(0, total - 1)
    val to = (firstVisible + range).coerceIn(0, total - 1)
    return (from..to).toList()
}

internal fun bookmarkSizeFactor(zoom: Float): Float = zoom.coerceIn(0.8f, 1.2f)

internal const val ZOOM_RESET_ANIM_MS = 220

internal const val DOUBLE_TAP_ZOOM = 2f

internal fun Modifier.docLiveZoom(
    docZoomState: MutableFloatState,
    liveZoomState: MutableFloatState,
    livePanState: MutableState<Offset>,
    enabled: Boolean = true,
    onGestureStart: () -> Unit = {},
    onCommit: (centroid: Offset) -> Unit,
    onReset: () -> Unit = { docZoomState.floatValue = DocZoom.DEFAULT },
): Modifier = this
    .pointerInput(docZoomState, liveZoomState, livePanState, enabled) {
        if (!enabled) return@pointerInput
        val center = Offset(size.width / 2f, size.height / 2f)
        try {
            awaitEachGesture {
                var active = false
                var lastCentroid = Offset.Zero
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        if (!active) onGestureStart()
                        active = true
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid()
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            lastCentroid = centroid
                            val docZ = docZoomState.floatValue
                            val next =
                                applyLive(
                                    LiveTransform(liveZoomState.floatValue, livePanState.value),
                                    zoomChange,
                                    panChange,
                                    centroid,
                                    center,
                                    DocZoom.MIN / docZ,
                                    DocZoom.MAX / docZ,
                                )
                            liveZoomState.floatValue = next.zoom
                            livePanState.value = next.pan
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
                if (active) onCommit(lastCentroid)
            }
        } finally {
            liveZoomState.floatValue = 1f
            livePanState.value = Offset.Zero
        }
    }
    .pointerInput(docZoomState) {
        detectTapGestures(onDoubleTap = { onReset() })
    }

@Composable
private fun PdfReaderBar(
    title: String,
    currentPage: Int,
    pageCount: Int,
    bookmarkListActive: Boolean,
    drawActive: Boolean,
    docZoomPercent: Int,
    onZoomReset: () -> Unit,
    onBack: () -> Unit,
    onPageClick: () -> Unit,
    onBookmarks: () -> Unit,
    onDrawToggle: () -> Unit,
) {
    val statusBars = WindowInsets.statusBars
    Surface(

        shape = RectangleShape,
        color = ColorTopBar,
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("pdf_top_bar")
            .drawBehind {
                val top = statusBars.getTop(this).toFloat()
                if (top > 0f) drawRect(color = ColorTopBar, topLeft = Offset(0f, -top), size = Size(size.width, top))
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp).testTag("pdf_top_bar_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White,
                )
            }
            if (pageCount > 0) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(onClick = onPageClick)
                        .testTag("pdf_page_indicator"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        Text(
                            text = "${currentPage + 1}/$pageCount",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(onClick = onZoomReset)
                        .testTag("pdf_zoom_indicator"),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = "$docZoomPercent%",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                        )
                    }
                }

                ViewerAction(
                    icon = Icons.Default.Edit,
                    description = "Рисование",
                    testTag = "pdf_draw_btn",
                    active = drawActive,
                    onClick = onDrawToggle,
                )
                ViewerAction(
                    icon = Icons.Default.Bookmark,
                    description = "Закладки",
                    testTag = "pdf_bookmarks_btn",
                    active = bookmarkListActive,
                    onClick = onBookmarks,
                )
            } else {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White),
                )
            }
        }
    }
}

private fun newPageBitmapCache(): LruCache<Long, Bitmap> {
    val maxBytes = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
    return object : LruCache<Long, Bitmap>(maxBytes) {
        override fun sizeOf(
            key: Long,
            value: Bitmap,
        ): Int = value.byteCount
    }
}

@Composable
private fun PdfPageRow(
    renderer: PdfRenderer,
    pageIndex: Int,
    cache: LruCache<Long, Bitmap>,
    renderLock: Mutex,
    renderWidthPx: Int,
    zoomState: FloatState,
    bookmarked: Boolean,
    onBookmarkClick: (bookmarked: Boolean) -> Unit,
    strokes: List<Stroke>,
    drawMode: Boolean,
    drawColor: Int,
    drawWidthDp: Float,
    eraserActive: Boolean,
    onTransform: (panChange: Offset, zoomChange: Float) -> Unit,
    onStrokeFinished: (Stroke) -> Unit,
    onStrokesErased: (erased: List<Pair<Int, Stroke>>, remaining: List<Stroke>) -> Unit,
) {
    var bitmap by remember(pageIndex) { mutableStateOf(cache.get(pageCacheKey(pageIndex, renderWidthPx))) }
    var currentStroke by remember(pageIndex) { mutableStateOf<Stroke?>(null) }
    var boxSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val latestStrokes by rememberUpdatedState(strokes)

    LaunchedEffect(pageIndex, renderer, renderWidthPx) {
        val key = pageCacheKey(pageIndex, renderWidthPx)
        val cached = cache.get(key)
        if (cached != null) {
            bitmap = cached
        } else {
            val rendered =
                withContext(Dispatchers.Default) {
                    renderLock.withLock { renderPageBitmap(renderer, pageIndex, renderWidthPx) }
                }
            if (rendered != null) {
                cache.put(key, rendered)
                bitmap = rendered
            }
        }
    }

    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("pdf_page_$pageIndex")
            .onSizeChanged { boxSize = it },
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Страница ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width / bmp.height.toFloat()),
            )
        } else {
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f)
                    .background(ColorBgMain, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
            }
        }

        AnnotationLayer(
            strokes = strokes,
            current = currentStroke,
            drawMode = drawMode,
            strokeColor = drawColor,
            strokeWidthDp = drawWidthDp,
            onCurrentChange = { currentStroke = it },
            onStrokeFinished = onStrokeFinished,
            eraserActive = eraserActive,
            onEraseAt = { p ->
                val bw = boxSize.width.toFloat()
                val bh = boxSize.height.toFloat()
                if (bw > 1f && bh > 1f) {
                    val aspect = bh / bw

                    val erased = mutableListOf<Pair<Int, Stroke>>()
                    val remaining = mutableListOf<Stroke>()
                    latestStrokes.forEachIndexed { i, st ->
                        val th = eraserThresholdNorm(st.widthDp, ERASER_TOUCH_EXTRA_DP, density.density, bw, 1f)
                        if (eraserHitTest(st, p, th, aspect)) erased += i to st else remaining += st
                    }
                    if (erased.isNotEmpty()) onStrokesErased(erased, remaining)
                }
            },
            transformGestures = onTransform,

            layerScale = 1f,
            modifier = Modifier.matchParentSize(),
        )

        Box(
            modifier =
            Modifier
                .align(Alignment.TopStart)
                .size(Tokens.IconHit)
                .bouncyClickable(onClick = { onBookmarkClick(bookmarked) })
                .testTag("pdf_bookmark_add_$pageIndex"),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (bookmarked) ColorBrandBlue else ColorBgMain.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ColorBorderLight),

                modifier =
                Modifier
                    .graphicsLayer {
                        val s = bookmarkSizeFactor(zoomState.floatValue) / zoomState.floatValue.coerceAtLeast(0.01f)
                        scaleX = s
                        scaleY = s
                    }.size(26.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Закладка на странице ${pageIndex + 1}",
                        tint = if (bookmarked) Color.White else ColorBrandBlue,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBgMain.copy(alpha = 0.85f),
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .graphicsLayer {
                    val s = 1f / zoomState.floatValue.coerceAtLeast(0.01f)
                    scaleX = s
                    scaleY = s
                }.testTag("pdf_page_num_$pageIndex"),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ColorTextBody),
                )
            }
        }
    }
}

private fun renderPageBitmap(
    renderer: PdfRenderer,
    pageIndex: Int,
    widthPx: Int,
): Bitmap? = runCatching {
    renderer.openPage(pageIndex).use { page ->
        val w = widthPx.coerceAtLeast(1)
        val h = (w.toFloat() * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(AndroidColor.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }
}.getOrNull()

private fun pageCacheKey(
    pageIndex: Int,
    widthPx: Int,
): Long = (pageIndex.toLong() shl 32) or widthPx.toLong()

@Composable
internal fun BookmarkPanel(
    bookmarks: List<MaterialBookmarkEntity>,
    onJump: (Int) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("pdf_bookmark_panel"),
    ) {
        if (bookmarks.isEmpty()) {
            Text(
                text = "Закладок нет. Нажмите значок закладки на нужной странице.",
                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                items(count = bookmarks.size, key = { bookmarks[it].id }) { i ->
                    val bm = bookmarks[i]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .bouncyClickable { onJump(bm.page) }
                            .padding(start = 12.dp, end = 2.dp)
                            .testTag("pdf_bookmark_${bm.id}"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bm.label.ifBlank { "Страница ${bm.page + 1}" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle),
                            )
                            Text(
                                text = "Стр. ${bm.page + 1}",
                                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            )
                        }
                        IconButton(onClick = { onDelete(bm.id) }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить закладку",
                                tint = ColorTextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AddBookmarkDialog(
    page: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var label by remember { mutableStateOf("Страница ${page + 1}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Text(
                text = "Закладка на странице ${page + 1}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
            )
        },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Подпись", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("bookmark_label_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label.trim()) }) {
                Text("СОХРАНИТЬ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorBrandFill))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ОТМЕНА", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorTextBody))
            }
        },
    )
}

@Composable
internal fun PageJumpDialog(
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var raw by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Text(
                text = "Перейти на страницу",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
            )
        },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { v -> raw = v.filter { it.isDigit() }.take(5) },
                label = { Text("Номер (1…$pageCount)", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("page_jump_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { (raw.toIntOrNull() ?: return@TextButton).let { onJump(it - 1) } }) {
                Text("ПЕРЕЙТИ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorBrandBlue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ОТМЕНА", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ColorTextBody))
            }
        },
    )
}
