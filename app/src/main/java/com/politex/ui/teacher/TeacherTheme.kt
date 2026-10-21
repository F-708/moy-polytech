package com.politex.ui.teacher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.MyApplicationTheme

data class TeacherMetrics(
    val hitTarget: Dp = 48.dp,
    val chipText: TextUnit = 13.sp,
    val body: TextUnit = 16.sp,
    val buttonText: TextUnit = 14.sp,
)

val LocalTeacherMetrics = staticCompositionLocalOf { TeacherMetrics() }

@Composable
fun TeacherTheme(content: @Composable () -> Unit) {
    MyApplicationTheme(content = content)
}
