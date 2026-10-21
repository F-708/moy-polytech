package com.politex.ui.teacher

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.politex.data.repository.LessonStatus
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLogic
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.components.CenteredLoader
import com.politex.ui.components.DaySlotCell
import com.politex.ui.components.NoLessonCard
import com.politex.ui.components.SimpleDatePickerDialog
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TeacherHomeScreen(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    onRoomClick: (String) -> Unit = {},
    onGroupClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherDayViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(profile) { viewModel.setProfile(profile) }

    if (profile == null) {
        PickProfilePrompt(onPickProfile = onPickProfile, modifier = modifier)
        return
    }

    val todayCal = Calendar.getInstance()
    var showDatePicker by remember { mutableStateOf(false) }

    val subtitleText = remember(state.dateKey, state.isToday, state.dayOffset, state.selectedDate) {
        val parts = state.dateKey.split(".")
        val ddMM = if (parts.size >= 2) "${parts[0]}.${parts[1]}" else state.dateKey
        val prefix =
            when {
                state.selectedDate.isNotBlank() -> dayNameNominative(state.dow)
                state.isToday -> "Сегодня"
                isTomorrow(state.dateKey, todayCal) -> "Завтра"
                else -> dayNameNominative(state.dow).lowercase()
            }
        "$prefix, $ddMM"
    }

    if (showDatePicker) {
        SimpleDatePickerDialog(
            initialDateIso = isoOf(state.dateKey),
            minYear = todayCal.get(Calendar.YEAR) - 1,
            maxYear = todayCal.get(Calendar.YEAR) + 1,
            onDismiss = { showDatePicker = false },
            onConfirm = { iso ->
                showDatePicker = false
                dotDateOf(iso)?.let { viewModel.selectDate(it) }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Расписание",
                    style = TextStylePageTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorBrandBlue),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text =
                        if (state.lastSyncEpoch > 0L) {
                            "обновлено в " + SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru")).format(Date(state.lastSyncEpoch))
                        } else {
                            "данные кэша"
                        },
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier =
                    Modifier
                        .sizeIn(minWidth = Tokens.HitTarget, minHeight = Tokens.HitTarget)
                        .bouncyClickable(onClick = { viewModel.refresh() })
                        .testTag("teacher_refresh"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (state.refreshing) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Синхронизировать с сайтом",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DaySlotCell(
                title = "СЕГОДНЯ",
                subtitle = slotSubtitle(0, todayCal),
                selected = state.selectedDate.isBlank() && state.dayOffset == 0,
                onClick = { viewModel.selectDay(0) },
                modifier = Modifier.weight(1f).testTag("teacher_day_today"),
            )
            DaySlotCell(
                title = "ЗАВТРА",
                subtitle = slotSubtitle(1, todayCal),
                selected = state.selectedDate.isBlank() && state.dayOffset == 1,
                onClick = { viewModel.selectDay(1) },
                modifier = Modifier.weight(1f).testTag("teacher_day_tomorrow"),
            )
            AppCard(
                modifier =
                    Modifier
                        .size(Tokens.HitTarget)
                        .bouncyClickable { showDatePicker = true }
                        .testTag("teacher_calendar_button"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Выбрать дату",
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        AppDivider(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

        val slots = remember(state.lessons) { teacherSlots(state.lessons) }
        val statusByNumber =
            remember(state.lessons, state.isToday) {

                val c = Calendar.getInstance()
                val nowMinutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
                TeacherDayLogic
                    .statuses(state.lessons, nowMinutes, isToday = state.isToday)
                    .mapIndexed { i, s -> state.lessons[i].lessonNumber to s }
                    .toMap()
            }

        val listState = rememberLazyListState()
        LaunchedEffect(slots, statusByNumber, state.dateKey) {

            val idx = slots.indexOfFirst { (_, lesson) ->
                lesson != null && statusByNumber[lesson.lessonNumber].let { it == LessonStatus.NOW || it == LessonStatus.NEXT }
            }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }

        when {
            state.loading && state.lessons.isEmpty() ->
                CenteredLoader(modifier = Modifier.fillMaxWidth().weight(1f))

            state.lessons.isEmpty() ->
                TeacherEmptyState(
                    text = state.error.ifBlank { TeacherDayLogic.emptyDayText(state.dow, state.nextLesson, isToday = state.isToday) },
                    hasError = state.error.isNotBlank(),
                    onSync = { viewModel.refresh() },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.ambiguous) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Не удалось точно определить (однофамильцы) — проверьте список",
                                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextMuted),
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                    items(slots, key = { it.first }) { (number, lesson) ->
                        if (lesson != null) {
                            TeacherLessonCard(
                                lesson = lesson,
                                isCurrent = statusByNumber[lesson.lessonNumber] == LessonStatus.NOW,
                                onRoomClick = onRoomClick,
                                onGroupClick = onGroupClick,
                            )
                        } else {
                            NoLessonCard(number)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeacherLessonCard(
    lesson: TeacherDayLesson,
    isCurrent: Boolean,
    onRoomClick: (String) -> Unit,
    onGroupClick: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, if (isCurrent) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().testTag("lesson_card_${lesson.lessonNumber}"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    modifier = Modifier.size(24.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${lesson.lessonNumber}",
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (lesson.start.isNotBlank()) {
                    Text(
                        text = "${lesson.start} – ${lesson.end}",
                        style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ColorTextBody),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val noSubject = lesson.subject.isBlank()
            Text(
                text = if (noSubject) "Предмет уточняется" else lesson.subject,
                style =
                    TextStyle(
                        fontWeight = if (noSubject) FontWeight.Normal else FontWeight.Bold,
                        fontSize = if (noSubject) 11.sp else 15.sp,
                        color = if (noSubject) ColorTextMuted else ColorTextTitle,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (lesson.groups.isNotEmpty()) {
                    GroupChip(
                        name = lesson.groups.joinToString(", "),

                        onClick = if (lesson.groups.size == 1) {
                            { onGroupClick(lesson.groups[0]) }
                        } else {
                            null
                        },
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (lesson.room.isNotBlank()) {
                    RoomButton(room = lesson.room, onClick = { onRoomClick(lesson.room) })
                }
            }
        }
    }
}

@Composable
private fun GroupChip(
    name: String,
    onClick: (() -> Unit)?,
) {
    if (onClick == null) {
        Text(
            text = name,
            style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Box(
        modifier = Modifier.heightIn(min = 44.dp).bouncyClickable(scaleDown = 0.97f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
            color = ColorSurfaceHighlight,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 5.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = name,
                    style = TextStyle(fontSize = 12.sp, color = ColorBrandBlue, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Мои пары у этой группы",
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun RoomButton(
    room: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp).bouncyClickable(scaleDown = 0.97f, onClick = onClick).testTag("teacher_room_$room"),
        contentAlignment = Alignment.Center,
    ) {
        AppCard(modifier = Modifier.wrapContentWidth(), color = ColorSurfaceHighlight) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "каб. $room",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ColorBrandBlue),
                )
            }
        }
    }
}

@Composable
private fun TeacherEmptyState(
    text: String,
    hasError: Boolean,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(ColorSurfaceHighlight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = null,
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ColorTextTitle),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBrandBlue),
                color = ColorBrandFill,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .bouncyClickable(onClick = onSync)
                        .testTag("teacher_empty_sync_btn"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasError) "ПОВТОРИТЬ" else "СИНХРОНИЗИРОВАТЬ С САЙТОМ",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickProfilePrompt(
    onPickProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(ColorBgMain).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Выберите себя", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextTitle))
        Spacer(Modifier.height(8.dp))
        Text(
            "Чтобы увидеть свой день, найдите себя в списке преподавателей колледжа.",
            style = TextStyle(fontSize = 14.sp, color = ColorTextMuted),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBrandFill,
            modifier = Modifier.heightIn(min = Tokens.HitTarget).bouncyClickable(onClick = onPickProfile).testTag("teacher_pick_profile"),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
                Text("Выбрать", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
}

internal fun slotSubtitle(
    offset: Int,
    base: Calendar = Calendar.getInstance(),
): String {
    val cal = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
    return String.format(Locale.ROOT, "%02d.%02d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
}

private val DAY_NAMES = listOf("", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

private fun dayNameNominative(dow: Int): String = DAY_NAMES.getOrElse(dow) { "" }

private fun isTomorrow(
    dateKey: String,
    today: Calendar,
): Boolean {
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    return com.politex.util.DateFormats.dotDate(tomorrow) == dateKey
}

private fun isoOf(dotDate: String): String? {
    val p = dotDate.split(".")
    return if (p.size == 3) "${p[2]}-${p[1]}-${p[0]}" else null
}

private fun dotDateOf(iso: String): String? {
    val p = iso.split("-")
    return if (p.size == 3) "${p[2]}.${p[1]}.${p[0]}" else null
}

internal fun teacherSlots(lessons: List<TeacherDayLesson>): List<Pair<Int, TeacherDayLesson?>> {
    if (lessons.isEmpty()) return emptyList()
    val byNumber = lessons.associateBy { it.lessonNumber }
    return (1..lessons.maxOf { it.lessonNumber }).map { number -> number to byNumber[number] }
}
