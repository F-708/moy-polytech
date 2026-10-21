package com.politex.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.BellItem
import com.politex.data.model.BellScheduleType
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextDisabled
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable
import com.politex.util.Dow
import com.politex.util.TeacherModeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow

private val RAIL_BLUE: Color get() = ColorActiveBlue
private val RAIL_BLUE_SOFT: Color get() = ColorActiveBlue.copy(alpha = 0.30f)
private val RAIL_GRAY: Color get() = ColorTextMuted.copy(alpha = 0.55f)
private val RAIL_GRAY_SOFT: Color get() = ColorTextDisabled

private sealed interface BellRow {
    data class Lesson(
        val item: BellItem,
    ) : BellRow

    data class Break(
        val startMinutes: Int,
        val endMinutes: Int,
        val minutes: Int,
        val isBig: Boolean,
    ) : BellRow
}

private fun railColor(
    minutes: Int,
    blueEnd: Int?,
    isActive: Boolean,
): Color {
    if (!isActive) return RAIL_GRAY_SOFT
    return if (blueEnd != null && minutes <= blueEnd) RAIL_BLUE else RAIL_GRAY
}

fun bellsForDisplay(type: BellScheduleType): List<BellItem> = CollegeBellSchedule.getBellsForType(type)

fun sundayNoteVisible(
    todayDow: Int,
    isTodaySelected: Boolean,
): Boolean = todayDow == 7 && isTodaySelected

fun indicatorVisible(todayDow: Int): Boolean = todayDow != 7

private fun dotProgressIn(
    bell: BellItem,
    nowMinutes: Int,
    isActive: Boolean,
): Float? {
    if (!isActive) return null
    if (nowMinutes in bell.startMinutes..bell.endMinutes) {
        return (nowMinutes - bell.startMinutes).toFloat() /
            (bell.endMinutes - bell.startMinutes).coerceAtLeast(1)
    }
    return null
}

