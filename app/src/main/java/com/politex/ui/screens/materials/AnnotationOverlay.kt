package com.politex.ui.screens.materials

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.ColorAnnotationDanger
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

data class Stroke(
    val color: Int,
    val widthDp: Float,
    val pts: List<Offset>,
) {
    companion object {

        fun serialize(strokes: List<Stroke>): String {
            val arr = JSONArray()
            strokes.forEach { s ->
                val pts = JSONArray()
                s.pts.forEach {
                    pts.put(it.x.toDouble())
                    pts.put(it.y.toDouble())
                }
                arr.put(JSONObject().put("c", s.color).put("w", s.widthDp.toDouble()).put("pts", pts))
            }
            return JSONObject().put("v", 1).put("strokes", arr).toString()
        }

        fun parse(json: String): List<Stroke> =
            runCatching {
                val arr = JSONObject(json).optJSONArray("strokes") ?: return emptyList()
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val ptsArr = o.optJSONArray("pts") ?: JSONArray()
                    val pts =
                        (0 until ptsArr.length() / 2).map { k ->
                            Offset(ptsArr.getDouble(2 * k).toFloat(), ptsArr.getDouble(2 * k + 1).toFloat())
                        }
                    Stroke(o.optInt("c"), o.optDouble("w", 4.0).toFloat(), pts)
                }
            }.getOrDefault(emptyList())
    }
}

val ANNOTATION_COLORS = listOf(0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt())

val ANNOTATION_WIDTHS = listOf(2f, 4f, 8f)

private const val MIN_POINT_DIST = 0.004f

const val ERASER_TOUCH_EXTRA_DP = 8f

const val CLEAR_CONFIRM_TIMEOUT_MS = 500L

fun eraserHitTest(
    stroke: Stroke,
    point: Offset,
    threshold: Float,
    aspect: Float = 1f,
): Boolean {
    if (stroke.pts.isEmpty()) return false
    fun iso(o: Offset) = Offset(o.x, o.y * aspect)
    val p = iso(point)
    if (stroke.pts.size == 1) return (p - iso(stroke.pts[0])).getDistance() <= threshold
    return stroke.pts.zipWithNext { a, b -> distPointToSegment(p, iso(a), iso(b)) }.min() <= threshold
}

private fun distPointToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 == 0f) return (p - a).getDistance()
    val ap = p - a
    val t = ((ap.x * ab.x + ap.y * ab.y) / len2).coerceIn(0f, 1f)
    return (ap - ab * t).getDistance()
}

internal fun compensatedStrokeWidthPx(widthDp: Float, pxPerDp: Float, layerScale: Float): Float =
    widthDp * pxPerDp / layerScale.coerceAtLeast(0.01f)

internal fun eraserThresholdNorm(widthDp: Float, extraDp: Float, pxPerDp: Float, boxWidthPx: Float, layerScale: Float): Float =
    (widthDp / 2f + extraDp) * pxPerDp / (layerScale.coerceAtLeast(0.01f) * boxWidthPx.coerceAtLeast(1f))

internal fun clampToBox(pos: Offset, w: Float, h: Float): Offset =
    Offset(pos.x.coerceIn(0f, w), pos.y.coerceIn(0f, h))

sealed interface AnnotationAction {
    data class Add(val page: Int, val stroke: Stroke) : AnnotationAction
    data class Erase(val page: Int, val index: Int, val stroke: Stroke) : AnnotationAction
    data class ClearAll(val snapshot: Map<Int, List<Stroke>>) : AnnotationAction
}

class AnnotationUndoSession {
    private val stack = ArrayDeque<AnnotationAction>()
    fun record(action: AnnotationAction) { stack.addLast(action) }
    fun pop(): AnnotationAction? = stack.removeLastOrNull()
    fun clear() { stack.clear() }
    val size: Int get() = stack.size
}

fun applyUndo(action: AnnotationAction, strokes: Map<Int, List<Stroke>>): Map<Int, List<Stroke>> = when (action) {
    is AnnotationAction.Add -> {
        val list = strokes[action.page].orEmpty().toMutableList()
        val i = list.indexOfLast { it == action.stroke }
        if (i >= 0) list.removeAt(i)
        strokes + (action.page to list)
    }
    is AnnotationAction.Erase -> {
        val list = strokes[action.page].orEmpty().toMutableList()
        list.add(action.index.coerceIn(0, list.size), action.stroke)
        strokes + (action.page to list)
    }
    is AnnotationAction.ClearAll -> strokes + action.snapshot
}

