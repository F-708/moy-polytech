package com.politex.ui

import androidx.compose.runtime.Composable

object FlavorOverlay {
    @Volatile
    var root: (@Composable (content: @Composable () -> Unit) -> Unit)? = null

    @Volatile
    var collegeMenuExtras: (@Composable () -> Unit)? = null
}