@Composable
fun BellsScreen(
    groupInfo: com.politex.data.model.GroupInfo,
    scheduleRepository: com.politex.data.repository.ScheduleRepository,
    scrollState: ScrollState = rememberScrollState(),
    scrollToCurrentRequested: Boolean = false,
    onScrollToCurrentConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val teacherModeRevision = TeacherModeStore.revision.intValue
    val isTeacherMode =
        remember(context, teacherModeRevision) {
            TeacherModeStore.isTeacher(context)
        }
    val teacherLastLesson by produceState(initialValue = null as Int?, key1 = isTeacherMode) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (TeacherModeStore.isTeacher(context)) {
                        com.politex.widget.TeacherWidgetData
                            .snapshot(context)
                            ?.lessons
                            ?.maxOfOrNull { it.lessonNumber }
                    } else {
                        null
                    }
                }.getOrNull()
            }
    }

    val now =
        com.politex.ui.util
            .rememberToday()

    val todayDow =
        remember(now) {
            Dow.of(now)
        }
    val todayLessons by scheduleRepository
        .getLessonsForDay(groupInfo.canonicalName, todayDow)
        .collectAsState(initial = emptyList<com.politex.data.local.entity.LessonEntity>())
    val lastLessonToday =
        remember(todayLessons, isTeacherMode, teacherLastLesson) {
            if (isTeacherMode) teacherLastLesson else todayLessons.maxOfOrNull { it.lessonNumber }
        }

    val todayType = remember(todayDow) { CollegeBellSchedule.getTypeForDay(todayDow) }

    var selectedScheduleType by rememberSaveable(todayDow) { mutableStateOf(todayType) }

    val currentTimeMinutes = com.politex.ui.util.rememberNowMinutes(30_000)

    val isTodaySelected = selectedScheduleType == todayType && todayDow != 7
    val activeSlot =
        remember(currentTimeMinutes, selectedScheduleType, isTodaySelected) {
            if (!isTodaySelected) {
                null
            } else {
                CollegeBellSchedule
                    .getBellsForType(selectedScheduleType)
                    .firstOrNull { currentTimeMinutes in it.startMinutes..it.endMinutes }
            }
        }

    val todayTypeSelected = selectedScheduleType == todayType
    val bells = bellsForDisplay(selectedScheduleType)
    val showSundayNote = sundayNoteVisible(todayDow, todayTypeSelected)

    val blueEndNum =
        if (isTodaySelected) {
            lastLessonToday
        } else {
            null
        }
    val blueEndMinutes =
        blueEndNum?.let { num ->
            bells.lastOrNull { !it.isInfoHour && it.lessonNumber <= num }?.endMinutes
        }

    val rows =
        remember(bells) {
            val b = bells
            val result = mutableListOf<BellRow>()
            b.forEach { item ->
                result.add(BellRow.Lesson(item))
                if (item.breakAfterMinutes > 0) {
                    result.add(
                        BellRow.Break(
                            startMinutes = item.endMinutes,
                            endMinutes = item.endMinutes + item.breakAfterMinutes,
                            minutes = item.breakAfterMinutes,
                            isBig = item.isBigBreak,
                        ),
                    )
                }
            }
            result
        }

    val density = androidx.compose.ui.platform.LocalDensity.current
    var viewportPx by remember { mutableStateOf(0) }

    var flashCurrent by remember { mutableStateOf(false) }
    LaunchedEffect(flashCurrent) {
        if (!flashCurrent) return@LaunchedEffect
        delay(1600)
        flashCurrent = false
    }
    LaunchedEffect(scrollToCurrentRequested, activeSlot, rows) {
        if (!scrollToCurrentRequested) return@LaunchedEffect

        snapshotFlow { viewportPx > 0 }.first { it }
        if (scrollState.maxValue == 0) {
            onScrollToCurrentConsumed()
            return@LaunchedEffect
        }

        androidx.compose.runtime
            .snapshotFlow { scrollState.maxValue > 0 }
            .first { it }
        val target =
            activeSlot
                ?: rows.lastOrNull { it is BellRow.Lesson }?.let { (it as BellRow.Lesson).item }
        val idx = rows.indexOfFirst { (it as? BellRow.Lesson)?.item == target }
        if (idx >= 0) {
            with(density) {

                var posPx = 0f
                rows.take(idx).forEach { row ->
                    posPx += (if (row is BellRow.Lesson) 56.dp else 26.dp).toPx() + 6.dp.toPx()
                }
                val cardH = 56.dp.toPx()

                val scrollTo = (posPx + cardH / 2 - viewportPx / 2).toInt()
                scrollState.animateScrollTo(scrollTo.coerceIn(0, scrollState.maxValue))
            }
            flashCurrent = true
        }
        onScrollToCurrentConsumed()
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

            Text(
                text = "Расписание звонков",
                style = TextStylePageTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BellScheduleType.entries.forEach { type ->
                val isSelected = selectedScheduleType == type
                val isToday = type == todayType
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        modifier =
                        Modifier
                            .fillMaxWidth()

                            .heightIn(min = 44.dp)
                            .bouncyClickable { selectedScheduleType = type }
                            .testTag("bells_type_${type.name.lowercase()}"),
                    ) {

                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text =
                                when (type) {
                                    BellScheduleType.STANDARD -> "ПН - ПТ"
                                    BellScheduleType.THURSDAY -> "ЧЕТВЕРГ"
                                    BellScheduleType.SATURDAY -> "СУББОТА"
                                },
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color.White else ColorTextBody,
                                ),
                                modifier =
                                Modifier
                                    .padding(vertical = 8.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally),
                            )
                        }
                    }
                    Box(
                        modifier =
                        Modifier
                            .padding(top = 3.dp)
                            .width(28.dp)
                            .height(3.dp)
                            .background(
                                if (isToday && indicatorVisible(todayDow)) ColorActiveBlue else Color.Transparent,
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
        }

        AppDivider(modifier = Modifier.fillMaxWidth().padding(top = 5.dp))

        if (isTodaySelected && bells.isNotEmpty()) {
            val breakIdx =
                bells.indexOfFirst { bell ->
                    val breakEnd = bell.endMinutes + bell.breakAfterMinutes
                    bell.breakAfterMinutes > 0 &&
                        currentTimeMinutes > bell.endMinutes &&
                        currentTimeMinutes < breakEnd
                }
            val context: NowContext =
                when {
                    activeSlot != null -> NowContext.Active(activeSlot)
                    currentTimeMinutes < bells.first().startMinutes ->
                        NowContext.Inactive("Уроки ещё не начались", "первый звонок в ${bells.first().start}")
                    breakIdx >= 0 -> {
                        val nextStart = bells.getOrNull(breakIdx + 1)?.start ?: bells[breakIdx].end
                        NowContext.Inactive("СЕЙЧАС: ПЕРЕМЕНА", "до $nextStart")
                    }
                    else -> NowContext.Inactive("Уроки закончились", "последний звонок в ${bells.last().end}")
                }
            NowContextCard(context = context)
        }

        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { viewportPx = it.height }
                .verticalScroll(scrollState)
                .padding(start = 16.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showSundayNote) {

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorSurfaceVariantLight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Сегодня воскресенье — уроков нет",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted,
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            rows.forEach { row ->
                when (row) {
                    is BellRow.Lesson ->
                        LessonBellCard(
                            item = row.item,
                            isActive = activeSlot == row.item,
                            highlight = flashCurrent && activeSlot == row.item,
                            railTopColor = railColor(row.item.startMinutes, blueEndMinutes, isTodaySelected),
                            railBottomColor = railColor(row.item.endMinutes, blueEndMinutes, isTodaySelected),
                            inBlue = row.item.startMinutes < (blueEndMinutes ?: -1),
                            dotProgress = dotProgressIn(row.item, currentTimeMinutes, isTodaySelected),
                        )
                    is BellRow.Break -> BreakRow(row = row)
                }
            }
        }
    }
}

private sealed interface NowContext {
    data class Active(
        val slot: BellItem,
    ) : NowContext

