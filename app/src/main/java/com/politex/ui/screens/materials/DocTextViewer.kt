package com.politex.ui.screens.materials

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.dao.MaterialDao
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.data.local.entity.MaterialKind
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.DocxParagraph
import com.politex.util.DocxTextExtractor
import com.politex.util.MaterialImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.ceil
import kotlin.math.roundToInt

private val TEXT_LIKE_EXT = setOf("txt", "md", "markdown", "csv", "log", "json", "xml")

internal const val A4_RATIO = 1.414f
internal const val DOC_SHEET_GAP_DP = 8
internal const val DOC_SHEET_MARGIN_DP = 24

internal fun pageHeightFromWidth(widthPx: Float): Float = widthPx * A4_RATIO

internal fun sheetCount(textHeightPx: Float, pageHeightPx: Float, gapPx: Float): Int {
    if (textHeightPx <= 0f || pageHeightPx <= 0f) return 1
    return ceil((textHeightPx + gapPx) / (pageHeightPx + gapPx)).toInt().coerceAtLeast(1)
}

@Composable
fun MaterialDocTextViewer(
    item: MaterialItemEntity,
    dao: MaterialDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var content by remember(item.id, item.uriPath) { mutableStateOf<List<DocxParagraph>?>(null) }
    var failed by remember(item.id, item.uriPath) { mutableStateOf(false) }

    LaunchedEffect(item.id, item.uriPath, item.kind, item.textEdited) {
        content = withContext(Dispatchers.IO) { loadContent(item) }
        failed = content == null
    }

    var editMode by remember(item.id) { mutableStateOf(false) }
    var textValue by remember(item.id) { mutableStateOf(TextFieldValue()) }

    var originalText by remember(item.id) { mutableStateOf("") }
    var showUnsavedDialog by remember(item.id) { mutableStateOf(false) }
    val undoStack = remember(item.id) { ArrayDeque<String>() }
    val redoStack = remember(item.id) { ArrayDeque<String>() }

    val docZoomState = remember(item.id) { mutableFloatStateOf(DocZoom.DEFAULT) }
    val docPanState = remember(item.id) { mutableStateOf(Offset.Zero) }
    val liveZoomState = remember(item.id) { mutableFloatStateOf(1f) }
    val livePanState = remember(item.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(item.id) { mutableStateOf(IntSize.Zero) }

    fun resetZoomSmooth() {
        val fromZoom = docZoomState.floatValue
        val fromPan = docPanState.value
        if (fromZoom == DocZoom.DEFAULT && fromPan == Offset.Zero) return
        scope.launch {
            Animatable(0f).animateTo(1f, tween(ZOOM_RESET_ANIM_MS)) {
                docZoomState.floatValue = fromZoom + (DocZoom.DEFAULT - fromZoom) * value
                docPanState.value = fromPan * (1f - value)
            }
        }
    }

    fun commitLiveTransform() {
        val liveZoom = liveZoomState.floatValue
        val livePan = livePanState.value
        if (liveZoom == 1f && livePan.x == 0f && livePan.y == 0f) return
        val newScale = DocZoom.apply(docZoomState.floatValue, liveZoom)
        docZoomState.floatValue = newScale
        docPanState.value =
            clampPan(
                docPanState.value * liveZoom + livePan,
                newScale,
                containerSize.width.toFloat(),
                containerSize.height.toFloat(),
            )
        liveZoomState.floatValue = 1f
        livePanState.value = Offset.Zero
    }

    fun startEdit() {
        val initial = content?.joinToString("\n") { it.text } ?: item.textContent
        textValue = TextFieldValue(initial)
        originalText = initial
        undoStack.clear()
        redoStack.clear()
        editMode = true
    }

    fun requestExitEdit() {
        if (textValue.text != originalText) showUnsavedDialog = true else editMode = false
    }

    fun snapshot() {
        if (textValue.text.length > 100_000) return
        undoStack.addLast(textValue.text)
        if (undoStack.size > 10) undoStack.removeFirst()
        redoStack.clear()
    }

    fun wrapSelection(marker: String) {
        snapshot()
        val t = textValue.text
        val sel = textValue.selection
        val newText = t.substring(0, sel.start) + marker + t.substring(sel.start, sel.end) + marker + t.substring(sel.end)
        textValue = TextFieldValue(newText, selection = TextRange(sel.start + marker.length, sel.end + marker.length))
    }

    fun bulletLine() {
        snapshot()
        val t = textValue.text
        val cursor = textValue.selection.min
        val lineStart = if (cursor == 0) 0 else t.lastIndexOf('\n', cursor - 1) + 1
        textValue = TextFieldValue(t.substring(0, lineStart) + "- " + t.substring(lineStart), selection = TextRange(cursor + 2))
    }

    fun numberLine() {
        snapshot()
        val t = textValue.text
        val cursor = textValue.selection.min
        val lineStart = if (cursor == 0) 0 else t.lastIndexOf('\n', cursor - 1) + 1
        textValue = TextFieldValue(t.substring(0, lineStart) + "1. " + t.substring(lineStart), selection = TextRange(cursor + 3))
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(textValue.text)
        if (redoStack.size > 10) redoStack.removeFirst()
        textValue = TextFieldValue(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(textValue.text)
        if (undoStack.size > 10) undoStack.removeFirst()
        textValue = TextFieldValue(next)
    }

    fun save() {
        val text = textValue.text
        scope.launch { dao.updateTextContent(item.id, text) }

        content = text.split('\n').map { DocxParagraph(it.trimEnd()) }
        failed = false
        editMode = false
    }

    val canEdit = item.kindEnum == MaterialKind.TEXT || item.ext in TEXT_LIKE_EXT

    BackHandler(enabled = editMode) { requestExitEdit() }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        MaterialViewerTopBar(title = item.title, testTag = "doc_top_bar", onBack = { if (editMode) requestExitEdit() else onBack() }) {
            if (!editMode) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .bouncyClickable(onClick = { resetZoomSmooth() })
                        .testTag("doc_zoom_indicator"),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            "${(docZoomState.floatValue * 100).roundToInt()}%",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                        )
                    }
                }
            }
            if (canEdit) {
                ViewerAction(
                    icon = Icons.Default.Edit,
                    description = "Редактировать текст",
                    testTag = "doc_edit_btn",
                    active = editMode,
                    onClick = { if (editMode) requestExitEdit() else startEdit() },
                )
            }
        }

        if (editMode) {
            TextEditToolbar(
                onBold = { wrapSelection("**") },
                onItalic = { wrapSelection("*") },
                onUnderline = { wrapSelection("__") },
                onBullet = ::bulletLine,
                onNumber = ::numberLine,
                onUndo = ::undo,
                onRedo = ::redo,
                onSave = ::save,
            )
            TextField(
                value = textValue,
                onValueChange = { next ->
                    snapshot()
                    textValue = next
                },
                modifier = Modifier.fillMaxSize().testTag("doc_edit_field"),
                textStyle = TextStyle(fontSize = 14.sp, color = ColorTextBody, lineHeight = 19.sp),
            )
        } else {
            val doc = content
            when {
                failed -> UnsupportedFormatPlaque(ext = item.ext, fileName = item.fileName.ifBlank { item.title })

                doc == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorBrandBlue, modifier = Modifier.testTag("doc_loading"))
                }

                doc.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = "Текст не найден: документ пустой или не читается встроенным просмотрщиком.",
                            style = TextStyle(fontSize = 13.sp),
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier.bouncyClickable(onClick = onBack).testTag("doc_empty_back"),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    "НАЗАД",
                                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                                )
                            }
                        }
                    }
                }

                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .background(ColorSurfaceHighlight)
                        .onSizeChanged { containerSize = it }
                        .docLiveZoom(
                            docZoomState = docZoomState,
                            liveZoomState = liveZoomState,
                            livePanState = livePanState,
                            onCommit = { commitLiveTransform() },
                            onReset = { resetZoomSmooth() },
                        ),
                ) {
                    val density = LocalDensity.current
                    val scrollState = rememberScrollState()
                    var textHeightPx by remember(item.id) { mutableIntStateOf(0) }
                    var sheetWidthPx by remember(item.id) { mutableIntStateOf(0) }
                    val gapPx = with(density) { DOC_SHEET_GAP_DP.dp.toPx() }
                    val pageHeightPx = pageHeightFromWidth(sheetWidthPx.toFloat())
                    val sheets = sheetCount(textHeightPx.toFloat(), pageHeightPx, gapPx)
                    val pageHeightDp = with(density) { pageHeightPx.toDp() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val live = liveZoomState.floatValue
                                val doc = docZoomState.floatValue
                                scaleX = doc * live
                                scaleY = doc * live
                                translationX = docPanState.value.x * live + livePanState.value.x
                                translationY = docPanState.value.y * live + livePanState.value.y
                            }
                            .verticalScroll(scrollState)
                            .testTag("doc_paragraph_list"),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            repeat(sheets) { i ->
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = Color.White,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(pageHeightDp)
                                        .testTag("doc_sheet_$i"),
                                ) {}
                                if (i < sheets - 1) Spacer(modifier = Modifier.height(DOC_SHEET_GAP_DP.dp))
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged {
                                    textHeightPx = it.height
                                    sheetWidthPx = it.width
                                }
                                .padding(horizontal = DOC_SHEET_MARGIN_DP.dp, vertical = DOC_SHEET_MARGIN_DP.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            doc.forEachIndexed { index, p ->
                                Text(
                                    text = parseInlineMarks(p.text),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (p.heading) Modifier.padding(top = 10.dp) else Modifier)
                                        .testTag("doc_paragraph_$index"),
                                    style = when {
                                        p.heading -> TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle, lineHeight = 21.sp)
                                        p.bold -> TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ColorTextTitle, lineHeight = 19.sp)
                                        else -> TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = ColorTextBody, lineHeight = 19.sp)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            shape = RoundedCornerShape(2.dp),
            containerColor = ColorBgMain,
            title = {
                Text(
                    "Правки не сохранены",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
                )
            },
            text = {
                Text(
                    "Сохранить изменения перед выходом?",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        save()
                    },
                    modifier = Modifier.testTag("doc_unsaved_save"),
                ) {
                    Text("СОХРАНИТЬ", style = TextStyle(fontWeight = FontWeight.Bold, color = ColorBrandFill))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        editMode = false
                    },
                    modifier = Modifier.testTag("doc_unsaved_discard"),
                ) {
                    Text("ВЫЙТИ БЕЗ СОХРАНЕНИЯ", style = TextStyle(fontWeight = FontWeight.Bold, color = ColorTextBody))
                }
            },
        )
    }
}

