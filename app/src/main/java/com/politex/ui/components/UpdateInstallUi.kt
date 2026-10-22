package com.politex.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileDownloadDone
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.BuildConfig
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import com.politex.update.AppUpdateChecker
import com.politex.update.InstallButtonState
import com.politex.update.UpdateInstallController
import com.politex.update.installButtonDescription
import com.politex.update.moreUpdateSubtitle

@Composable
fun UpdateInstallButton(
    state: InstallButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloading = state is InstallButtonState.Downloading
    val ready = state is InstallButtonState.Downloaded || state is InstallButtonState.InstallLaunched
    val targetProgress = (state as? InstallButtonState.Downloading)?.progress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 300),
        label = "UpdateInstallProgress",
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .bouncyClickable(enabled = !downloading, onClick = onClick)
            .semantics { contentDescription = installButtonDescription(state) },
        contentAlignment = Alignment.Center,
    ) {
        when {
            downloading -> UpdateProgressIcon(progress = animatedProgress)
            ready -> SettingsIcon(Icons.Default.FileDownloadDone)
            else -> SettingsIcon(Icons.Default.FileDownload)
        }
    }
}

@Composable
fun UpdateProgressIcon(progress: Float) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(ColorBrandFill.copy(alpha = 0.12f), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .drawWithContent {
                    clipRect(top = size.height * (1f - clamped)) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = null,
                tint = ColorBrandFill,
                modifier = Modifier.size(20.dp),
            )
        }
        Icon(
            imageVector = Icons.Outlined.FileDownload,
            contentDescription = null,
            tint = ColorBrandBlue,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun UpdateAvailableCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by UpdateInstallController.state.collectAsState()
    val pending by UpdateInstallController.foundUpdate.collectAsState()
    LaunchedEffect(Unit) { UpdateInstallController.restore(context) }
    if (!AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(BuildConfig.FLAVOR)) return
    val info = pending ?: return
    val downloading = state is InstallButtonState.Downloading
    val ready = state is InstallButtonState.Downloaded || state is InstallButtonState.InstallLaunched
    val targetProgress = (state as? InstallButtonState.Downloading)?.progress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 300),
        label = "MoreUpdateProgress",
    )
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(enabled = !downloading, onClick = { UpdateInstallController.onButtonTap(context, info) })
            .testTag("more_update_btn"),
        color = ColorBrandFill.copy(alpha = 0.08f),
        borderColor = ColorBrandBlue,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                downloading -> UpdateProgressIcon(progress = animatedProgress)
                ready -> SettingsIcon(Icons.Default.FileDownloadDone)
                else -> SettingsIcon(Icons.Default.FileDownload)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ДОСТУПНО ОБНОВЛЕНИЕ",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ColorTextTitle,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = moreUpdateSubtitle(state, info.versionName),
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!downloading) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = ColorTextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
