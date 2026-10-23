package com.politex.ui.teacher

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.politex.ui.components.AppCard
import com.politex.ui.components.SectionCaption
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.BellCountdownNotifier

@Composable
fun TeacherSettingsScreen(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        SubPageHeader(
            title = "Настройки",
            subtitle = "Профиль, «до звонка», масштаб и тема",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionCaption("ПРЕПОДАВАТЕЛЬ") }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(ColorBrandFill, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile?.name ?: "Преподаватель не выбран",
                                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle),
                                modifier = Modifier.testTag("teacher_settings_profile_name"),
                            )
                            Text(
                                text = "Смена преподавателя вернёт к поиску себя в списке",
                                style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier = Modifier.heightIn(min = 44.dp).bouncyClickable(onClick = onPickProfile).testTag("teacher_settings_change_profile"),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = "СМЕНИТЬ",
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White),
                                )
                            }
                        }
                    }
                }
            }

            item { SectionCaption("ДО ЗВОНКА") }
            item { BellCountdownCard(context) }

            item { SectionCaption("ВНЕШНИЙ ВИД") }
            item { ScaleCard() }
            item { ThemeSwatchesCard() }

            item { SectionCaption("О ПРОГРАММЕ") }
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Версия", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle))
                            Text(
                                text = teacherAboutVersion(),
                                style = TextStyle(fontSize = 14.sp, color = ColorTextMuted),
                                modifier = Modifier.testTag("teacher_settings_version"),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = TEACHER_ABOUT_DISCLAIMER,
                            style = TextStyle(fontSize = 11.sp, color = ColorTextBody),
                            modifier = Modifier.testTag("teacher_settings_disclaimer"),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BellCountdownCard(context: Context) {
    var isEnabled by remember { mutableStateOf(BellCountdownNotifier.isEnabled(context)) }
    var choice by remember { mutableStateOf(choiceForStyle(BellCountdownNotifier.getStyle(context))) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isEnabled = true
            BellCountdownNotifier.setEnabled(context, true)
            com.politex.widget.WidgetAlarm.scheduleNext(context)
        } else {
            isEnabled = false
        }
    }

    AppCard(modifier = Modifier.fillMaxWidth().testTag("teacher_bell_card")) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Показывать в шторке", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle))
                    Text(
                        text = if (isEnabled) "Строка «до звонка» видна" else "Отключено",
                        style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { enable ->
                        if (enable) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                isEnabled = true
                                BellCountdownNotifier.setEnabled(context, true)
                                com.politex.widget.WidgetAlarm.scheduleNext(context)
                            }
                        } else {
                            isEnabled = false
                            BellCountdownNotifier.setEnabled(context, false)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ColorBrandBlue,
                        uncheckedTrackColor = ColorBorderLight,
                        uncheckedThumbColor = ColorTextMuted,
                    ),
                    modifier = Modifier.testTag("teacher_bell_switch"),
                )
            }

            if (isEnabled) {
                Spacer(Modifier.height(12.dp))
                Text("СТИЛЬ СТРОКИ", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ColorBrandBlue))
                Spacer(Modifier.height(6.dp))

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = choice.title,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = RoundedCornerShape(2.dp),
                        textStyle = TextStyle(fontSize = 13.sp, color = ColorTextTitle),
                        modifier = Modifier.menuAnchor().fillMaxWidth().testTag("teacher_bell_style_field"),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        BellStyleChoice.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.title, fontSize = 13.sp) },
                                onClick = {
                                    choice = option
                                    expanded = false
                                    BellCountdownNotifier.setStyle(context, bellStyleForChoice(option))
                                },
                                modifier = Modifier.testTag("teacher_bell_style_${option.name}"),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Постоянная строка со временем до звонка — обновляется каждую минуту (в стиле «с секундами» — каждую секунду).",
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        }
    }
}

enum class BellStyleChoice(val title: String) {
    MINUTES("В минутах"),
    SECONDS("С секундами"),
}

internal fun bellStyleForChoice(choice: BellStyleChoice): BellCountdownNotifier.Style = when (choice) {
    BellStyleChoice.MINUTES -> BellCountdownNotifier.Style.COMPACT
    BellStyleChoice.SECONDS -> BellCountdownNotifier.Style.WITH_SECONDS
}

internal fun choiceForStyle(style: BellCountdownNotifier.Style): BellStyleChoice = if (style == BellCountdownNotifier.Style.WITH_SECONDS) BellStyleChoice.SECONDS else BellStyleChoice.MINUTES
