package com.politex.ui.teacher

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.politex.util.UiScale
import com.politex.util.UiScaleStore

val uiScaleState = mutableStateOf(UiScale.NORMAL)

fun applyUiScale(
    context: Context,
    scale: UiScale,
) {
    UiScaleStore.save(context, scale)
    uiScaleState.value = scale
}

fun initUiScale(context: Context) {
    uiScaleState.value = UiScaleStore.load(context)
}

@Composable
fun ScaledDensityProvider(content: @Composable () -> Unit) {
    val scale = uiScaleState.value
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density * scale.factor, base.fontScale),
        content = content,
    )
}
