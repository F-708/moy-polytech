package com.politex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = ColorBgMain,
    borderColor: Color = ColorBorderLight,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, borderColor),
        color = color,
        modifier = modifier,
    ) { content() }
}
