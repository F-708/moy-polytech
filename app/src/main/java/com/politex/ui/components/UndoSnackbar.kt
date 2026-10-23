package com.politex.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable

const val UNDO_WINDOW_MS = 7000

@Composable
fun UndoSnackbar(
    data: SnackbarData,
    onExpire: () -> Unit = {},
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(data) {
        progress.snapTo(1f)
        progress.animateTo(0f, tween(durationMillis = UNDO_WINDOW_MS, easing = LinearEasing))
        onExpire()
    }
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorTopBar,
        modifier =
        Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = data.visuals.message,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 13.sp, color = Color.White),
                    modifier = Modifier.weight(1f),
                )
                data.visuals.actionLabel?.let { label ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                        Modifier
                            .heightIn(min = Tokens.HitTarget)
                            .bouncyClickable { data.performAction() }
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style =
                            TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = Tokens.ButtonText,
                                color = Color.White,
                            ),
                        )
                    }
                }
            }

            Box(
                modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ColorActiveBlue)
                    .graphicsLayer {
                        scaleX = progress.value
                        transformOrigin = TransformOrigin(1f, 0.5f)
                    },
            )
        }
    }
}