@Composable
private fun TextEditToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onBullet: () -> Unit,
    onNumber: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceHighlight,
        modifier = Modifier.fillMaxWidth().testTag("doc_edit_toolbar"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            EditMarkButton("B", "doc_bold_btn", TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle), onBold)
            EditMarkButton("I", "doc_italic_btn", TextStyle(fontStyle = FontStyle.Italic, fontSize = 15.sp, color = ColorTextTitle), onItalic)
            EditMarkButton(
                "U",
                "doc_underline_btn",
                TextStyle(textDecoration = TextDecoration.Underline, fontSize = 15.sp, color = ColorTextTitle),
                onUnderline,
            )
            ToolbarDivider()
            EditMarkButton("•", "doc_bullet_btn", TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle), onBullet)
            EditMarkButton("1.", "doc_number_btn", TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorTextTitle), onNumber)
            ToolbarDivider()
            EditMarkIcon(Icons.AutoMirrored.Filled.Undo, "Отменить", "doc_undo_btn", onUndo)
            EditMarkIcon(Icons.AutoMirrored.Filled.Redo, "Повторить", "doc_redo_btn", onRedo)
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier = Modifier.size(width = 104.dp, height = 36.dp).bouncyClickable(onClick = onSave).testTag("doc_save_btn"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("СОХРАНИТЬ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = Tokens.ButtonText, color = Color.White))
                }
            }
        }
    }
}

