package com.politex.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.Student
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.components.BackButton
import com.politex.ui.components.SearchEmptyState
import com.politex.ui.components.SubPageHeader
import com.politex.ui.theme.ColorAccentGold
import com.politex.ui.theme.ColorAccentGoldBg
import com.politex.ui.theme.ColorAccentGoldBright
import com.politex.ui.theme.ColorAccentGoldMuted
import com.politex.ui.theme.ColorAccentGoldSoft
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorErrorText
import com.politex.ui.theme.ColorOnAccentGold
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.TextStyleSubPageTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberNowMinutes
import com.politex.ui.util.rememberToday
import com.politex.util.CuratorStore
import com.politex.util.Dow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun StudentsDatabaseScreen(
    onBack: () -> Unit,
    onOpenStudent: (Student) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val students by produceState(initialValue = emptyList<Student>(), key1 = context) {
        value = withContext(Dispatchers.IO) { com.politex.AppGraph.studentsRepository.loadStudents() }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var groupFilter by rememberSaveable { mutableStateOf("") }
    var courseFilter by rememberSaveable { mutableStateOf("") }

    val groups = remember(students) { students.map { it.group }.distinct().sorted() }
    val courses = remember(students) { students.map { it.course }.distinct().sorted() }

    val filtered = remember(students, searchQuery, groupFilter, courseFilter) {
        val q = searchQuery.trim().lowercase()
        students.filter { s ->
            (
                q.isBlank() ||
                    s.fullName.lowercase().contains(q) ||
                    s.curator.lowercase().contains(q)
                ) &&
                (groupFilter.isBlank() || s.group == groupFilter) &&
                (courseFilter.isBlank() || s.course == courseFilter)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        tint = ColorAccentGold,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "База данных учащихся",
                        style = TextStyleSubPageTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "Найдено: ${filtered.size} из ${students.size}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = ColorAccentGold,
                    ),
                )
            }
        }

        AppDivider()

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ФИО или куратор...") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = ColorAccentGold, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(2.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ColorSurfaceVariantLight,
                unfocusedContainerColor = ColorSurfaceVariantLight,
                focusedIndicatorColor = ColorAccentGold,
                unfocusedIndicatorColor = ColorBorderLight,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterDropdown(
                label = if (groupFilter.isBlank()) "Группа" else groupFilter,
                options = groups,
                selected = groupFilter,
                onSelect = { groupFilter = it },
                modifier = Modifier.weight(1f),
            )
            FilterDropdown(
                label = if (courseFilter.isBlank()) "Курс" else courseFilter,
                options = courses,
                selected = courseFilter,
                onSelect = { courseFilter = it },
                modifier = Modifier.weight(1f),
            )
        }

        if (filtered.isEmpty() && (searchQuery.isNotBlank() || groupFilter.isNotBlank() || courseFilter.isNotBlank())) {
            SearchEmptyState(
                query = searchQuery,
                modifier = Modifier.fillMaxSize(),
                onReset = {
                    searchQuery = ""
                    groupFilter = ""
                    courseFilter = ""
                },
                resetLabel = "Сбросить фильтры",
                resetTestTag = "students_reset_filters_btn",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered) { student ->
                    StudentRow(student) { onOpenStudent(student) }
                }
            }
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, if (selected.isNotBlank()) ColorAccentGold else ColorBorderLight),
            color = ColorBgMain,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable { expanded = true },
        ) {
            Text(
                text = label,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = ColorTextBody,
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Все") },
                onClick = {
                    onSelect("")
                    expanded = false
                },
            )
            options.forEach { opt ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun StudentRow(student: Student, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()

            .heightIn(min = 48.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(ColorAccentGoldBg, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = student.group,
                    color = ColorAccentGoldBright,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.fullName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ColorTextTitle,
                    ),
                )
                Text(
                    text = listOf(student.course + " курс", student.funding).filter { it.isNotBlank() }.joinToString(" • "),
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorTextMuted,
                    ),
                )
            }
            Icon(
                imageVector = Icons.Default.PersonSearch,
                contentDescription = null,
                tint = ColorAccentGold,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun StudentCardScreen(
    student: Student,
    scheduleRepository: ScheduleRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    CuratorStore.attach(context)
    val curatorRevision = CuratorStore.revision.intValue
    val curatorName = remember(student.group, student.curator, curatorRevision) {
        resolveCardCurator(student.group, student.curator)
    }

    val today = rememberToday()
    val dow = remember(today) { Dow.of(today) }
    val minutes = rememberNowMinutes()

    val allGroupLessons by scheduleRepository
        .getAllLessonsForGroup(student.group)
        .collectAsState(initial = emptyList())

    var dayMode by remember(student.group) { mutableStateOf(DayMode.TODAY) }
    var calendarDate by remember(student.group) { mutableStateOf<String?>(null) }

    val tomorrowDow = if (dow >= 6) 1 else dow + 1

    val todayLessons by scheduleRepository
        .getLessonsForDay(student.group, dow)
        .collectAsState(initial = emptyList())
    val tomorrowLessons by scheduleRepository
        .getLessonsForDay(student.group, tomorrowDow)
        .collectAsState(initial = emptyList())
    val calendarFlow = remember(student.group, calendarDate) {
        calendarDate?.let { scheduleRepository.getLessonsForDate(student.group, it) }
            ?: flowOf(emptyList())
    }
    val calendarLessons by calendarFlow.collectAsState(initial = emptyList())
    val lessons = when (dayMode) {
        DayMode.TODAY -> todayLessons
        DayMode.TOMORROW -> tomorrowLessons
        DayMode.CALENDAR -> calendarLessons
    }

    val availableDates by scheduleRepository
        .getDistinctDates(student.group)
        .collectAsState(initial = emptyList())

    var isLoading by remember(student.group) { mutableStateOf(false) }
    var loadMessage by remember(student.group) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun loadSchedule(force: Boolean = true) {
        if (isLoading) return
        isLoading = true
        loadMessage = ""
        scope.launch {
            val result = scheduleRepository.syncScheduleFromWeb(student.group, force = force)
            loadMessage = result.fold(
                onSuccess = { count ->
                    if (count > 0) {
                        "Загружено уроков: $count"
                    } else {
                        "Группы ${student.group} нет в опубликованном расписании на эту дату"
                    }
                },
                onFailure = { "Не удалось загрузить (проверьте интернет)" },
            )
            isLoading = false
        }
    }

    LaunchedEffect(student.group) {
        if (!scheduleRepository.hasSchedule(student.group)) {
            loadSchedule(force = false)
        }
    }

    val teachers by produceState(initialValue = emptyList<Teacher>(), key1 = context) {
        value = withContext(Dispatchers.IO) { com.politex.AppGraph.teachersRepository.loadTeachers() }
    }
    val insights = remember(teachers, allGroupLessons) {
        TeacherInsights(teachers, allGroupLessons)
    }

    val currentLesson = remember(todayLessons, minutes) {
        val bells = CollegeBellSchedule.getBellsForDay(dow)
        val currentNum = bells.firstOrNull { minutes in it.startMinutes..it.endMinutes }?.lessonNumber
        if (currentNum != null && currentNum > 0) {
            todayLessons.firstOrNull { it.lessonNumber == currentNum }
        } else {
            null
        }
    }
    val nextLesson = remember(todayLessons, minutes, currentLesson) {
        todayLessons.filter { it.lessonNumber > (currentLesson?.lessonNumber ?: 0) }
            .minByOrNull { it.lessonNumber }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        SubPageHeader(
            title = student.fullName,
            subtitle = listOf(
                "Группа ${student.group}",
                if (student.course.isNotBlank()) "${student.course} курс" else "",
            ).filter { it.isNotBlank() }.joinToString(" • "),
            accent = ColorAccentGold,
            titleMaxLines = 2,
            onBack = onBack,
            actions = {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorAccentGold),
                    color = ColorBgMain,
                    modifier = Modifier
                        .size(Tokens.IconHit)
                        .bouncyClickable { loadSchedule() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = ColorAccentGold,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить расписание группы",
                                tint = ColorAccentGold,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            },
        )

        if (loadMessage.isNotBlank()) {
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = ColorSurfaceVariantLight,
            ) {
                Text(
                    text = loadMessage,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        InfoLine("Специальность", student.specialty)
                        if (student.specialtyCode.isNotBlank()) {
                            InfoLine("Код специальности", student.specialtyCode)
                        }
                        if (student.funding.isNotBlank()) InfoLine("Основа обучения", student.funding)
                        if (student.dormitory.isNotBlank()) InfoLine("Общежитие", student.dormitory)
                        if (!curatorName.isNullOrBlank()) {
                            InfoLine("Куратор", curatorName)
                        }

                        if (student.courseMismatch) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "В базе указан ${student.course} курс, но у этой специальности " +
                                    "меньше курсов обучения. Группы ${student.group} в расписании " +
                                    "не будет — проверьте группу и курс.",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = ColorErrorText,
                                ),
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorAccentGoldBg,
                    border = BorderStroke(1.dp, ColorAccentGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("student_where_now"),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ГДЕ СЕЙЧАС",
                            style = androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = ColorAccentGoldBright,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        when {
                            currentLesson != null -> {
                                Text(
                                    text = currentLesson.subjectRaw,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.White,
                                    ),
                                )
                                Text(
                                    text = buildString {
                                        append("Урок ${currentLesson.lessonNumber} • ")
                                        append("${currentLesson.timeStart}–${currentLesson.timeEnd}")
                                        val rooms = listOf(currentLesson.roomFirst, currentLesson.roomSecond)
                                            .filter { it.isNotBlank() }
                                        if (rooms.isNotEmpty()) append(" • каб. ${rooms.joinToString(" / ")}")
                                    },
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        color = ColorAccentGoldSoft,
                                    ),
                                )
                                val teacher = listOf(currentLesson.teacherFirst, currentLesson.teacherSecond)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" / ")
                                if (teacher.isNotBlank()) {
                                    Text(
                                        text = teacher,
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            color = ColorAccentGoldMuted,
                                        ),
                                    )
                                }
                            }
                            isLoading -> {
                                Text(
                                    text = "Загружаем расписание…",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                    ),
                                )
                            }
                            else -> {
                                val bells = CollegeBellSchedule.getBellsForDay(dow)
                                val breakNow = bells.firstOrNull {
                                    it.breakAfterMinutes > 0 &&
                                        minutes > it.endMinutes &&
                                        minutes < it.endMinutes + it.breakAfterMinutes
                                }
                                Text(
                                    text = when {
                                        allGroupLessons.isEmpty() ->
                                            "Группы нет в расписании на эту дату"
                                        breakNow != null -> "Перемена (${breakNow.breakAfterMinutes} мин)"
                                        nextLesson != null -> "Сейчас нет урока"
                                        else -> "Уроков на сегодня нет"
                                    },
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White,
                                    ),
                                )
                                if (nextLesson != null) {
                                    Text(
                                        text = "Следующий: урок ${nextLesson.lessonNumber} в ${nextLesson.timeStart} — ${nextLesson.subjectRaw}",
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.sp,
                                            color = ColorAccentGoldSoft,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (insights.activeNames.isNotEmpty()) {
                item {
                    Text(
                        text = "ПРЕПОДАВАТЕЛИ ГРУППЫ (${insights.activeNames.size})",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorAccentGold,
                        ),
                    )
                }
                items(insights.activeNames.sorted()) { teacherName ->
                    val rooms = insights.rooms(teacherName)
                    val subjects = insights.subjects(teacherName)
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = teacherName,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ColorTextTitle,
                                ),
                            )
                            if (subjects.isNotEmpty()) {
                                Text(
                                    text = subjects.take(3).joinToString("; "),
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted,
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (rooms.isNotEmpty()) {
                                Text(
                                    text = "каб. ${rooms.take(4).joinToString(", ")}",
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorBrandBlue,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            val subjects = allGroupLessons.map { it.subjectRaw }.filter { it.isNotBlank() }.distinct().sorted()
            if (subjects.isNotEmpty()) {
                item {
                    Text(
                        text = "ПРЕДМЕТЫ В РАСПИСАНИИ (${subjects.size})",
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorAccentGold,
                        ),
                    )
                }
                items(subjects) { subject ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = ColorSurfaceVariantLight,
                    ) {
                        Text(
                            text = subject,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextBody),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DayMode.entries.forEach { mode ->
                            val selected = dayMode == mode
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = if (selected) ColorAccentGold else ColorBgMain,
                                border = BorderStroke(1.dp, ColorAccentGold),
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        dayMode = mode
                                        calendarDate = null
                                    },
                            ) {
                                Text(
                                    text = mode.title,
                                    textAlign = TextAlign.Center,
                                    style = androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (selected) ColorOnAccentGold else ColorAccentGold,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (dayMode) {
                            DayMode.TODAY -> "РАСПИСАНИЕ ГРУППЫ ${student.group} НА СЕГОДНЯ"
                            DayMode.TOMORROW -> "РАСПИСАНИЕ ГРУППЫ ${student.group} НА ЗАВТРА"
                            DayMode.CALENDAR -> calendarDate?.let { "РАСПИСАНИЕ ГРУППЫ ${student.group} НА $it" }
                                ?: "КАЛЕНДАРЬ ГРУППЫ ${student.group}"
                        },
                        style = androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorBrandBlue,
                        ),
                    )
                }
            }

            if (dayMode == DayMode.CALENDAR) {
                if (availableDates.isEmpty()) {
                    item {
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            color = ColorSurfaceVariantLight,
                        ) {
                            Text(
                                text = "Архив расписания группы пуст — нажмите «Обновить» справа сверху",
                                style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(availableDates) { date ->
                                val selected = date == calendarDate

                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .bouncyClickable { calendarDate = date },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(2.dp),
                                        color = if (selected) ColorAccentGold else ColorBgMain,
                                        border = BorderStroke(1.dp, ColorAccentGold),
                                    ) {
                                        Text(
                                            text = date,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (selected) ColorOnAccentGold else ColorTextBody,
                                            ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val displayLessons = lessons
            if (displayLessons.isEmpty()) {
                item {
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = ColorSurfaceVariantLight,
                    ) {
                        Text(
                            text = when {
                                isLoading -> "Загружаем расписание…"
                                dayMode == DayMode.CALENDAR && calendarDate == null -> "Выберите дату выше"
                                dayMode == DayMode.TOMORROW -> "На завтра уроков нет"
                                else -> "На сегодня уроков нет"
                            },
                            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            } else {
                items(displayLessons) { lesson ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        color = ColorSurfaceVariantLight,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${lesson.lessonNumber}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ColorBrandBlue,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lesson.subjectRaw,
                                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = ColorTextBody),
                                )
                                Text(
                                    text = listOf(
                                        "${lesson.timeStart}–${lesson.timeEnd}",
                                        lesson.teacherFirst,
                                        lesson.roomFirst.takeIf { it.isNotBlank() }?.let { "каб. $it" } ?: "",
                                    ).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class DayMode(val title: String) {
    TODAY("Сегодня"),
    TOMORROW("Завтра"),
    CALENDAR("Календарь"),
}

internal fun resolveCardCurator(
    group: String,
    csvCurator: String,
): String? = CuratorStore.curatorFor(group) ?: csvCurator.trim().takeIf { it.isNotBlank() }

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label.uppercase(),
            style = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = ColorTextMuted,
            ),
        )
        Text(
            text = value,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = ColorTextBody,
            ),
        )
    }
}