@Composable
fun AnnotationLayer(
    strokes: List<Stroke>,
    current: Stroke?,
    drawMode: Boolean,
    strokeColor: Int,
    strokeWidthDp: Float,
    onCurrentChange: (Stroke?) -> Unit,
    onStrokeFinished: (Stroke) -> Unit,
    transformGestures: ((pan: Offset, zoom: Float) -> Unit)?,
    modifier: Modifier = Modifier,
    eraserActive: Boolean = false,
    onEraseAt: ((Offset) -> Unit)? = null,

    transformKey: Any? = null,

    layerScale: Float = 1f,
) {

    var eraserPos by remember { mutableStateOf<Offset?>(null) }

    val currentOnChange by rememberUpdatedState(onCurrentChange)
    val currentFinished by rememberUpdatedState(onStrokeFinished)
    val currentErase by rememberUpdatedState(onEraseAt)
    val currentTransform by rememberUpdatedState(transformGestures)

    val gestureModifier =
        if (drawMode) {
            Modifier.pointerInput(drawMode, eraserActive, strokeColor, strokeWidthDp, transformKey) {
                awaitEachGesture {

                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var pts = emptyList<Offset>()
                    fun addPoint(pos: Offset) {
                        val c = clampToBox(pos, w, h)
                        val n = Offset(c.x / w, c.y / h)
                        if (pts.isEmpty() || (n - pts.last()).getDistance() >= MIN_POINT_DIST) {
                            pts = pts + n
                            currentOnChange(Stroke(strokeColor, strokeWidthDp, pts))
                        }
                    }
                    fun eraseAt(pos: Offset) {
                        val c = clampToBox(pos, w, h)
                        val n = Offset(c.x / w, c.y / h)
                        eraserPos = n
                        currentErase?.invoke(n)
                    }
                    if (eraserActive) eraseAt(down.position) else addPoint(down.position)
                    var transformed = false
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) {

                            if (!transformed) {
                                transformed = true
                                pts = emptyList()
                                eraserPos = null
                                currentOnChange(null)
                            }
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                currentTransform?.invoke(panChange, zoomChange)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (!transformed) {
                            event.changes.forEach { c ->
                                if (c.pressed) {
                                    if (c.positionChanged()) {
                                        if (eraserActive) eraseAt(c.position) else addPoint(c.position)
                                    }
                                    c.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (!transformed && !eraserActive && pts.isNotEmpty()) currentFinished(Stroke(strokeColor, strokeWidthDp, pts))
                    currentOnChange(null)
                    eraserPos = null
                }
            }
        } else {
            Modifier
        }

    Canvas(modifier = modifier.then(gestureModifier).testTag("annotation_layer")) {
        (strokes + listOfNotNull(current)).forEach { drawStroke(it, layerScale) }

        if (eraserActive) {
            eraserPos?.let { p ->
                val r = compensatedStrokeWidthPx(strokeWidthDp / 2f + ERASER_TOUCH_EXTRA_DP, density, layerScale)
                val center = Offset(p.x * size.width, p.y * size.height)
                drawCircle(color = ColorAnnotationDanger.copy(alpha = 0.13f), radius = r, center = center)
                drawCircle(color = ColorAnnotationDanger.copy(alpha = 0.6f), radius = r, center = center, style = DrawStroke(width = 1.5.dp.toPx()))
            }
        }
    }
}

private fun DrawScope.drawStroke(s: Stroke, layerScale: Float) {
    if (s.pts.isEmpty()) return
    val pts = s.pts.map { Offset(it.x * size.width, it.y * size.height) }
    val path =
        Path().apply {
            moveTo(pts[0].x, pts[0].y)
            if (pts.size == 1) {

                lineTo(pts[0].x + 0.1f, pts[0].y + 0.1f)
            } else {
                for (i in 0 until pts.size - 2) {
                    val mid = Offset((pts[i].x + pts[i + 1].x) / 2f, (pts[i].y + pts[i + 1].y) / 2f)
                    quadraticBezierTo(pts[i].x, pts[i].y, mid.x, mid.y)
                }
                lineTo(pts.last().x, pts.last().y)
            }
        }
    drawPath(
        path = path,
        color = Color(s.color),
        style = DrawStroke(
            width = compensatedStrokeWidthPx(s.widthDp, density, layerScale),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

@Composable
fun AnnotationToolbar(
    color: Int,
    widthDp: Float,
    onColor: (Int) -> Unit,
    onWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    eraserActive: Boolean = false,
    onEraserToggle: () -> Unit = {},
) {

    var clearConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(clearConfirm) {
        if (clearConfirm) {

            delay(CLEAR_CONFIRM_TIMEOUT_MS)
            clearConfirm = false
        }
    }
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderLight),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("draw_toolbar"),
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,

                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
            ) {
                ANNOTATION_COLORS.forEachIndexed { i, c ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(Tokens.IconHit).bouncyClickable { onColor(c) }.testTag("draw_color_$i"),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size(if (c == color) 24.dp else 20.dp)
                                .background(Color(c), CircleShape)
                                .then(if (c == color) Modifier.border(2.dp, ColorBrandBlue, CircleShape) else Modifier),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                ANNOTATION_WIDTHS.forEachIndexed { i, w ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(Tokens.IconHit).bouncyClickable { onWidth(w) }.testTag("draw_width_$i"),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size((w + 6).dp)
                                .background(if (w == widthDp) ColorBrandBlue else ColorTextMuted, CircleShape),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            ) {
                ToolbarAction(
                    icon = Icons.Default.FormatColorReset,
                    description = if (eraserActive) "Ластик (включён)" else "Ластик",
                    tag = "draw_eraser_btn",
                    active = eraserActive,
                    onClick = onEraserToggle,
                )
                ToolbarAction(Icons.AutoMirrored.Filled.Undo, "Отменить штрих", "draw_undo_btn", onClick = onUndo)
                ToolbarAction(
                    icon = Icons.Default.Delete,
                    description = if (clearConfirm) "Точно? Нажмите ещё раз" else "Удалить все аннотации документа",
                    tag = "draw_clear_btn",
                    tint = if (clearConfirm) ColorDangerFill else ColorBrandBlue,
                ) {
                    if (clearConfirm) {
                        clearConfirm = false
                        onClear()
                    } else {
                        clearConfirm = true
                    }
                }
                ToolbarAction(Icons.Default.Close, "Выйти из рисования", "draw_exit_btn", onClick = onExit)
            }
        }
    }
}

@Composable
private fun ToolbarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tag: String,
    tint: Color = ColorBrandBlue,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (active) ColorBrandBlue.copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier.size(Tokens.IconHit).bouncyClickable(onClick = onClick).testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}
