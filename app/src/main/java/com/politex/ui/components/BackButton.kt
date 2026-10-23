package com.politex.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable

@Composable
fun BackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ColorBrandBlue,
    testTag: String? = null,
) {
    AppCard(
        modifier = modifier
            .size(Tokens.IconHit)
            .bouncyClickable(onClick = onBack)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
