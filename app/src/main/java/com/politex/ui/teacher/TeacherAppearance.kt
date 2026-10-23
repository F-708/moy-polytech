package com.politex.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.components.AppCard
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import com.politex.util.AppThemeStore
import com.politex.util.UiScale

@Composable
fun ScaleCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(uiScaleState.value) }
    AppCard(modifier = modifier.fillMaxWidth().testTag("teacher_scale_card")) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Масштаб интерфейса", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle))
            Spacer(Modifier.height(4.dp))
            Text("Размер текста и элементов: 75% — компактнее, 125% — крупнее", style = TextStyle(fontSize = 11.sp, color = ColorTextMuted))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UiScale.entries.forEach { scale ->
                    val isSelected = selected == scale
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .bouncyClickable {
                                selected = scale
                                applyUiScale(context, scale)
                            }
                            .testTag("teacher_scale_${scale.name}"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = scale.label,
                                style = TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else ColorTextTitle,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSwatchesCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentTheme by remember { mutableStateOf(AppThemeStore.load(context)) }
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Тема приложения", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle))
            Spacer(Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("teacher_theme_swatches"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(AppTheme.entries.toList()) { theme ->
                    val isSelected = currentTheme == theme
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = ColorBgMain,
                        border = BorderStroke(
                            if (isSelected) 2.dp else 1.dp,
                            if (isSelected) ColorTopBar else ColorBorderLight,
                        ),
                        modifier = Modifier.bouncyClickable {
                            AppThemeStore.save(context, theme)
                            currentTheme = theme
                        }.testTag("teacher_theme_${theme.name}"),
                    ) {
                        Column(
                            modifier = Modifier.width(92.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(theme.palette.topBar, RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier.width(20.dp).height(7.dp).background(theme.palette.brandBlue, RoundedCornerShape(1.dp)),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = theme.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = ColorTextTitle,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    }
}