    data class Inactive(
        val title: String,
        val subtitle: String,
    ) : NowContext
}

@Composable
private fun NowContextCard(context: NowContext) {
    val isActive = context is NowContext.Active
    val slot = (context as? NowContext.Active)?.slot
    val title =
        when (context) {
            is NowContext.Active ->
                if (slot!!.isInfoHour) "СЕЙЧАС: ИНФОРМАЦИОННЫЙ ЧАС" else "СЕЙЧАС: ${slot.title.uppercase()}"
            is NowContext.Inactive -> context.title
        }
    val subtitle =
        when (context) {
            is NowContext.Active -> slot!!.displayRange
            is NowContext.Inactive -> context.subtitle
        }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceHighlight,
        border = BorderStroke(1.dp, if (isActive) ColorActiveBlue else ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                Modifier
                    .size(30.dp)
                    .background(if (isActive) ColorActiveBlue else ColorBorderLight, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                    when {
                        slot?.isInfoHour == true -> Icons.Default.Campaign
                        isActive -> Icons.Default.NotificationsActive
                        else -> Icons.Default.AccessTime
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isActive) ColorBrandBlue else ColorTextBody,
                    ),
                )
                Text(
                    text = subtitle,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorTextBody,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LessonBellCard(
    item: BellItem,
    isActive: Boolean,
    railTopColor: Color,
    railBottomColor: Color,
    inBlue: Boolean,
    dotProgress: Float?,
    highlight: Boolean = false,
) {

    val flashAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (highlight) 0.16f else 0f,
        animationSpec = tween(durationMillis = if (highlight) 150 else 900),
        label = "BellFlash",
    )
    val tickColor = if (inBlue) RAIL_BLUE else RAIL_GRAY_SOFT

    val pulseRadius by
        if (dotProgress != null) {
            rememberInfiniteTransition(label = "DotPulse").animateFloat(
                initialValue = 4.5f,
                targetValue = 7f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
                label = "DotPulseRadius",
            )
        } else {
            remember { mutableStateOf(0f) }
        }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border =
        BorderStroke(
            1.dp,
            when {
                item.isInfoHour -> ColorSuccess
                isActive -> ColorActiveBlue
                else -> ColorBorderLight
            },
        ),
        shadowElevation = 0.dp,
        modifier =
        Modifier
            .fillMaxWidth()
            .drawBehind {

                if (flashAlpha > 0.005f) {
                    drawRect(
                        color = ColorActiveBlue.copy(alpha = flashAlpha),
                        size = size,
                    )
                }

                val cx = size.width + 10.dp.toPx()
                val tickLen = 5.dp.toPx()

                drawLine(
                    color = railTopColor,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = railBottomColor,
                    start = Offset(cx, size.height / 2f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                drawLine(
                    color = tickColor,
                    start = Offset(cx - tickLen, 0f),
                    end = Offset(cx + tickLen, 0f),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawLine(
                    color = tickColor,
                    start = Offset(cx - tickLen, size.height),
                    end = Offset(cx + tickLen, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )

                if (dotProgress != null) {
                    val inset = 16.dp.toPx()
                    val travel = (size.height - inset * 2).coerceAtLeast(1f)
                    val y = inset + travel * dotProgress
                    drawCircle(
                        color = RAIL_BLUE_SOFT,
                        radius = pulseRadius.dp.toPx() + 4.dp.toPx(),
                        center = Offset(cx, y),
                    )
                    drawCircle(
                        color = RAIL_BLUE,
                        radius = 4.5.dp.toPx(),
                        center = Offset(cx, y),
                    )
                }
            },
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color =
                    when {
                        item.isInfoHour -> ColorSuccess
                        isActive -> ColorActiveBlue
                        else -> ColorBrandBlue
                    },
                    contentColor = Color.White,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        if (item.isInfoHour) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = if (item.isInfoHour) "ИНФОЧАС" else "${item.lessonNumber} УРОК",
                            softWrap = false,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }

            AppCard(
                modifier = Modifier.wrapContentWidth(),
                color = ColorSurfaceVariantLight,
            ) {
                Text(
                    text = item.displayRange,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorBrandBlue,
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BreakRow(row: BellRow.Break) {
    val range = "${minutesToTime(row.startMinutes)} – ${minutesToTime(row.endMinutes)}"
    Row(
        modifier =
        Modifier
            .fillMaxWidth()

            .drawBehind {
                val cx = size.width + 10.dp.toPx()
                drawLine(
                    color = RAIL_GRAY_SOFT,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (row.isBig) "перемена ${row.minutes} мин (большая)" else "перемена ${row.minutes} мин",
            style =
            androidx.compose.ui.text.TextStyle(
                fontSize = 11.sp,
                fontWeight = if (row.isBig) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.isBig) ColorBrandBlue else ColorTextMuted,
            ),
        )
        Text(
            text = range,
            style =
            androidx.compose.ui.text.TextStyle(
                fontSize = 10.sp,
                color = ColorTextMuted,
            ),
        )
    }
}

private fun minutesToTime(minutes: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
