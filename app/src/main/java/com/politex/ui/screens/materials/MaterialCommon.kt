package com.politex.ui.screens.materials

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.entity.MaterialItemEntity
import com.politex.ui.components.AppCard
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBadgeLab
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable

object ViewerMode {
    var active by mutableStateOf(false)
}

fun materialIconFor(item: MaterialItemEntity): ImageVector = when {
    item.ext == "pdf" -> Icons.Default.PictureAsPdf
    item.ext == "doc" || item.ext == "docx" -> Icons.Default.Description
    item.ext == "xls" || item.ext == "xlsx" -> Icons.Default.TableChart
    item.ext == "txt" || item.ext == "md" || item.ext == "csv" || item.ext == "log" -> Icons.AutoMirrored.Filled.TextSnippet
    item.imagePaths.isNotEmpty() -> Icons.Default.Image
    item.ext.isEmpty() -> Icons.AutoMirrored.Filled.TextSnippet
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

fun materialIconTint(item: MaterialItemEntity): Color = when {
    item.ext == "pdf" -> ColorDangerFill
    item.ext == "doc" || item.ext == "docx" -> ColorActiveBlue
    item.ext == "xls" || item.ext == "xlsx" -> ColorSuccess
    item.imagePaths.isNotEmpty() -> ColorBadgeLab
    else -> ColorBrandBlue
}

@Composable
fun MaterialViewerTopBar(
    title: String,
    testTag: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val statusBars = WindowInsets.statusBars
    Surface(

        shape = RectangleShape,
        color = ColorTopBar,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .drawBehind {
                val top = statusBars.getTop(this).toFloat()
                if (top > 0f) drawRect(color = ColorTopBar, topLeft = Offset(0f, -top), size = Size(size.width, top))
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(Tokens.HitTarget)
                        .testTag("${testTag}_back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                    )
                }
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack == null) 12.dp else 0.dp),
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White),
            )
            actions()
        }
    }
}

@Composable
fun ViewerAction(
    icon: ImageVector,
    description: String,
    testTag: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (active) ColorTopBar else Color.Transparent,
        modifier = Modifier
            .size(44.dp)
            .bouncyClickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
fun UnsupportedFormatPlaque(ext: String, fileName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("unsupported_plaque"),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Формат .${ext.ifBlank { "?" }} не поддерживается встроенным просмотрщиком",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (ext) {
                        "doc" ->
                            "Конвертируйте файл в .docx (Word → «Сохранить как» → .docx) и загрузите заново: " +
                                ".docx читается прямо в приложении, и по нему работает поиск."
                        "xls", "xlsx" ->
                            "Таблицы Excel открываются только внешним приложением. " +
                                "Файл сохранён в материальной базе и никуда не денется."
                        else ->
                            "Файл сохранён в материальной базе. Внутри приложения читаются PDF, DOCX, " +
                                "TXT и наборы изображений."
                    },
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = fileName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ColorTextBody),
                )
            }
        }
    }
}

@Composable
fun MaterialCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp)
        .let { if (testTag != null) it.testTag(testTag) else it }
        .let { if (onClick != null) it.bouncyClickable(onClick = onClick) else it }
    AppCard(
        modifier = base,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) { content() }
    }
}
