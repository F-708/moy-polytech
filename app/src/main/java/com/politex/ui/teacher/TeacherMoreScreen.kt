package com.politex.ui.teacher

import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.BuildConfig
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.components.AppCard
import com.politex.ui.components.BackButton
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.Dow
import java.util.Calendar
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun TeacherMoreScreen(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},

    onOpenBells: () -> Unit = {},
    onOpenMap: () -> Unit = {},

    onOpenGroupSchedule: () -> Unit = {},
    onOpenTeachers: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(ColorBgMain),
    ) {
        Header(title = "Ещё", subtitle = "Профиль, преподаватели и настройки")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.AccountCircle, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = profile?.name ?: "Выберите себя",
                    subtitle = if (profile != null) "Нажмите, чтобы сменить преподавателя" else "Найти себя в списке преподавателей",
                    onClick = onPickProfile,
                    testTag = "more_profile",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.AccessTime, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Расписание звонков",
                    subtitle = "Время звонков на сегодня и по дням",
                    onClick = onOpenBells,
                    testTag = "more_bells",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.LocationOn, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Карта колледжа",
                    subtitle = "Планы этажей и поиск кабинета",
                    onClick = onOpenMap,
                    testTag = "more_map",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.DateRange, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Найти группу",
                    subtitle = "Расписание любой группы колледжа",
                    onClick = onOpenGroupSchedule,
                    testTag = "more_group",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Преподаватели",
                    subtitle = "Поиск по базе, избранное и фотографии",
                    onClick = onOpenTeachers,
                    testTag = "more_teachers",
                )
            }
            item {
                MenuCard(
                    icon = { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp)) },
                    title = "Настройки",
                    subtitle = "Профиль, «до звонка», масштаб и тема",
                    onClick = onOpenSettings,
                    testTag = "more_settings",
                )
            }
        }
    }
}

@Composable
private fun MenuCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ColorTextTitle),
                )
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = title, style = TextStylePageTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = subtitle,
            style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorBrandBlue),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TeacherBellsPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalTeacherMetrics.current
    val dow = remember { Dow.of(Calendar.getInstance()) }
    val bells = remember(dow) { CollegeBellSchedule.getBellsForDay(dow) }
    val dowTitle = remember(dow) { dowTitle(dow) }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack = onBack, testTag = "bells_back")
            Spacer(Modifier.width(10.dp))
            Text("Звонки · $dowTitle", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle))
        }
        Text(
            "Звонок по стандартному расписанию колледжа. Сокращённые дни и переносы приложение не определяет — сверяйтесь с объявлением учебной части.",
            style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp).testTag("bells_honest_text"),
        )
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            bells.forEach { bell ->
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${bell.lessonNumber}",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorBrandBlue),
                            modifier = Modifier.widthIn(min = 34.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bell.title, style = TextStyle(fontSize = metrics.body, color = ColorTextBody))
                            if (bell.breakAfterMinutes > 0) {
                                Text("перемена ${bell.breakAfterMinutes} мин", style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted))
                            }
                        }
                        Text(bell.displayRange, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun dowTitle(dow: Int): String =
    when (dow) {
        1 -> "понедельник"
        2 -> "вторник"
        3 -> "среда"
        4 -> "четверг"
        5 -> "пятница"
        6 -> "суббота"
        else -> "воскресенье"
    }

internal const val TEACHER_ABOUT_DISCLAIMER =
    "Неофициальное приложение. Данные из открытых источников guo-mpk.by. Может отставать от приказов о заменах."

internal fun teacherAboutVersion(): String = BuildConfig.VERSION_NAME
