package com.politex.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    val dark = darkThemeState.value

    val scheme =
        if (dark) {
            darkColorScheme(
                primary = ColorBrandFill,
                onPrimary = Color.White,
                primaryContainer = ColorSurfaceHighlight,
                onPrimaryContainer = ColorBrandBlue,
                secondary = ColorActiveFill,
                onSecondary = Color.White,
                secondaryContainer = ColorSurfaceHighlight,
                onSecondaryContainer = ColorBrandBlue,
                tertiary = ColorTopBar,
                onTertiary = Color.White,
                background = ColorBgMain,
                onBackground = ColorTextBody,
                surface = ColorBgMain,
                onSurface = ColorTextTitle,
                surfaceVariant = ColorSurfaceVariantLight,
                onSurfaceVariant = ColorTextMuted,
                outline = ColorBorderLight,
                outlineVariant = ColorDividerLight,
                error = ColorError,
                onError = Color(0xFF450A0A),
            )
        } else {
            lightColorScheme(
                primary = ColorBrandBlue,
                onPrimary = Color.White,
                primaryContainer = ColorSurfaceHighlight,
                onPrimaryContainer = ColorBrandBlue,
                secondary = ColorActiveBlue,
                onSecondary = Color.White,
                secondaryContainer = ColorSurfaceHighlight,
                onSecondaryContainer = ColorBrandBlue,
                tertiary = ColorTopBar,
                onTertiary = Color.White,
                background = ColorBgMain,
                onBackground = ColorTextBody,
                surface = ColorBgMain,
                onSurface = ColorTextTitle,
                surfaceVariant = ColorSurfaceVariantLight,
                onSurfaceVariant = ColorTextMuted,
                outline = ColorBorderLight,
                outlineVariant = ColorDividerLight,
                error = ColorError,
                onError = Color.White,
            )
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            runCatching {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                window.statusBarColor = ColorTopBar.toArgb()
                window.navigationBarColor = ColorBgMain.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}