@Composable
private fun EditMarkButton(
    label: String,
    tag: String,
    style: TextStyle,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = Modifier.size(Tokens.IconHit).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, style = style) }
    }
}

@Composable
private fun EditMarkIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = Color.Transparent,
        modifier = Modifier.size(Tokens.IconHit).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, tint = ColorTextBody, modifier = Modifier.size(18.dp))
        }
    }
}

internal fun parseInlineMarks(par: String): AnnotatedString {
    val s = if (par.startsWith("- ")) "• " + par.substring(2) else par
    return buildAnnotatedString {
        var i = 0
        while (i < s.length) {
            when {
                s.startsWith("**", i) -> {
                    val end = s.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(s[i])
                        i++
                    }
                }
                s[i] == '*' -> {
                    val end = s.indexOf('*', i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(s[i])
                        i++
                    }
                }
                s.startsWith("__", i) -> {
                    val end = s.indexOf("__", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(s.substring(i + 2, end)) }
                        i = end + 2
                    } else {
                        append(s[i])
                        i++
                    }
                }
                else -> {
                    append(s[i])
                    i++
                }
            }
        }
    }
}

private fun loadContent(item: MaterialItemEntity): List<DocxParagraph>? {
    if (item.textEdited) {
        return item.textContent.split('\n').map { DocxParagraph(it.trimEnd()) }
    }

    if (item.kindEnum == MaterialKind.TEXT) {
        if (item.textContent.isBlank()) return emptyList()
        return item.textContent.split('\n').map { DocxParagraph(it.trimEnd()) }
    }

    if (item.uriPath.isBlank()) return null
    val file = File(item.uriPath)
    if (!file.exists()) return null

    return when (item.ext) {
        "docx" -> runCatching { DocxTextExtractor.extract(ByteArrayInputStream(file.readBytes())) }.getOrNull()

        in TEXT_LIKE_EXT -> runCatching {
            MaterialImporter.decodeSmart(file.readBytes()).split('\n').map { DocxParagraph(it.trimEnd()) }
        }.getOrNull()

        else -> null
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(ColorBorderLight),
    )
}
