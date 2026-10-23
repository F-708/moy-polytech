package com.politex.ui.screens.materials

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialAnnotationEntity
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun MaterialImageViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val paths = remember(item.uriPath) { item.imagePaths }

    if (paths.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().background(ColorSurfaceHighlight)) {
            MaterialViewerTopBar(title = item.title, testTag = "image_top_bar", onBack = onBack)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = "Изображения не найдены: файлы материала удалены с диска.",
                        style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BrandActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Назад",
                        testTag = "image_deleted_back",
                        filled = false,
                        onClick = onBack,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BrandActionButton(
                        icon = Icons.Default.Delete,
                        label = "Удалить запись",
                        testTag = "image_deleted_delete",
                        filled = true,
                        onClick = onDelete,
                    )
                }
            }
        }
        return
    }

    var rotation by remember(item.id) { mutableStateOf(item.rotationDeg) }
    var zoom by remember(item.id) { mutableStateOf(1f) }
    var pan by remember(item.id) { mutableStateOf(Offset.Zero) }
    val pagerState = rememberPagerState(pageCount = { paths.size })

    LaunchedEffect(pagerState.currentPage) {
        zoom = 1f
        pan = Offset.Zero
    }

    var drawMode by remember { mutableStateOf(false) }
    var drawColor by remember { mutableIntStateOf(ANNOTATION_COLORS.first()) }
    var drawWidth by remember { mutableFloatStateOf(4f) }

    var eraserActive by remember { mutableStateOf(false) }

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

    val currentPage = pagerState.currentPage

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

    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        MaterialViewerTopBar(
            title = item.title,
            testTag = "image_top_bar",
            onBack = flushAndBack,
        ) {
            ViewerAction(
                icon = Icons.Default.Edit,
                description = "Рисование",
                testTag = "image_draw_btn",
                active = drawMode,
                onClick = {
                    drawMode = !drawMode
                    if (!drawMode) eraserActive = false
                },
            )
            ViewerAction(
                icon = Icons.Default.RotateRight,
                description = "Повернуть на 90°",
                testTag = "image_rotate_btn",
                onClick = {
                    rotation = (rotation + 90) % 360
                    scope.launch { dao.setRotationDeg(item.id, rotation) }
                },
            )
            Surface(
                shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(2.dp),
                color = Color.Transparent,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${paths.size}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                        modifier = Modifier.testTag("image_page_counter"),
                    )
                }
            }
        }

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

        HorizontalPager(
            state = pagerState,
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("image_pager"),
        ) { page ->
            BoxWithConstraints(
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                var currentStroke by remember(page) { mutableStateOf<Stroke?>(null) }

                val fit =
                    if (rotation % 180 != 0 && boxW > 0f && boxH > 0f) {
                        min(boxW / boxH, boxH / boxW)
                    } else {
                        1f
                    }
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)

                                if (drawMode) return@awaitEachGesture
                                var handled = zoom > 1.05f
                                do {
                                    val event = awaitPointerEvent()
                                    if (!handled && event.changes.count { it.pressed } >= 2) handled = true
                                    if (!handled) continue
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val zooming = zoomChange != 1f
                                    val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                                    val maxX = max(0f, (newZoom - 1f) * size.width / 2f)
                                    val maxY = max(0f, (newZoom - 1f) * size.height / 2f)
                                    val newX = (pan.x + panChange.x).coerceIn(-maxX, maxX)
                                    val newY = (pan.y + panChange.y).coerceIn(-maxY, maxY)
                                    val movedY = newY != pan.y
                                    val zoomed = newZoom > 1.01f

                                    if (zooming || movedY || (zoomed && panChange.x != 0f)) {
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        if (newZoom < 1.05f) {
                                            zoom = 1f
                                            pan = Offset.Zero
                                        } else {
                                            zoom = newZoom
                                            pan = Offset(newX, newY)
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }.graphicsLayer {
                            rotationZ = rotation.toFloat()
                            scaleX = zoom * fit
                            scaleY = zoom * fit
                            translationX = pan.x
                            translationY = pan.y
                        },
                ) {
                    AsyncImage(
                        model = File(paths[page]),
                        contentDescription = "Изображение ${page + 1} из ${paths.size}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    AnnotationLayer(
                        strokes = pageStrokes[page].orEmpty(),
                        current = currentStroke,
                        drawMode = drawMode,
                        strokeColor = drawColor,
                        strokeWidthDp = drawWidth,
                        onCurrentChange = { currentStroke = it },
                        onStrokeFinished = { stroke ->
                            pageStrokes[page] = pageStrokes[page].orEmpty() + stroke
                            undoSession.record(AnnotationAction.Add(page, stroke))
                            markDirty(page)
                        },
                        eraserActive = eraserActive,
                        transformKey = rotation,

                        layerScale = zoom * fit,
                        onEraseAt = { p ->

                            if (boxW > 1f && boxH > 1f) {
                                val aspect = boxH / boxW
                                val current = pageStrokes[page].orEmpty()
                                val erased = mutableListOf<Pair<Int, Stroke>>()
                                val remaining = mutableListOf<Stroke>()
                                current.forEachIndexed { i, st ->
                                    val th = eraserThresholdNorm(st.widthDp, ERASER_TOUCH_EXTRA_DP, density.density, boxW, zoom * fit)
                                    if (eraserHitTest(st, p, th, aspect)) erased += i to st else remaining += st
                                }
                                if (erased.isNotEmpty()) {
                                    pageStrokes[page] = remaining
                                    erased.forEach { (i, st) -> undoSession.record(AnnotationAction.Erase(page, i, st)) }
                                    markDirty(page)
                                }
                            }
                        },
                        transformGestures = { panChange, zoomChange ->

                            val scale = zoom * fit
                            val rad = Math.toRadians(rotation.toDouble())
                            val cs = kotlin.math.cos(rad).toFloat()
                            val sn = kotlin.math.sin(rad).toFloat()
                            val screenPan =
                                Offset(
                                    (panChange.x * cs - panChange.y * sn) * scale,
                                    (panChange.x * sn + panChange.y * cs) * scale,
                                )
                            val newZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            val maxX = max(0f, (newZoom - 1f) * boxW / 2f)
                            val maxY = max(0f, (newZoom - 1f) * boxH / 2f)
                            if (newZoom < 1.05f) {
                                zoom = 1f
                                pan = Offset.Zero
                            } else {
                                zoom = newZoom
                                pan =
                                    Offset(
                                        (pan.x + screenPan.x).coerceIn(-maxX, maxX),
                                        (pan.y + screenPan.y).coerceIn(-maxY, maxY),
                                    )
                            }
                        },
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }

        if (zoom > 1.01f) {
            Surface(
                shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(2.dp),
                color = Color.White.copy(alpha = 0.12f),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .bouncyClickable {
                        zoom = 1f
                        pan = Offset.Zero
                    }.testTag("image_reset_zoom_btn"),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "СБРОСИТЬ МАСШТАБ",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White),
                    )
                }
            }
        }
    }
}
