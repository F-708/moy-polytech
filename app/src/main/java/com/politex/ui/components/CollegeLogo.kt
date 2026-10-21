package com.politex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.darkThemeState
import kotlin.math.min
import kotlin.math.roundToInt

private const val LOGO_VIEWPORT_W = 1392f
private const val LOGO_VIEWPORT_H = 123f
private const val LOGO_LINE1_INK_LEFT = 181f
private const val LOGO_LINE1_INK_WIDTH = 1209f
private const val LOGO_LINE1_BASELINE = 65f
private const val LOGO_LINE1_CAP = 28f
private const val LOGO_LINE1_TRACKING = 0.16f
private const val LOGO_LINE2_INK_LEFT = 180f
private const val LOGO_LINE2_INK_WIDTH = 1211f
private const val LOGO_LINE2_BASELINE = 117f
private const val LOGO_LINE2_CAP = 37f
private const val LOGO_LINE2_TRACKING = 0.03f
private const val FONT_CAP_HEIGHT_RATIO = 0.7109f
private const val LOGO_LINE1_TEXT = "ГОСУДАРСТВЕННОЕ УЧРЕЖДЕНИЕ ОБРАЗОВАНИЯ"
private const val LOGO_LINE2_TEXT = "“МИНСКИЙ ПОЛИТЕХНИЧЕСКИЙ КОЛЛЕДЖ”"

private val CollegeLogoIndigo = Color(0xFF262C61)

private val emblemStaticRects =
    floatArrayOf(
        48f, 0f, 62f, 4f,
        40f, 4f, 78f, 4f,
        31f, 10f, 96f, 8f,
        21f, 22f, 116f, 9f,
        41f, 31f, 76f, 3f,
        8f, 34f, 142f, 7f,
        0f, 101f, 46f, 18f, 112f, 101f, 46f, 18f,
        53f, 101f, 13f, 11f, 92f, 101f, 13f, 11f,
        66f, 110f, 24f, 2f,
        0f, 113f, 158f, 2f,
        0f, 117f, 158f, 2f,
    )

private val emblemColumnCenters = floatArrayOf(13f, 36f, 59f, 98f, 121f, 144f)

private val emblemColumnParts =
    floatArrayOf(
        -6f, 43f, 14f, 1f,
        -5f, 44f, 11f, 1f,
        -5f, 46f, 11f, 1f,
        -4f, 47f, 9f, 3f,
        -3f, 52f, 7f, 42f,
        -4f, 95f, 9f, 2f,
        -6.5f, 98f, 13f, 3f,
    )

private class LogoLineLayout(
    val xPx: Float,
    val yPx: Float,
    val fontSize: TextUnit,
    val tracking: Float,
    val scaleX: Float,
)

@Composable
fun CollegeLogo(
    modifier: Modifier = Modifier,
    color: Color = if (darkThemeState.value) ColorTextTitle else CollegeLogoIndigo,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val fit =
            min(
                constraints.maxWidth.toFloat() / LOGO_VIEWPORT_W,
                constraints.maxHeight.toFloat() / LOGO_VIEWPORT_H,
            )
        val wPx = LOGO_VIEWPORT_W * fit
        val hPx = LOGO_VIEWPORT_H * fit

        val refStyle =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
                letterSpacing = 0.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            )

        fun layoutLine(
            text: String,
            inkLeftDesign: Float,
            inkWidthDesign: Float,
            baselineDesign: Float,
            capDesign: Float,
            trackingEm: Float,
        ): LogoLineLayout {
            val emPx = capDesign / FONT_CAP_HEIGHT_RATIO * fit
            val fontSize = with(density) { (emPx / fontScale).toSp() }
            val style = refStyle.copy(fontSize = fontSize, letterSpacing = trackingEm.em)
            val m = measurer.measure(AnnotatedString(text), style = style)
            val inkLeft = m.getBoundingBox(0).left
            val inkRight = m.getBoundingBox(text.lastIndex).right
            val scaleX = inkWidthDesign * fit / (inkRight - inkLeft)
            return LogoLineLayout(
                xPx = inkLeftDesign * fit - inkLeft * scaleX,
                yPx = baselineDesign * fit - m.firstBaseline,
                fontSize = fontSize,
                tracking = trackingEm,
                scaleX = scaleX,
            )
        }

        val line1 = layoutLine(LOGO_LINE1_TEXT, LOGO_LINE1_INK_LEFT, LOGO_LINE1_INK_WIDTH, LOGO_LINE1_BASELINE, LOGO_LINE1_CAP, LOGO_LINE1_TRACKING)
        val line2 = layoutLine(LOGO_LINE2_TEXT, LOGO_LINE2_INK_LEFT, LOGO_LINE2_INK_WIDTH, LOGO_LINE2_BASELINE, LOGO_LINE2_CAP, LOGO_LINE2_TRACKING)

        Box(Modifier.align(Alignment.Center).size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.height / LOGO_VIEWPORT_H
                var i = 0
                while (i < emblemStaticRects.size) {
                    drawRect(
                        color = color,
                        topLeft = Offset(emblemStaticRects[i] * s, emblemStaticRects[i + 1] * s),
                        size = Size(emblemStaticRects[i + 2] * s, emblemStaticRects[i + 3] * s),
                    )
                    i += 4
                }
                for (c in emblemColumnCenters) {
                    var j = 0
                    while (j < emblemColumnParts.size) {
                        drawRect(
                            color = color,
                            topLeft = Offset((c + emblemColumnParts[j]) * s, emblemColumnParts[j + 1] * s),
                            size = Size(emblemColumnParts[j + 2] * s, emblemColumnParts[j + 3] * s),
                        )
                        j += 4
                    }
                }
            }

            @Composable
            fun lineText(line: LogoLineLayout, text: String) {
                Text(
                    text = text,
                    style = refStyle.copy(fontSize = line.fontSize, letterSpacing = line.tracking.em, color = color),
                    softWrap = false,
                    maxLines = 1,
                    modifier =
                    Modifier
                        .offset { IntOffset(line.xPx.roundToInt(), line.yPx.roundToInt()) }
                        .graphicsLayer {
                            scaleX = line.scaleX
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                )
            }

            lineText(line1, LOGO_LINE1_TEXT)
            lineText(line2, LOGO_LINE2_TEXT)
        }
    }
}
