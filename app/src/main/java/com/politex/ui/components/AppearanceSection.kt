package com.politex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorOnAccent
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.isDarkThemeActive
import com.politex.ui.util.bouncyClickable
import com.politex.util.AppThemeStore

/**
 * Секция «Тема оформления» — общая для стандартных и teacher-настроек.
 *
 * Состав: 8 пресетов (как раньше), режим Системная/Светлая/Тёмная вместо тумблера
 * (та же высота 56 dp) и свёрнутая по умолчанию строка «Тон оформления».
 * Видимых строк по умолчанию столько же, сколько было.
 */
@Composable
fun AppearanceSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(AppThemeStore.readPrefs(context)) }
    var toneExpanded by rememberSaveable { mutableStateOf(false) }

    SettingsCard(modifier) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(Icons.Default.Palette)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Тема оформления",
                style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = ColorTextTitle,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PresetSwatches(
            currentTheme = config.theme,
            onThemePicked = { theme ->
                AppThemeStore.saveTheme(context, theme)
                config = AppThemeStore.readPrefs(context)
            },
        )

        SettingsDivider()

        ModeSegments(
            selected = config.mode,
            onSelect = { mode ->
                AppThemeStore.saveMode(context, mode)
                config = AppThemeStore.readPrefs(context)
            },
        )

        SettingsDivider()

        SettingsRow(
            icon = Icons.Default.Tune,
            title = "Тон оформления",
            subtitle = config.tone.title,
            showChevron = true,
            onClick = { toneExpanded = !toneExpanded },
            modifier = Modifier.testTag("theme_tone_row"),
        )

        AnimatedVisibility(visible = toneExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ToneChips(
                    selected = config.tone,
                    onSelect = { tone ->
                        AppThemeStore.saveTone(context, tone)
                        config = AppThemeStore.readPrefs(context)
                    },
                )
            }
        }

    }
}

@Composable
private fun PresetSwatches(
    currentTheme: AppTheme,
    onThemePicked: (AppTheme) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .testTag("theme_swatches")
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppTheme.entries.toList().chunked(4).forEach { rowThemes ->
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowThemes.forEach { theme ->
                    ThemeSwatch(
                        theme = theme,
                        isSelected = currentTheme == theme,
                        onClick = { onThemePicked(theme) },
                        modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                repeat(4 - rowThemes.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isDarkThemeActive
    val previewBorder = if (dark) ColorTextMuted.copy(alpha = 0.55f) else ColorBorderLight
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) ColorBrandBlue else ColorBorderLight),
        modifier =
        modifier
            .semantics {
                role = Role.RadioButton
                selected = isSelected
                contentDescription = "Тема ${theme.title}"
            }
            .testTag("theme_swatch_${theme.id}")
            .bouncyClickable(onClick = onClick),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                Modifier
                    .size(26.dp)
                    .background(theme.palette.topBar, RoundedCornerShape(2.dp))
                    .border(1.dp, previewBorder, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                    Modifier
                        .size(width = 16.dp, height = 5.dp)
                        .background(theme.palette.brandBlue, RoundedCornerShape(1.dp)),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = theme.title,
                // Названия длинные («Графитовая», «Изумрудная»): одна строка без переноса,
                // без многоточия и с плотным трекингом — плитка не «прыгает» по высоте.
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                style =
                TextStyle(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = (-0.3).sp,
                    color = ColorTextTitle,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun ModeSegments(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("theme_mode_segments")
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (isSelected) ColorBrandFill else ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, if (isSelected) ColorBrandFill else ColorBorderLight),
                modifier =
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = "Режим оформления: ${mode.title}"
                    }
                    .testTag("theme_mode_${mode.id}")
                    .bouncyClickable { onSelect(mode) },
            ) {
                SegmentLabel(text = mode.title, isSelected = isSelected)
            }
        }
    }
}

@Composable
private fun ToneChips(
    selected: SurfaceTone,
    onSelect: (SurfaceTone) -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SurfaceTone.entries.forEach { tone ->
            val isSelected = tone == selected
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (isSelected) ColorBrandFill else ColorBgMain,
                border = BorderStroke(1.dp, if (isSelected) ColorBrandFill else ColorBorderLight),
                modifier =
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = isSelected
                        contentDescription = "Тон оформления: ${tone.title}"
                    }
                    .testTag("theme_tone_${tone.id}")
                    .bouncyClickable { onSelect(tone) },
            ) {
                SegmentLabel(text = tone.title, isSelected = isSelected)
            }
        }
    }
}

@Composable
private fun SegmentLabel(
    text: String,
    isSelected: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style =
            TextStyle(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                color = if (isSelected) ColorOnAccent else ColorTextBody,
                textAlign = TextAlign.Center,
            ),
        )
    }
}