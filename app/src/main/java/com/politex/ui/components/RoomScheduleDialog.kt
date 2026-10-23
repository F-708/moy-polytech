package com.politex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.politex.data.local.entity.LessonEntity
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.subjectDisplayName

fun filterRoomLessonsForDate(
    lessons: List<LessonEntity>,
    room: String,
    dateString: String,
): List<LessonEntity> {
    if (room.isBlank()) return emptyList()
    val inRoom = lessons.filter { it.roomFirst == room || it.roomSecond == room }
    val exact = inRoom.filter { it.dateString == dateString }
    return (if (exact.isNotEmpty()) exact else inRoom.filter { it.dateString.isBlank() })
        .sortedBy { it.lessonNumber }
}

fun currentRoomLessonNumber(
    currentMinutes: Int,
    dayOfWeek: Int,
): Int? {
    val bells =
        com.politex.data.model.CollegeBellSchedule
            .getBellsForDay(dayOfWeek)
    return bells
        .firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
        ?.lessonNumber
        ?.takeIf { it > 0 }
}

@Composable
fun RoomScheduleDialog(
    roomNumber: String,
    dateTitle: String,
    lessons: List<LessonEntity>,
    currentLessonNumber: Int?,
    onTeacherClick: (String) -> Unit,
    onDismissRequest: () -> Unit,
    tomorrowSelected: Boolean = false,
    onDayChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier =
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        containerColor = ColorBgMain,
        confirmButton = {},
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "кабинет",
                            style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = roomNumber,
                            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextTitle),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("room_header_room"),
                        )
                    }
                    IconButton(
                        onClick = onDismissRequest,

                        modifier = Modifier.size(Tokens.HitTarget).testTag("room_close"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = ColorTextMuted,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dateTitle.isNotBlank()) {
                        Text(
                            text = dateTitle,

                            style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier =
                            Modifier
                                .weight(1f)
                                .testTag("room_header_date"),
                        )
                    }
                    DayToggle(
                        tomorrowSelected = tomorrowSelected,
                        onDayChange = onDayChange,
                    )
                }
            }
        },
        text = {
            if (lessons.isEmpty()) {
                RoomScheduleEmptyState(tomorrow = tomorrowSelected)
            } else {
                LazyColumn(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        lessons,
                        key = { "${it.groupName}_${it.lessonNumber}_${it.id}" },
                    ) { lesson ->
                        RoomLessonCard(
                            lesson = lesson,
                            isCurrent = lesson.lessonNumber == currentLessonNumber,
                            onTeacherClick = onTeacherClick,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RoomScheduleEmptyState(tomorrow: Boolean) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoomScheduleEmptyContent(tomorrow = tomorrow)
    }
}

@Composable
private fun DayToggle(
    tomorrowSelected: Boolean,
    onDayChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.testTag("room_day_toggle"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(false to "Сегодня", true to "Завтра").forEach { (isTomorrow, title) ->
            val selected = tomorrowSelected == isTomorrow
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (selected) ColorTopBar else ColorBgMain,
                border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
                modifier =
                Modifier
                    .heightIn(min = 32.dp)
                    .bouncyClickable(scaleDown = 0.97f) { onDayChange(isTomorrow) }
                    .testTag(if (isTomorrow) "room_day_tomorrow" else "room_day_today"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = title,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (selected) androidx.compose.ui.graphics.Color.White else ColorTextMuted,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomScheduleEmptyContent(tomorrow: Boolean) {
    EmptyState(
        icon = Icons.Default.EventBusy,
        title =
        if (tomorrow) {
            "Нет уроков в этом кабинете завтра"
        } else {
            "Нет уроков в этом кабинете сегодня"
        },
    )
}

@Composable
private fun RoomLessonCard(
    lesson: LessonEntity,
    isCurrent: Boolean,
    onTeacherClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),

        color = if (isCurrent) ColorActiveBlue.copy(alpha = 0.07f) else ColorBgMain,
        border = BorderStroke(if (isCurrent) 2.dp else 1.dp, if (isCurrent) ColorActiveBlue else ColorBorderLight),
        shadowElevation = 0.dp,
        modifier =
        modifier
            .fillMaxWidth()
            .testTag("room_lesson_card_${lesson.lessonNumber}")
            .then(
                if (isCurrent) {
                    Modifier.testTag("room_lesson_current_${lesson.lessonNumber}")
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = subjectDisplayName(lesson.subjectRaw),
                style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ColorTextTitle),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = lesson.timeRangeDisplay,
                style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lesson.groupName,
                    style = TextStyle(fontSize = 12.sp, color = ColorTextBody),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("room_lesson_group_${lesson.lessonNumber}"),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoomTeacherChip(name = lesson.teacherFirst, onClick = onTeacherClick)
                    RoomTeacherChip(name = lesson.teacherSecond, onClick = onTeacherClick)
                }
            }
        }
    }
}

@Composable
private fun RoomTeacherChip(
    name: String,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (name.isBlank()) return
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
        color = ColorSurfaceHighlight,
        modifier = modifier.bouncyClickable(scaleDown = 0.97f) { onClick(name) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 5.dp, end = 5.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Person,
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
            )
        }
    }
}
