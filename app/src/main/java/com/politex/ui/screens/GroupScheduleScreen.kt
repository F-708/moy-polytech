package com.politex.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.checkingStatusFor
import com.politex.data.repository.emptyStateText
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.components.CenteredLoader
import com.politex.ui.components.DaySlotCell
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.theme.TextStyleSubPageTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberNowMinutes
import com.politex.ui.util.rememberToday
import com.politex.util.CuratorStore
import com.politex.util.Dow
import com.politex.util.GroupCatalog
import com.politex.util.GroupParser
import com.politex.util.HintFlags
import com.politex.util.MpkCurriculum
import kotlinx.coroutines.launch
import java.util.Calendar

internal fun groupScheduleDayOfWeek(cal: Calendar): Int = Dow.of(cal)

internal fun groupScheduleIsSchoolDay(
    dayOfWeek: Int,
    saturdayIsSchoolDay: Boolean,
): Boolean = dayOfWeek in 1..5 || (dayOfWeek == 6 && saturdayIsSchoolDay)

internal fun groupScheduleNextSchoolDay(
    base: Calendar,
    saturdayIsSchoolDay: Boolean,
): Calendar {
    val cal = (base.clone() as Calendar)
    do {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    } while (!groupScheduleIsSchoolDay(groupScheduleDayOfWeek(cal), saturdayIsSchoolDay))
    return cal
}

internal fun groupScheduleDbDate(cal: Calendar): String = com.politex.util.DateFormats.dotDate(cal)

internal fun pickForeignGroupLessons(
    rows: List<LessonEntity>,
    dateString: String,
): List<LessonEntity> {
    val exact = rows.filter { it.dateString == dateString }
    if (exact.isNotEmpty()) return exact
    return rows.filter { it.dateString.isBlank() }
}

internal fun needsFetch(
    hasCachedLessons: Boolean,
    userForced: Boolean,
): Boolean = userForced || !hasCachedLessons

internal fun buildGroupCode(
    course: Int?,
    number: Int?,
    letter: Char?,
): String? {
    if (course !in 1..4 || number !in 1..9) return null
    if (letter == null || !letter.isLetter()) return null
    return "$course$number${GroupParser.normalizeSpecialtyChar(letter)}"
}

internal fun validateAgainstCatalog(
    code: String,
    catalog: List<String>,
): Boolean {
    val normalized = GroupCatalog.normalize(code) ?: return false
    return catalog.any { GroupCatalog.normalize(it) == normalized }
}

internal fun groupPickerMaxNumber(
    catalog: List<String>,
    course: Int?,
): Int {
    if (course == null) return 9
    return catalog
        .mapNotNull { GroupCatalog.normalize(it) }
        .filter { it[0] == course.digitToChar() }
        .maxOfOrNull { it[1] - '0' } ?: 9
}

internal fun groupPickerLetters(
    catalog: List<String>,
    course: Int?,
    number: Int?,
): List<Char> {
    val normalized = catalog.mapNotNull { GroupCatalog.normalize(it) }
    if (normalized.isEmpty()) {
        return GroupParser.generateCommonGroups().map { it.specialtyCode }.distinct().sorted()
    }
    val prefix = if (course != null && number != null) "$course$number" else null
    return normalized
        .filter { prefix == null || it.startsWith(prefix) }
        .map { it[2] }
        .distinct()
        .sorted()
}

