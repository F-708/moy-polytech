package com.politex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.politex.data.repository.ScheduleRepository
import com.politex.ui.screens.LessonCard
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStyleCalendarMonth
import com.politex.ui.theme.TextStyleCalendarWeekdays
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.SubjectFormatter
import com.politex.util.SubjectRenameStore
import java.util.Calendar
import java.util.Locale

private val MONTH_NAMES_CAPS =
    listOf(
        "ЯНВАРЬ",
        "ФЕВРАЛЬ",
        "МАРТ",
        "АПРЕЛЬ",
        "МАЙ",
        "ИЮНЬ",
        "ИЮЛЬ",
        "АВГУСТ",
        "СЕНТЯБРЬ",
        "ОКТЯБРЬ",
        "НОЯБРЬ",
        "ДЕКАБРЬ",
    )

private val WEEKDAY_HEADERS = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")

@Composable
fun CalendarArchiveDialog(
    groupName: String,
    scheduleRepository: ScheduleRepository,
    onDismiss: () -> Unit,
    onDateSelected: ((dayOfWeek: Int, dateString: String) -> Unit)? = null,
    onOpenRoom: (String) -> Unit = {},
    onOpenTeacher: (String) -> Unit = {},
    isTeacherKnown: (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val calendar =
        com.politex.ui.util
            .rememberToday()
    var displayedYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var displayedMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }

    var selectedDateString by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val savedDates by scheduleRepository.getDistinctDates(groupName).collectAsState(initial = emptyList())
    val allGroupLessons by scheduleRepository.getAllLessonsForGroup(groupName).collectAsState(initial = emptyList())

    val savedDatesDot =
        remember(savedDates) {
            savedDates
                .mapNotNull { d ->
                    com.politex.data.network.MpkScheduleParser.parseDotDate(d)?.let { cal ->
                        "%02d.%02d.%04d".format(
                            cal.get(Calendar.DAY_OF_MONTH),
                            cal.get(Calendar.MONTH) + 1,
                            cal.get(Calendar.YEAR),
                        )
                    }
                }.toSet()
        }

    val weeklyLessonDows =
        remember(allGroupLessons) {
            allGroupLessons.filter { it.dateString.isBlank() }.map { it.dayOfWeek }.toSet()
        }

    val selectedDateDot =
        remember(selectedDateString) {
            if (selectedDateString.isBlank()) {
                ""
            } else {
                com.politex.data.network.MpkScheduleParser.parseDotDate(selectedDateString)?.let { cal ->
                    "%02d.%02d.%04d".format(
                        cal.get(Calendar.DAY_OF_MONTH),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.YEAR),
                    )
                } ?: ""
            }
        }

    val selectedDateLessons =
        remember(selectedDateDot, allGroupLessons) {
            if (selectedDateDot.isBlank()) {
                emptyList()
            } else {
                val exact = allGroupLessons.filter { it.dateString == selectedDateDot }
                if (exact.isNotEmpty()) {
                    exact
                } else {
                    val dow =
                        com.politex.data.network.MpkScheduleParser
                            .parseDotDate(selectedDateDot)
                            ?.let {
                                com.politex.data.network.MpkScheduleParser
                                    .dowOfCalendar(it)
                            }
                    if (dow != null) {
                        allGroupLessons.filter { it.dateString.isBlank() && it.dayOfWeek == dow }
                    } else {
                        emptyList()
                    }
                }
            }
        }

    val renameRevision = SubjectRenameStore.revision.intValue

    val searchResults =
        remember(searchQuery, allGroupLessons, renameRevision) {
            if (searchQuery.isBlank()) {
                emptyList()
            } else {
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                allGroupLessons.filter {
                    it.subjectRaw.lowercase(Locale.ROOT).contains(q) ||
                        it.teacherFirst.lowercase(Locale.ROOT).contains(q) ||
                        it.teacherSecond.lowercase(Locale.ROOT).contains(q) ||
                        it.roomFirst.lowercase(Locale.ROOT).contains(q) ||
                        it.roomSecond.lowercase(Locale.ROOT).contains(q) ||
                        SubjectFormatter.getShortName(it.subjectRaw).lowercase(Locale.ROOT).contains(q)
                }
            }
        }

    val prevMonthIndex = if (displayedMonth == 0) 11 else displayedMonth - 1
    val nextMonthIndex = if (displayedMonth == 11) 0 else displayedMonth + 1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            shadowElevation = 0.dp,
            modifier =
            modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f)
                .testTag("calendar_archive_dialog"),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "АРХИВ РАСПИСАНИЯ",
                            style = TextStyleCalendarMonth.copy(fontSize = 16.sp, color = ColorBrandBlue),
                        )
                        Text(
                            text = "ГРУППА $groupName",
                            style = TextStyleCalendarWeekdays.copy(fontSize = 12.sp, color = ColorTextMuted),
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, ColorBorderLight),
                        modifier =
                        Modifier
                            .size(Tokens.IconHit)
                            .bouncyClickable(onClick = onDismiss)
                            .testTag("close_archive_dialog_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = ColorTextBody,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Поиск: предмет, преподаватель, кабинет...",
                            style =
                            androidx.compose.ui.text
                                .TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(Tokens.IconHit),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Очистить",
                                    tint = ColorTextMuted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = ColorSurfaceVariantLight,
                        unfocusedContainerColor = ColorSurfaceVariantLight,
                        focusedIndicatorColor = ColorBrandBlue,
                        unfocusedIndicatorColor = ColorBorderLight,
                    ),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("archive_search_input"),
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "НАЙДЕНО ЗАНЯТИЙ: ${searchResults.size}",
                        style = TextStyleCalendarWeekdays.copy(color = ColorBrandBlue),
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (searchResults.isEmpty()) {
                        SearchEmptyState(
                            query = searchQuery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(searchResults, key = { it.id }) { lesson ->

                                LessonCard(
                                    lesson = lesson,
                                    isTeacherKnown = isTeacherKnown,
                                    onTeacherClick = onOpenTeacher,
                                    onRoomClick = onOpenRoom,
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppCard(
                            modifier = Modifier
                                .bouncyClickable {
                                    if (displayedMonth == 0) {
                                        displayedMonth = 11
                                        displayedYear -= 1
                                    } else {
                                        displayedMonth -= 1
                                    }
                                }.testTag("calendar_prev_month"),
                        ) {
                            Text(
                                text = "< ${MONTH_NAMES_CAPS[prevMonthIndex]}",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ColorBrandBlue,
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }

                        Text(
                            text = "${MONTH_NAMES_CAPS[displayedMonth]} $displayedYear",
                            style = TextStyleCalendarMonth,
                            textAlign = TextAlign.Center,
                        )

                        AppCard(
                            modifier = Modifier
                                .bouncyClickable {
                                    if (displayedMonth == 11) {
                                        displayedMonth = 0
                                        displayedYear += 1
                                    } else {
                                        displayedMonth += 1
                                    }
                                }.testTag("calendar_next_month"),
                        ) {
                            Text(
                                text = "${MONTH_NAMES_CAPS[nextMonthIndex]} >",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ColorBrandBlue,
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        WEEKDAY_HEADERS.forEach { dayName ->
                            Text(
                                text = dayName,
                                style = TextStyleCalendarWeekdays,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    AppDivider()

                    Spacer(modifier = Modifier.height(4.dp))

                    val daysInMonth =
                        remember(displayedYear, displayedMonth) {
                            calculateMonthDays(displayedYear, displayedMonth)
                        }

                    val weekCount = (daysInMonth.size + 6) / 7
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height((weekCount * 46).dp),
                        userScrollEnabled = false,
                    ) {
                        items(daysInMonth) { calendarDay ->
                            if (calendarDay == null) {
                                Box(modifier = Modifier.aspectRatio(1.1f))
                            } else {
                                val dotStr =
                                    String.format(
                                        Locale.ROOT,
                                        "%02d.%02d.%04d",
                                        calendarDay,
                                        displayedMonth + 1,
                                        displayedYear,
                                    )
                                val isSelected = selectedDateDot == dotStr
                                val hasData =
                                    savedDatesDot.contains(dotStr) ||
                                        (
                                            com.politex.data.network.MpkScheduleParser.parseDotDate(dotStr)?.let { cal ->
                                                com.politex.data.network.MpkScheduleParser
                                                    .dowOfCalendar(cal) in weeklyLessonDows
                                            } ?: false
                                            )

                                Box(
                                    modifier =
                                    Modifier
                                        .aspectRatio(1.1f)
                                        .clickable {
                                            selectedDateString = dotStr

                                            onDateSelected?.let { callback ->
                                                val cal =
                                                    com.politex.data.network.MpkScheduleParser
                                                        .parseDotDate(dotStr)
                                                val dow =
                                                    com.politex.data.network.MpkScheduleParser
                                                        .dowOfCalendar(cal ?: Calendar.getInstance())
                                                callback(dow, dotStr)
                                            }
                                        }.testTag("calendar_day_$calendarDay"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier =
                                            Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(ColorTopBar),
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = "$calendarDay",
                                            style =
                                            androidx.compose.ui.text.TextStyle(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = if (isSelected) Color.White else ColorTextBody,
                                            ),
                                        )

                                        if (hasData && !isSelected) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier =
                                                Modifier
                                                    .size(3.dp)
                                                    .clip(CircleShape)
                                                    .background(ColorBrandBlue),
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AppDivider()

                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedDateString.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "УРОКИ НА $selectedDateString (${selectedDateLessons.size})",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ColorTextTitle,
                                ),
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (selectedDateLessons.isEmpty()) {
                            Box(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.EventBusy,
                                        contentDescription = null,
                                        tint = ColorTextMuted,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "На этот день в архиве нет уроков",
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            color = ColorTextMuted,
                                            fontSize = 13.sp,
                                        ),
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(selectedDateLessons, key = { it.id }) { lesson ->

                                    LessonCard(
                                        lesson = lesson,
                                        isTeacherKnown = isTeacherKnown,
                                        onTeacherClick = onOpenTeacher,
                                        onRoomClick = onOpenRoom,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Выберите день в календаре для просмотра архива",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    color = ColorTextMuted,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
