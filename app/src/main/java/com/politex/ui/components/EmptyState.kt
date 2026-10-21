package com.politex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Inventory2,
    hint: String = "",
    note: String = "",
    hintTestTag: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(ColorSurfaceHighlight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                textAlign = TextAlign.Center,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle),
            )
            if (hint.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = hint,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(horizontal = 12.dp).then(
                        if (hintTestTag != null) Modifier.testTag(hintTestTag) else Modifier,
                    ),
                )
            }
            if (note.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = note,
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                action()
            }
        }
    }
}