@Composable
fun GroupScheduleRoute(
    group: String,
    groupInfo: com.politex.data.model.GroupInfo,
    scheduleRepository: ScheduleRepository,
    onGroupPicked: (String) -> Unit,
    onChangeGroup: () -> Unit,
    onTeacherClick: (String) -> Unit,
    onRoomClick: (room: String, fromRoom: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupScheduleScreen(
        scheduleRepository = scheduleRepository,
        onBack = onChangeGroup,
        selectedGroupName = group,
        onGroupSelected = onGroupPicked,
        onChangeGroup = onChangeGroup,
        ownGroupName = groupInfo.canonicalName,
        onTeacherClick = onTeacherClick,
        onRoomClick = onRoomClick,
        modifier = modifier,
    )
}

@Composable
fun GroupScheduleScreen(
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
    selectedGroupName: String = "",
    onGroupSelected: (String) -> Unit = {},
    onChangeGroup: () -> Unit = {},
    ownGroupName: String = "",
    onTeacherClick: (String) -> Unit = {},
    onRoomClick: (room: String, fromRoom: String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    CuratorStore.attach(context)
    val curatorRevision = CuratorStore.revision.intValue
    val curatorName =
        remember(selectedGroupName, curatorRevision) {
            CuratorStore.curatorFor(selectedGroupName)
        }

    var selectedCourse by rememberSaveable {
        mutableIntStateOf(GroupParser.parse(ownGroupName)?.course ?: 1)
    }
    var selectedSlot by rememberSaveable(selectedGroupName) { mutableIntStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }

    var syncError by remember(selectedGroupName) { mutableStateOf<String?>(null) }
    var syncEmptyNotice by remember(selectedGroupName) { mutableStateOf<String?>(null) }

    var lastOutcome by remember(selectedGroupName) { mutableStateOf<SyncOutcome?>(null) }

    val foreignInfo = remember(selectedGroupName) { GroupParser.parse(selectedGroupName) }

    val isTeacherKnown: (String) -> Boolean = com.politex.ui.util.rememberIsTeacherKnown()

    val realNow = rememberToday()

    var scheduleHintDone by remember { mutableStateOf(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS)) }
    fun markScheduleHintDone() {
        if (!scheduleHintDone) {
            HintFlags.markDone(context, HintFlags.KEY_SCHEDULE_TAPS)
            scheduleHintDone = true
        }
    }

    val foreignLessonsFlow =
        remember(selectedGroupName) {
            scheduleRepository.getAllLessonsForGroup(selectedGroupName)
        }
    val allForeignLessons: List<LessonEntity>? by foreignLessonsFlow.collectAsState(initial = null)
    val saturdayIsSchoolDay =
        foreignInfo?.hasSaturdayClasses == true ||
            allForeignLessons?.any { it.dayOfWeek == 6 } == true

    val daySlots =
        remember(selectedGroupName, saturdayIsSchoolDay, realNow, allForeignLessons) {
            com.politex.ui.util.daySlots(realNow, saturdayIsSchoolDay) { cal ->
                val ds = groupScheduleDbDate(cal)
                val dow = groupScheduleDayOfWeek(cal)
                allForeignLessons.orEmpty().any { it.dateString == ds || (it.dateString.isBlank() && it.dayOfWeek == dow) }
            }
        }
    val leftCal = daySlots.first
    val rightCal = daySlots.second

    val tomorrow = remember(realNow) { (realNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) } }

    fun sameDay(
        a: Calendar,
        b: Calendar,
    ): Boolean = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    val leftIsToday = sameDay(leftCal, realNow)
    val rightIsTomorrow = sameDay(rightCal, tomorrow)

    val activeCal = if (selectedSlot == 0) leftCal else rightCal
    val activeDayOfWeek = groupScheduleDayOfWeek(activeCal)
    val activeDateString = groupScheduleDbDate(activeCal)

    fun syncForeignSchedule(force: Boolean) {
        if (selectedGroupName.isBlank() || isSyncing) return
        val group = selectedGroupName
        isSyncing = true
        syncError = null
        syncEmptyNotice = null
        scope.launch {

            val result = scheduleRepository.syncScheduleDetailed(
                group,
                force = force,
                targetDate = if (force) activeDateString else null,
            )
            isSyncing = false
            result.fold(
                onSuccess = { outcome ->
                    lastOutcome = outcome
                    syncEmptyNotice = when (outcome) {
                        is SyncOutcome.FoundNoGroup ->
                            "Группы $group нет в опубликованном расписании на ${outcome.date}"
                        is SyncOutcome.NotFound ->
                            "Расписание на ${outcome.date} ещё не опубликовано"
                        is SyncOutcome.Ok -> null
                    }
                },
                onFailure = { syncError = "Не удалось загрузить расписание: проверьте интернет" },
            )
        }
    }

    var autoCheckDone by remember(selectedGroupName) { mutableStateOf(false) }
    LaunchedEffect(selectedGroupName, allForeignLessons) {
        if (selectedGroupName.isBlank() || autoCheckDone) return@LaunchedEffect
        val rows = allForeignLessons ?: return@LaunchedEffect
        autoCheckDone = true
        val hasCached = listOf(leftCal, rightCal).any { cal ->
            lessonsForDay(rows, groupScheduleDbDate(cal), groupScheduleDayOfWeek(cal)).isNotEmpty()
        }
        if (needsFetch(hasCachedLessons = hasCached, userForced = false)) syncForeignSchedule(force = false)
    }

    val nowMinutes = rememberNowMinutes()
    val currentLessonNumber =
        remember(nowMinutes, activeDayOfWeek, selectedSlot, leftIsToday) {
            if (selectedSlot == 0 && leftIsToday) {
                CollegeBellSchedule
                    .getBellsForDay(activeDayOfWeek)
                    .firstOrNull { nowMinutes in it.startMinutes..it.endMinutes }
                    ?.lessonNumber
                    ?.takeIf { it > 0 }
            } else {
                null
            }
        }

    val isInitialLoad = allForeignLessons == null
    val lessons =
        remember(allForeignLessons, activeDateString, activeDayOfWeek) {
            lessonsForDay(allForeignLessons ?: emptyList(), activeDateString, activeDayOfWeek)
        }

    val pulseLessonNumber =
        remember(lessons) {
            lessons.firstOrNull { it.roomFirst.isNotBlank() || it.teacherFirst.isNotBlank() }?.lessonNumber
        }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {

        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppCard(
                modifier = Modifier
                    .size(Tokens.IconHit)
                    .bouncyClickable(onClick = onBack),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Расписание группы",
                    style = TextStyleSubPageTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                    if (selectedGroupName.isBlank()) {
                        "Найдите расписание любой группы колледжа"
                    } else {
                        "Сегодня и завтра • дата $activeDateString"
                    },
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ColorBrandBlue,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectedGroupName.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier =
                    Modifier
                        .size(Tokens.HitTarget)
                        .bouncyClickable { syncForeignSchedule(force = true) }
                        .testTag("group_schedule_sync_button"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить расписание группы",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        AppDivider()

        if (selectedGroupName.isBlank()) {

            val catalogCodes = remember(context) { GroupCatalog.load(context).all() }
            GroupSchedulePicker(
                selectedCourse = selectedCourse,
                onCourseSelected = { selectedCourse = it },
                catalogCodes = catalogCodes,
                onGroupSelected = onGroupSelected,
            )
        } else {

            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorTopBar,
                border = BorderStroke(1.dp, ColorBorderLight),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("group_schedule_caption"),
            ) {
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ГРУППА: $selectedGroupName",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val spec = foreignInfo?.let { MpkCurriculum.getSpecialty(it.specialtyCode) }
                        val specText = spec?.shortName.orEmpty()
                        if (specText.isNotBlank()) {
                            Text(
                                text = specText,
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (!curatorName.isNullOrBlank()) {
                            Text(
                                text = "Куратор: $curatorName",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("group_schedule_curator_line"),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier =
                        Modifier
                            .heightIn(min = Tokens.ButtonHeight)
                            .bouncyClickable { onChangeGroup() }
                            .testTag("group_schedule_change_button"),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = "СМЕНИТЬ",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = Tokens.ButtonText,
                                    color = Color.White,
                                ),
                            )
                        }
                    }
                }
            }

            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DaySlotCell(
                    title = if (leftIsToday) "СЕГОДНЯ" else dayTitle(leftCal),
                    subtitle =
                    String.format(
                        java.util.Locale.ROOT,
                        "%02d.%02d",
                        leftCal.get(Calendar.DAY_OF_MONTH),
                        leftCal.get(Calendar.MONTH) + 1,
                    ),
                    selected = selectedSlot == 0,
                    onClick = { selectedSlot = 0 },
                    modifier = Modifier.weight(1f),
                )
                DaySlotCell(
                    title = if (rightIsTomorrow) "ЗАВТРА" else dayTitle(rightCal),
                    subtitle =
                    String.format(
                        java.util.Locale.ROOT,
                        "%02d.%02d",
                        rightCal.get(Calendar.DAY_OF_MONTH),
                        rightCal.get(Calendar.MONTH) + 1,
                    ),
                    selected = selectedSlot == 1,
                    onClick = { selectedSlot = 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            AppDivider(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            if (syncError != null && lessons.isNotEmpty()) {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    color = ColorSurfaceVariantLight,
                ) {
                    Text(
                        text = syncError ?: "",
                        style =
                        androidx.compose.ui.text
                            .TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (isInitialLoad) {
                CenteredLoader(modifier = Modifier.fillMaxWidth().weight(1f))
            } else if (lessons.isEmpty()) {

                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(ColorSurfaceHighlight),
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

                        val noticeForActiveDate = syncEmptyNotice?.takeIf { lastOutcome?.date == activeDateString }
                        Text(
                            text =
                            when {

                                isSyncing -> checkingStatusFor(activeDateString)
                                syncError != null -> syncError ?: ""
                                noticeForActiveDate != null -> noticeForActiveDate
                                else -> emptyStateText(activeDateString, lastOutcome?.date ?: "", lastOutcome)
                            },
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle,
                            ),
                            textAlign = TextAlign.Center,
                        )
                        if (!isSyncing && syncError == null && noticeForActiveDate == null && lastOutcome == null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Расписание группы $selectedGroupName появится после синхронизации с сайтом guo-mpk.by",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorTextMuted,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (!isSyncing) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                border = BorderStroke(1.dp, ColorBrandBlue),
                                color = ColorBrandFill,
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Tokens.ButtonHeight)
                                    .bouncyClickable { syncForeignSchedule(force = true) }
                                    .testTag("group_schedule_retry_button"),
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
                                        text = "ОБНОВИТЬ",
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {

                GroupScheduleLessonList(
                    lessons = lessons,
                    currentLessonNumber = currentLessonNumber,
                    pulseLessonNumber = pulseLessonNumber,
                    showPulseHint = !scheduleHintDone,
                    isTeacherKnown = isTeacherKnown,
                    onTeacherClick = { name ->
                        markScheduleHintDone()
                        onTeacherClick(name)
                    },
                    onRoomClick = { room, fromRoom ->
                        markScheduleHintDone()
                        onRoomClick(room, fromRoom)
                    },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

private val groupDayNames = listOf("", "ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ")

@Composable
internal fun GroupScheduleLessonList(
    lessons: List<LessonEntity>,
    currentLessonNumber: Int?,
    pulseLessonNumber: Int?,
    showPulseHint: Boolean,
    isTeacherKnown: (String) -> Boolean,
    onTeacherClick: (String) -> Unit,
    onRoomClick: (room: String, fromRoom: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots =
        remember(lessons) {
            mergedLessonSlots(lessons, teacherMode = false)
        }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(slots, key = { it.key }) { slot ->
            ScheduleLessonSlotCard(
                slot = slot,
                currentLessonNumber = currentLessonNumber,
                teacherMode = false,
                pulseLessonNumber = pulseLessonNumber,
                showPulseHint = showPulseHint,
                onTeacherClick = onTeacherClick,
                isTeacherKnown = isTeacherKnown,
                onRoomClick = { room, lesson ->
                    onRoomClick(
                        room,
                        com.politex.ui.util.RouteStartResolver
                            .startForLesson(lessons, lesson),
                    )
                },
            )
        }
    }
}

private fun dayTitle(cal: Calendar): String = groupDayNames[groupScheduleDayOfWeek(cal)]

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupSchedulePicker(
    selectedCourse: Int,
    onCourseSelected: (Int) -> Unit,
    catalogCodes: List<String>,
    confirmLabel: String = "ОТКРЫТЬ",
    onGroupSelected: (String) -> Unit,
    note: String? = null,
) {
    var selectedNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedLetter by rememberSaveable { mutableStateOf<Char?>(null) }

    val maxNumber =
        remember(catalogCodes, selectedCourse) { groupPickerMaxNumber(catalogCodes, selectedCourse) }
    val number = selectedNumber?.takeIf { it in 1..maxNumber }

    val letters =
        remember(catalogCodes, selectedCourse, number) {
            groupPickerLetters(catalogCodes, selectedCourse, number)
        }
    val letter = selectedLetter?.takeIf { it in letters }

    val code = buildGroupCode(selectedCourse, number, letter)
    val inCatalog = code != null && validateAgainstCatalog(code, catalogCodes)
    val info = remember(code) { code?.let { GroupParser.parse(it) } }
    val specialtyShort = remember(info) { info?.let { MpkCurriculum.getSpecialty(it.specialtyCode)?.shortName }.orEmpty() }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = code ?: "• • •",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 44.sp,
                    ),
                    color = if (inCatalog) ColorSuccess else ColorTextTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("group_picker_code"),
                )
                if (inCatalog) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Группа есть в каталоге",
                        tint = ColorSuccess,
                        modifier =
                        Modifier
                            .size(28.dp)
                            .testTag("group_picker_valid_icon"),
                    )
                }
            }
            Text(
                text =
                when {
                    code == null -> "Выберите курс, номер и группу"
                    inCatalog -> "Группа есть в каталоге"
                    else -> "Группы $code нет в каталоге"
                },
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = if (inCatalog) ColorSuccess else ColorTextMuted,
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (note != null) {
                Text(
                    text = note,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorTextMuted,
                    ),
                    textAlign = TextAlign.Center,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .testTag("group_picker_note"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            PickerSectionLabel("1 · КУРС")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (1..4).forEach { course ->
                    PickerChip(
                        label = "$course",
                        selected = selectedCourse == course,
                        modifier = Modifier.weight(1f),
                        tag = "group_schedule_course_$course",
                        onClick = { onCourseSelected(course) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            PickerSectionLabel("2 · НОМЕР")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (1..maxNumber).forEach { n ->
                    PickerChip(
                        label = "$n",
                        selected = number == n,
                        modifier = Modifier.weight(1f),
                        tag = "group_picker_number_$n",
                        onClick = { selectedNumber = if (number == n) null else n },
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            PickerSectionLabel("3 · БУКВА")
            if (letters.isNotEmpty()) {
                FlowRow(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("group_picker_letters_row"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    letters.forEach { ch ->
                        PickerChip(
                            label = "$ch",
                            selected = letter == ch,
                            tag = "group_picker_letter_$ch",
                            onClick = { selectedLetter = if (letter == ch) null else ch },
                        )
                    }
                }
            } else if (number != null) {
                Text(
                    text = "Группы $selectedCourse$number нет в расписании",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = ColorTextMuted,
                    ),
                    modifier = Modifier.testTag("group_picker_no_groups"),
                )
            }

            if (info != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text =
                    listOfNotNull(
                        code,
                        "${info.course} курс",
                        specialtyShort.ifBlank { null },
                    ).joinToString(" · "),
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ColorTextBody,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag("group_picker_preview"),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = if (inCatalog) ColorBrandFill else ColorSurfaceVariantLight,
                border = BorderStroke(1.dp, if (inCatalog) ColorBrandBlue else ColorBorderLight),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.ButtonHeight)
                    .bouncyClickable(enabled = inCatalog) { code?.let(onGroupSelected) }
                    .testTag("group_picker_open_button"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Text(
                        text = confirmLabel,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (inCatalog) Color.White else ColorTextMuted,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text = text,
        style =
        androidx.compose.ui.text.TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = Tokens.SectionHeader,
            color = ColorTextMuted,
        ),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun PickerChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    tag: String = "",
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        modifier =
        modifier
            .heightIn(min = Tokens.HitTarget)
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
            Modifier
                .defaultMinSize(minWidth = Tokens.HitTarget)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 16.sp,
                    color = if (selected) Color.White else ColorTextBody,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
