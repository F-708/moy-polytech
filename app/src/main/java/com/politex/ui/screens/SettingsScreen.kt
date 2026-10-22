package com.politex.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.politex.BuildConfig
import com.politex.content.ContentPackStore
import com.politex.content.ContentPackWorker
import com.politex.content.contentPackStatusText
import com.politex.data.model.GroupInfo
import com.politex.ui.components.SectionCaption
import com.politex.ui.components.SettingsCard
import com.politex.ui.components.SettingsDivider
import com.politex.ui.components.SettingsIcon
import com.politex.ui.components.SettingsRow
import com.politex.ui.components.UpdateInstallButton
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.ColorWarning
import com.politex.ui.theme.Tokens
import com.politex.ui.theme.isDarkThemeActive
import com.politex.ui.util.bouncyClickable
import com.politex.update.AppUpdateChecker
import com.politex.update.AppUpdateWorker
import com.politex.update.InstallButtonState
import com.politex.update.ManualCheckResult
import com.politex.update.UpdateCheckController
import com.politex.update.UpdateInstallController
import com.politex.update.channelChangeRequiresCheck
import com.politex.update.defaultUpdateStatus
import com.politex.update.downloadedStatusText
import com.politex.update.downloadingStatusText
import com.politex.update.installErrorMessage
import com.politex.update.manualResultIsError
import com.politex.update.manualResultIsFound
import com.politex.update.manualResultText
import com.politex.util.AppThemeStore
import com.politex.util.BackupManager
import com.politex.util.BellCountdownNotifier
import com.politex.util.ExactAlarmGate
import com.politex.util.ScheduleDisplayPrefs
import com.politex.util.SubgroupPref
import com.politex.util.SubgroupSettings
import com.politex.util.TeacherModeStore
import com.politex.util.UpdateChannel
import com.politex.util.UpdateChannelStore
import com.politex.util.defaultBackupFileName
import com.politex.widget.WidgetUpdateHelper
import com.politex.worker.MpkWorkManagerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    groupInfo: GroupInfo,
    onGroupChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val teacherModeRevision = TeacherModeStore.revision.intValue
    val isTeacherMode =
        remember(context, teacherModeRevision) {
            TeacherModeStore.isTeacher(context)
        }
    val teacherName =
        remember(context, teacherModeRevision) {
            TeacherModeStore.teacherName(context)
        }
    var isNotificationEnabled by remember {
        mutableStateOf(WidgetUpdateHelper.isNotificationEnabled(context))
    }

    val settingsLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(settingsLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isNotificationEnabled = WidgetUpdateHelper.isNotificationEnabled(context)
            }
        }
        settingsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { settingsLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun applyScheduleNotification(enabled: Boolean) {
        isNotificationEnabled = enabled
        WidgetUpdateHelper.setNotificationEnabled(context, enabled)
        MpkWorkManagerHelper.setupPeriodicScheduleCheck(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        applyScheduleNotification(isGranted)
    }

    fun onScheduleNotificationToggle(enable: Boolean) {
        val needsPermission =
            enable &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            applyScheduleNotification(enable)
        }
    }

    var currentTheme by remember { mutableStateOf(AppThemeStore.load(context)) }
    fun onThemePicked(theme: AppTheme) {
        AppThemeStore.save(context, theme)
        currentTheme = theme
    }

    var backupMessage by remember { mutableStateOf("") }
    var backupIsError by remember { mutableStateOf(false) }
    val backupScope = rememberCoroutineScope()

    val contentRevision = ContentPackStore.revision.intValue
    val contentStatusText =
        remember(contentRevision) {
            contentPackStatusText(ContentPackStore.activeVersion, ContentPackStore.generatedAt)
        }
    var contentMessage by remember { mutableStateOf("") }

    var showBackupDialog by remember { mutableStateOf(false) }
    var pendingBackupSections by remember { mutableStateOf(BackupManager.BackupSection.ALL) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sections = pendingBackupSections
        backupScope.launch {
            backupMessage = try {
                withContext(Dispatchers.IO) {
                    val json = BackupManager.buildBackup(context, sections)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                backupIsError = false
                "Копия сохранена"
            } catch (e: Exception) {
                backupIsError = true
                "Не удалось сохранить: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupScope.launch {
            backupMessage = try {
                val restored = withContext(Dispatchers.IO) {
                    val json = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    if (json.isNullOrBlank()) {
                        null
                    } else {
                        BackupManager.restoreBackup(context, json)
                    }
                }
                if (restored == null) {
                    backupIsError = true
                    "Файл пустой"
                } else {
                    restored.fold(
                        onSuccess = { result ->
                            backupIsError = false
                            result.groupRestored?.let { onGroupChanged(it) }
                            val parts = mutableListOf("Восстановлено заданий: ${result.tasksRestored}")
                            if (result.gradesRestored > 0) parts += "оценок: ${result.gradesRestored}"
                            if (result.remindersRestored > 0) parts += "напоминаний: ${result.remindersRestored}"
                            if (result.lessonsRestored > 0) parts += "уроков: ${result.lessonsRestored}"
                            if (result.subjectRenamesRestored > 0) parts += "названий предметов: ${result.subjectRenamesRestored}"
                            result.groupRestored?.let { parts += "группа $it" }
                            if (result.warnings.isNotEmpty()) parts += result.warnings.joinToString("; ")
                            parts.joinToString(". ")
                        },
                        onFailure = {
                            backupIsError = true
                            "Не удалось прочитать файл: ${it.message}"
                        },
                    )
                }
            } catch (e: Exception) {
                backupIsError = true
                "Не удалось прочитать файл: ${e.message}"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showTeacherModeExit(isTeacherMode)) {
                item { SectionCaption("ПРОФИЛЬ", Modifier.padding(top = 4.dp)) }
                item {
                    TeacherModeCard(
                        teacherName = teacherName,
                        onSwitchRole = {
                            WidgetUpdateHelper.getPrefs(context).edit()
                                .remove("selected_group")
                                .apply()
                            SubgroupSettings.set(context, SubgroupPref.NEUTRAL)
                            TeacherModeStore.clear(context)
                        },
                    )
                }
            }

            item { SectionCaption("РАСПИСАНИЕ", Modifier.padding(top = 4.dp)) }
            item {
                ScheduleSettingsCard(
                    groupInfo = groupInfo,
                    showSubgroup = showSubgroupCard(isTeacherMode),
                )
            }

            item { SectionCaption("УВЕДОМЛЕНИЯ", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.Campaign,
                        title = "Уведомление о новом расписании",
                        trailing = {
                            SettingsSwitch(
                                checked = isNotificationEnabled,
                                onCheckedChange = { enabled -> onScheduleNotificationToggle(enabled) },
                                label = "Уведомление о новом расписании",
                                modifier = Modifier.testTag("notifications_switch"),
                            )
                        },
                    )
                    SettingsDivider()
                    BellCountdownSection()
                }
            }

            item { SectionCaption("ВНЕШНИЙ ВИД", Modifier.padding(top = 4.dp)) }
            item {
                AppearanceCard(
                    currentTheme = currentTheme,
                    onThemePicked = { theme -> onThemePicked(theme) },
                )
            }

            item { SectionCaption("КАРТА", Modifier.padding(top = 4.dp)) }
            item { MapSettingsCard() }

            item { SectionCaption("ДАННЫЕ", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.SaveAlt,
                        title = "Сохранить копию",
                        onClick = { showBackupDialog = true },
                        showChevron = true,
                        modifier = Modifier.testTag("backup_save_btn"),
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.SettingsBackupRestore,
                        title = "Восстановить из файла",
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        showChevron = true,
                        modifier = Modifier.testTag("backup_restore_btn"),
                    )
                    if (backupMessage.isNotBlank()) {
                        SettingsDivider()
                        Text(
                            text = backupMessage,
                            style =
                            TextStyle(
                                fontSize = 12.sp,
                                color = if (backupIsError) ColorError else ColorSuccess,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Обновить данные колледжа",
                        subtitle = contentStatusText,
                        onClick = {
                            ContentPackWorker.checkOnce(context)
                            contentMessage = "Проверка обновления данных запущена"
                        },
                        modifier = Modifier.testTag("content_pack_update_btn"),
                    )
                    if (contentMessage.isNotBlank()) {
                        SettingsDivider()
                        Text(
                            text = contentMessage,
                            style =
                            TextStyle(
                                fontSize = 12.sp,
                                color = ColorTextMuted,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("content_pack_message"),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (BuildConfig.FLAVOR == "admin") {
                item {
                    ResetActionCard(
                        icon = Icons.Default.RestartAlt,
                        title = "Сбросить подсказки",
                        subtitle = "Подсказки в заданиях и расписании появятся снова",
                        tag = "reset_hints_card",
                        buttonTag = "reset_hints_btn",
                        filled = true,
                        onReset = {
                            com.politex.util.HintFlags.resetAll(context)
                            Toast.makeText(context, "Подсказки сброшены", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                item {
                    ResetActionCard(
                        icon = Icons.Default.GroupRemove,
                        title = "Сбросить группу",
                        subtitle = "Приложение закроется и снова спросит группу и подгруппу",
                        tag = "reset_group_card",
                        buttonTag = "reset_group_btn",
                        filled = false,
                        onReset = {
                            WidgetUpdateHelper.getPrefs(context).edit()
                                .remove("selected_group")
                                .apply()
                            SubgroupSettings.set(context, SubgroupPref.NEUTRAL)
                            (context as? android.app.Activity)?.finishAffinity()
                        },
                    )
                }
            }

            if (showUpdateSection(BuildConfig.FLAVOR)) {
                item { SectionCaption("ОБНОВЛЕНИЯ", Modifier.padding(top = 4.dp)) }
                item { UpdatesCard() }
            }

            item { SectionCaption("О ПРИЛОЖЕНИИ", Modifier.padding(top = 4.dp)) }
            item { AboutCard() }
        }
    }

    if (showBackupDialog) {
        BackupSectionsDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { sections ->
                showBackupDialog = false
                pendingBackupSections = sections
                exportLauncher.launch(defaultBackupFileName())
            },
        )
    }
}

@Composable
private fun TeacherModeCard(
    teacherName: String?,
    onSwitchRole: () -> Unit,
) {
    SettingsCard(modifier = Modifier.testTag("teacher_mode_exit_card")) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(Icons.Default.Badge)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Режим преподавателя",
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ColorTextTitle,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = teacherName ?: "Расписание преподавателя",
                        style =
                        TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            ActionChip(
                text = "СМЕНИТЬ РОЛЬ",
                filled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("teacher_mode_exit_btn"),
                onClick = onSwitchRole,
            )
        }
    }
}

@Composable
private fun ScheduleSettingsCard(
    groupInfo: GroupInfo,
    showSubgroup: Boolean,
) {
    val context = LocalContext.current
    var compactSchedule by remember { mutableStateOf(ScheduleDisplayPrefs.isCompact(context)) }
    var subgroupPref by remember { mutableStateOf(SubgroupSettings.get(context)) }

    SettingsCard {
        SettingsRow(
            icon = Icons.Default.ViewAgenda,
            title = "Компактное расписание",
            trailing = {
                SettingsSwitch(
                    checked = compactSchedule,
                    onCheckedChange = { enabled ->
                        compactSchedule = enabled
                        ScheduleDisplayPrefs.setCompact(context, enabled)
                    },
                    label = "Компактное расписание",
                    modifier = Modifier.testTag("settings_compact_schedule_switch"),
                )
            },
        )
        if (showSubgroup) {
            SettingsDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subgroup_pref_card")
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIcon(Icons.Default.Groups)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Подгруппа",
                            style =
                            TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = ColorTextTitle,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Группа ${groupInfo.canonicalName}",
                            style =
                            TextStyle(
                                fontSize = 12.sp,
                                color = ColorTextMuted,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SubgroupChip(
                        title = "Нейтрально",
                        isSelected = subgroupPref == SubgroupPref.NEUTRAL,
                        tag = "subgroup_pref_neutral",
                        modifier = Modifier.weight(1f),
                    ) {
                        subgroupPref = SubgroupPref.NEUTRAL
                        SubgroupSettings.set(context, SubgroupPref.NEUTRAL)
                    }
                    SubgroupChip(
                        title = "1-я",
                        isSelected = subgroupPref == SubgroupPref.FIRST,
                        tag = "subgroup_pref_first",
                        modifier = Modifier.weight(1f),
                    ) {
                        subgroupPref = SubgroupPref.FIRST
                        SubgroupSettings.set(context, SubgroupPref.FIRST)
                    }
                    SubgroupChip(
                        title = "2-я",
                        isSelected = subgroupPref == SubgroupPref.SECOND,
                        tag = "subgroup_pref_second",
                        modifier = Modifier.weight(1f),
                    ) {
                        subgroupPref = SubgroupPref.SECOND
                        SubgroupSettings.set(context, SubgroupPref.SECOND)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceCard(
    currentTheme: AppTheme,
    onThemePicked: (AppTheme) -> Unit,
) {
    val context = LocalContext.current
    var darkTheme by remember { mutableStateOf(AppThemeStore.isDark(context)) }

    SettingsCard {
        Row(
            modifier = Modifier
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("theme_swatches")
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTheme.entries.toList().chunked(4).forEach { rowThemes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowThemes.forEach { theme ->
                        ThemeSwatch(
                            theme = theme,
                            isSelected = currentTheme == theme,
                            onClick = { onThemePicked(theme) },
                            modifier = Modifier
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
        SettingsDivider()
        SettingsRow(
            icon = Icons.Default.DarkMode,
            title = "Тёмная тема",
            trailing = {
                SettingsSwitch(
                    checked = darkTheme,
                    onCheckedChange = { enabled ->
                        darkTheme = enabled
                        AppThemeStore.setDark(context, enabled)
                    },
                    label = "Тёмная тема",
                    modifier = Modifier.testTag("settings_dark_theme_switch"),
                )
            },
        )
    }
}

@Composable
private fun ThemeSwatch(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewBorderColor =
        if (isDarkThemeActive) {
            ColorTextMuted.copy(alpha = 0.55f)
        } else {
            ColorBorderLight
        }
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) ColorBrandBlue else ColorBorderLight,
        ),
        modifier = modifier
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            }
            .bouncyClickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(theme.palette.topBar, RoundedCornerShape(2.dp))
                    .border(1.dp, previewBorderColor, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 6.dp)
                        .background(theme.palette.brandBlue, RoundedCornerShape(1.dp)),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = theme.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style =
                TextStyle(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = ColorTextTitle,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun MapSettingsCard() {
    val context = LocalContext.current
    var routesEnabled by remember {
        mutableStateOf(
            WidgetUpdateHelper.getPrefs(context)
                .getBoolean("map_routes_enabled", true),
        )
    }

    SettingsCard(modifier = Modifier.testTag("map_routes_toggle")) {
        SettingsRow(
            icon = Icons.Default.Directions,
            title = "Маршруты по карте",
            subtitle = if (routesEnabled) null else "Кабинеты без маршрута",
            trailing = {
                SettingsSwitch(
                    checked = routesEnabled,
                    onCheckedChange = { enabled ->
                        routesEnabled = enabled
                        WidgetUpdateHelper.getPrefs(context)
                            .edit()
                            .putBoolean("map_routes_enabled", enabled)
                            .apply()
                    },
                    label = "Маршруты по карте",
                )
            },
        )
    }
}

@Composable
private fun ResetActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    buttonTag: String,
    filled: Boolean,
    onReset: () -> Unit,
) {
    SettingsCard(modifier = Modifier.testTag(tag)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(icon)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorTextTitle,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style =
                    TextStyle(
                        fontSize = 12.sp,
                        color = ColorTextMuted,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            ActionChip(
                text = "СБРОСИТЬ",
                filled = filled,
                modifier = Modifier.testTag(buttonTag),
                onClick = onReset,
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current

    SettingsCard {
        SettingsRow(
            icon = Icons.Default.Language,
            title = "Сайт колледжа",
            subtitle = "guo-mpk.by",
            onClick = { openLink(context, "https://guo-mpk.by/") },
            showChevron = true,
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Default.CalendarMonth,
            title = "Портал расписания",
            subtitle = "guo-mpk.by/raspisanie/",
            onClick = { openLink(context, "https://guo-mpk.by/raspisanie/") },
            showChevron = true,
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Default.Send,
            title = "Telegram",
            subtitle = "@Betterthannothing12",
            onClick = { openLink(context, "https://t.me/Betterthannothing12") },
            showChevron = true,
            modifier = Modifier.testTag("settings_telegram_row"),
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Default.Info,
            title = "Версия",
            trailing = {
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style =
                    TextStyle(
                        fontSize = 13.sp,
                        color = ColorTextMuted,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
    }
}

@Composable
private fun UpdatesCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val channelRevision = UpdateChannelStore.revision.intValue
    var channel by remember(context, channelRevision) {
        mutableStateOf(UpdateChannelStore.channel(context))
    }
    var statusText by remember(channel) {
        mutableStateOf(defaultUpdateStatus(channel, BuildConfig.VERSION_NAME))
    }
    var statusColor by remember(channel) { mutableStateOf(ColorTextMuted) }
    var checking by remember { mutableStateOf(false) }
    val foundInfo by UpdateInstallController.foundUpdate.collectAsState()
    val installState by UpdateInstallController.state.collectAsState()

    fun pickChannel(next: UpdateChannel) {
        val previous = channel
        UpdateChannelStore.set(context, next)
        channel = next
        UpdateInstallController.cancelAndReset(context)
        if (channelChangeRequiresCheck(previous, next)) {
            ContentPackWorker.reschedule(context)
            AppUpdateWorker.reschedule(context)
            AppUpdateWorker.checkOnce(context)
        }
    }

    fun runCheck() {
        if (checking) return
        checking = true
        UpdateInstallController.cancelAndReset(context)
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateCheckController.checkNow(context) }
            statusText = manualResultText(result, channel, BuildConfig.VERSION_NAME)
            statusColor =
                when {
                    manualResultIsError(result) -> ColorError
                    manualResultIsFound(result) -> ColorSuccess
                    else -> ColorTextMuted
                }
            val info = (result as? ManualCheckResult.UpdateFound)?.info
            UpdateInstallController.rememberFound(context, info)
            checking = false
        }
    }

    LaunchedEffect(installState, foundInfo) {
        when (val current = installState) {
            is InstallButtonState.Downloading -> {
                statusText = downloadingStatusText(current.progress)
                statusColor = ColorTextMuted
            }
            is InstallButtonState.Downloaded -> {
                statusText = downloadedStatusText(foundInfo?.versionName)
                statusColor = ColorSuccess
            }
            is InstallButtonState.InstallLaunched -> {
                statusText = "Установщик запущен"
                statusColor = ColorSuccess
            }
            is InstallButtonState.Error -> {
                statusText = installErrorMessage(current.cause)
                statusColor = ColorError
                UpdateInstallController.acknowledgeError()
            }
            InstallButtonState.IdleFound -> Unit
        }
    }

    SettingsCard(modifier = Modifier.testTag("updates_card")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("update_channel_card")
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIcon(Icons.Default.Tune)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Канал обновлений",
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ColorTextTitle,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Превью выходят раньше, но могут быть нестабильными",
                        style =
                        TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SubgroupChip(
                    title = "Стабильные",
                    isSelected = channel == UpdateChannel.STABLE,
                    tag = "update_channel_stable",
                    modifier = Modifier.weight(1f),
                ) {
                    pickChannel(UpdateChannel.STABLE)
                }
                SubgroupChip(
                    title = "Превью",
                    isSelected = channel == UpdateChannel.PREVIEW,
                    tag = "update_channel_preview",
                    modifier = Modifier.weight(1f),
                ) {
                    pickChannel(UpdateChannel.PREVIEW)
                }
            }
        }
        SettingsDivider()
        SettingsRow(
            icon = Icons.Default.SystemUpdate,
            title = if (checking) "Проверка обновлений…" else "Проверить обновления",
            onClick = { runCheck() },
            modifier = Modifier.testTag("update_check_btn"),
        )
        SettingsDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusText,
                style =
                TextStyle(
                    fontSize = 12.sp,
                    color = statusColor,
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("update_status")
                    .padding(vertical = 8.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (foundInfo != null) {
                Spacer(modifier = Modifier.width(8.dp))
                UpdateInstallButton(
                    state = installState,
                    onClick = { UpdateInstallController.onButtonTap(context, foundInfo) },
                    modifier = Modifier.testTag("update_install_btn"),
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = ColorBrandBlue,
            uncheckedTrackColor = ColorBorderLight,
            uncheckedThumbColor = ColorTextMuted,
        ),
        modifier = modifier.then(
            if (label != null) {
                val description = label
                Modifier.semantics { contentDescription = description }
            } else {
                Modifier
            },
        ),
    )
}

@Composable
private fun BellCountdownSection() {
    val context = LocalContext.current
    var isOngoingEnabled by remember {
        mutableStateOf(BellCountdownNotifier.isEnabled(context))
    }
    var selectedStyle by remember {
        mutableStateOf(BellCountdownNotifier.getStyle(context))
    }
    var selectedTheme by remember {
        mutableStateOf(BellCountdownNotifier.getTheme(context))
    }
    var selectedScope by remember {
        mutableStateOf(BellCountdownNotifier.getScope(context))
    }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }
    var exactAlarmAsked by rememberSaveable { mutableStateOf(false) }

    var canExactAlarms by remember { mutableStateOf(ExactAlarmGate.canScheduleExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canExactAlarms = ExactAlarmGate.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestExactAlarmIfNeeded() {
        if (ExactAlarmGate.shouldRequest(Build.VERSION.SDK_INT, canExactAlarms, exactAlarmAsked)) {
            exactAlarmAsked = true
            ExactAlarmGate.openSettings(context)
        }
    }

    fun enableLine() {
        isOngoingEnabled = true
        BellCountdownNotifier.setEnabled(context, true)
        com.politex.widget.WidgetAlarm.scheduleNext(context)
        requestExactAlarmIfNeeded()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableLine()
        } else {
            isOngoingEnabled = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bell_notification_card")
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIcon(Icons.Default.Timer)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Строка «до звонка»",
                    style =
                    TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ColorTextTitle,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isOngoingEnabled) "Показывается в шторке" else "Отключена",
                    style =
                    TextStyle(
                        fontSize = 12.sp,
                        color = ColorTextMuted,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(Tokens.HitTarget)
                    .semantics {
                        contentDescription =
                            if (settingsExpanded) "Скрыть настройки строки" else "Настройки строки"
                    }
                    .bouncyClickable { settingsExpanded = !settingsExpanded }
                    .testTag("bell_settings_toggle"),
                contentAlignment = Alignment.Center,
            ) {
                SettingsIcon(
                    icon = Icons.Default.Settings,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            SettingsSwitch(
                checked = isOngoingEnabled,
                onCheckedChange = { enable ->
                    if (enable) {
                        val needsPermission =
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            enableLine()
                        }
                    } else {
                        isOngoingEnabled = false
                        BellCountdownNotifier.setEnabled(context, false)
                    }
                },
                label = "Строка «до звонка»",
                modifier = Modifier.testTag("bell_notification_switch"),
            )
        }

        if (isOngoingEnabled && ExactAlarmGate.hintNeeded(Build.VERSION.SDK_INT, canExactAlarms)) {
            ExactAlarmHint(onAllow = { ExactAlarmGate.openSettings(context) })
        }

        AnimatedVisibility(
            visible = settingsExpanded,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 62.dp, end = 14.dp, bottom = 14.dp),
            ) {
                BellSectionLabel("СТИЛЬ СТРОКИ")
                BellCountdownNotifier.Style.entries
                    .filter { it != BellCountdownNotifier.Style.WITH_APP_NAME }
                    .forEach { style ->
                        BellChoiceRow(
                            isSelected = selectedStyle == style,
                            title = bellStyleLabel(style),
                            tag = "bell_style_${style.id}",
                            onClick = {
                                selectedStyle = style
                                BellCountdownNotifier.setStyle(context, style)
                                requestExactAlarmIfNeeded()
                            },
                        )
                    }

                BellSectionLabel("ЦВЕТОВАЯ ТЕМА")
                BellCountdownNotifier.Theme.entries.forEach { theme ->
                    val swatchColor =
                        when {
                            theme == BellCountdownNotifier.Theme.APP -> ColorBrandFill
                            theme.accent != 0 -> Color(theme.accent)
                            else -> ColorTextMuted
                        }
                    BellChoiceRow(
                        isSelected = selectedTheme == theme,
                        title = theme.title,
                        tag = "bell_theme_${theme.id}",
                        swatchColor = swatchColor,
                        onClick = {
                            selectedTheme = theme
                            BellCountdownNotifier.setTheme(context, theme)
                        },
                    )
                }

                BellSectionLabel("СЧИТАТЬ ДО КОНЦА")
                val teacherModeOn = TeacherModeStore.isTeacher(context)
                BellCountdownNotifier.Scope.entries.forEach { scope ->
                    BellChoiceRow(
                        isSelected = selectedScope == scope,
                        title = scope.title,
                        description =
                            if (teacherModeOn && scope == BellCountdownNotifier.Scope.MY_LESSONS) {
                                "Счётчик заканчивается с последним уроком в вашем расписании"
                            } else {
                                scope.description
                            },
                        tag = "bell_scope_${scope.id}",
                        onClick = {
                            selectedScope = scope
                            BellCountdownNotifier.setScope(context, scope)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BellSectionLabel(text: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = text,
        style =
        TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = ColorBrandBlue,
        ),
    )
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun BellChoiceRow(
    isSelected: Boolean,
    title: String,
    tag: String,
    onClick: () -> Unit,
    description: String? = null,
    swatchColor: Color? = null,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (isSelected) ColorSurfaceHighlight else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.HitTarget)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            }
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (swatchColor != null) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(swatchColor, CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style =
                    TextStyle(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (isSelected) ColorTextTitle else ColorTextBody,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description != null) {
                    Text(
                        text = description,
                        style =
                        TextStyle(
                            fontSize = 11.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmHint(onAllow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceHighlight,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp, end = 14.dp, bottom = 10.dp)
            .testTag("exact_alarm_warning"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AlarmOff,
                contentDescription = null,
                tint = ColorWarning,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Разрешите точные будильники",
                style =
                TextStyle(
                    fontSize = 12.sp,
                    color = ColorTextBody,
                ),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            ActionChip(
                text = "РАЗРЕШИТЬ",
                filled = true,
                onClick = onAllow,
            )
        }
    }
}

@Composable
private fun BackupSectionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Set<BackupManager.BackupSection>) -> Unit,
) {
    var selected by remember { mutableStateOf(BackupManager.BackupSection.ALL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        modifier = Modifier.testTag("backup_sections_dialog"),
        title = {
            Text(
                text = "Что сохранить",
                style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorTextTitle,
                ),
            )
        },
        text = {
            Column {
                BackupManager.BackupSection.entries.forEach { section ->
                    val checked = section in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Tokens.HitTarget)
                            .bouncyClickable {
                                selected =
                                    if (checked) selected - section else selected + section
                            }
                            .testTag("backup_opt_${section.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected =
                                    if (checked) selected - section else selected + section
                            },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = backupSectionTitle(section),
                            style =
                            TextStyle(
                                fontSize = 14.sp,
                                color = ColorTextBody,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier = Modifier
                    .heightIn(min = Tokens.ButtonHeight)
                    .then(if (selected.isEmpty()) Modifier else Modifier.bouncyClickable { onConfirm(selected) })
                    .testTag("backup_confirm_btn"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "СОХРАНИТЬ",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = Tokens.ButtonText,
                            color = if (selected.isEmpty()) ColorTextMuted else Color.White,
                        ),
                    )
                }
            }
        },
        dismissButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBgMain,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier = Modifier
                    .heightIn(min = Tokens.ButtonHeight)
                    .bouncyClickable(onClick = onDismiss)
                    .testTag("backup_cancel_btn"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "ОТМЕНА",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = Tokens.ButtonText,
                            color = ColorTextTitle,
                        ),
                    )
                }
            }
        },
    )
}

private fun backupSectionTitle(section: BackupManager.BackupSection): String = when (section) {
    BackupManager.BackupSection.SCHEDULE_HISTORY -> "История расписания"
    BackupManager.BackupSection.TASKS -> "Задания"
    BackupManager.BackupSection.GRADES -> "Оценки"
    BackupManager.BackupSection.SUBJECT_NAMES -> "Названия предметов"
    BackupManager.BackupSection.SETTINGS -> "Настройки"
}

private fun bellStyleLabel(style: BellCountdownNotifier.Style): String = when (style) {
    BellCountdownNotifier.Style.COMPACT,
    BellCountdownNotifier.Style.WITH_APP_NAME,
    -> "В минутах"
    BellCountdownNotifier.Style.WITH_LESSON -> "В минутах + идущий урок"
    BellCountdownNotifier.Style.WITH_PROGRESS -> "С полосой прогресса"
    BellCountdownNotifier.Style.WITH_SECONDS -> "В секундах"
}

private fun openLink(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "Нет браузера", Toast.LENGTH_SHORT).show()
        false
    }

@Composable
private fun ActionChip(
    text: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (filled) ColorBrandFill else ColorBgMain,
        border = BorderStroke(1.dp, if (filled) ColorBrandFill else ColorBorderLight),
        modifier = modifier
            .heightIn(min = Tokens.ButtonHeight)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = Tokens.ButtonText,
                    color = if (filled) Color.White else ColorTextTitle,
                ),
            )
        }
    }
}

@Composable
private fun SubgroupChip(
    title: String,
    isSelected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
        color = if (isSelected) ColorTopBar else ColorBgMain,
        modifier = modifier
            .heightIn(min = Tokens.HitTarget)
            .semantics {
                role = Role.RadioButton
                selected = isSelected
            }
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = title,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                TextStyle(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

internal fun showSubgroupCard(teacherMode: Boolean): Boolean = !teacherMode

internal fun showTeacherModeExit(teacherMode: Boolean): Boolean = teacherMode

internal fun showUpdateSection(flavor: String): Boolean = AppUpdateChecker.UPDATE_ENABLED_FLAVORS.contains(flavor)