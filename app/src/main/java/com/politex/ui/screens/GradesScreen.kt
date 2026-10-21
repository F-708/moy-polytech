package com.politex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.GradeDao
import com.politex.data.local.entity.GradeConfigEntity
import com.politex.data.local.entity.GradeEntryEntity
import com.politex.data.model.GroupInfo
import com.politex.ui.components.AppCard
import com.politex.ui.theme.ColorActiveBlue
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorError
import com.politex.ui.theme.ColorSuccess
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.ColorWarning
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberToday
import com.politex.util.GradeAggregator
import com.politex.util.GradeCategory
import com.politex.util.HintFlags
import com.politex.util.MpkCurriculum
import com.politex.util.SubjectRenameStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

object GradeDetailMode {
    var active by mutableStateOf(false)
}

private val UI_CATEGORIES = GradeCategory.values().filter { it != GradeCategory.LAB }

internal enum class DeleteArmState { NONE, ARMED, DELETED }

internal fun gradeDeleteAction(
    current: DeleteArmState,
    tapped: Boolean,
    expired: Boolean,
): DeleteArmState =
    when {
        expired -> DeleteArmState.NONE
        !tapped -> current
        current == DeleteArmState.ARMED -> DeleteArmState.DELETED
        else -> DeleteArmState.ARMED
    }

private const val DELETE_ARM_TIMEOUT_MS = 500L

internal const val TUTORIAL_SUBJECT = "Тестовый урок"

@Composable
fun GradesScreen(
    groupInfo: GroupInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dao = remember { MpkDatabase.getInstance(context).gradeDao() }

    var openSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSubject by rememberSaveable { mutableStateOf<String?>(null) }

    var showTutorial by rememberSaveable { mutableStateOf(false) }

    val detail = openSubject != null || settingsSubject != null || showTutorial
    DisposableEffect(detail) {
        GradeDetailMode.active = detail
        onDispose { GradeDetailMode.active = false }
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        when {
            showTutorial ->
                GradeTutorialScreen(onBack = { showTutorial = false })
            settingsSubject != null ->
                GradeSubjectSettingsScreen(
                    subject = settingsSubject!!,
                    dao = dao,
                    onBack = { settingsSubject = null },
                )
            openSubject != null ->
                GradeSubjectScreen(
                    subject = openSubject!!,
                    dao = dao,
                    onBack = { openSubject = null },
                    onOpenSettings = { settingsSubject = openSubject },
                )
            else ->
                GradeSubjectList(
                    groupInfo = groupInfo,
                    dao = dao,
                    onOpenSubject = { openSubject = it },
                    onOpenTutorial = { showTutorial = true },
                )
        }
    }
}

@Composable
private fun GradeSubjectList(
    groupInfo: GroupInfo,
    dao: GradeDao,
    onOpenSubject: (String) -> Unit,
    onOpenTutorial: () -> Unit,
) {
    val context = LocalContext.current
    val entries by dao.allEntries().collectAsState(initial = emptyList())
    val configs by dao.allConfigs().collectAsState(initial = emptyList())
    val subjects =
        remember(groupInfo.specialtyCode, groupInfo.course) {
            MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course)
        }

    val tutorialPending = remember { !HintFlags.isDone(context, HintFlags.KEY_GRADES_TUTORIAL) }

    LazyColumn(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_root_list"),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tutorialPending) {
            item {
                AppCard(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .pulseHalo(true)
                        .testTag("grades_tutorial_row")
                        .bouncyClickable { onOpenTutorial() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = TUTORIAL_SUBJECT,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(fontSize = 14.sp, color = ColorTextTitle),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = GradeAggregator.format(4.0),
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = gradeColor(4.0)),
                            modifier = Modifier.testTag("grades_tutorial_avg"),
                        )
                    }
                }
            }
        }

        items(subjects) { subject ->

            val avg =
                remember(entries, configs, subject) {
                    val config = configs.firstOrNull { it.subjectName == subject }
                    subjectAverage(
                        byCategory =
                        entries
                            .filter { it.subjectName == subject }
                            .groupBy({ it.category }) { it.value },
                        config = config,
                        weightsEnabled =
                        GradeAggregator.effectiveWeightsEnabled(subject, config?.weightsEnabled ?: true),
                    )
                }
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("grades_subject_row_$subject")
                    .bouncyClickable { onOpenSubject(subject) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SubjectRenameStore.displayName(context, subject),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontSize = 14.sp, color = ColorTextTitle),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (avg == null) {

                        Text(
                            text = "нет оценок",
                            style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                            modifier = Modifier.testTag("grades_avg_$subject"),
                        )
                    } else {
                        Text(
                            text = GradeAggregator.format(avg),
                            style =
                            TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = gradeColor(avg),
                            ),
                            modifier = Modifier.testTag("grades_avg_$subject"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeTutorialScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val finish: () -> Unit = {
        HintFlags.markDone(context, HintFlags.KEY_GRADES_TUTORIAL)
        onBack()
    }
    BackHandler { finish() }

    var gradeVisible by remember { mutableStateOf(true) }
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(DELETE_ARM_TIMEOUT_MS)
            armed = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().testTag("grades_tutorial_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = finish, modifier = Modifier.size(Tokens.HitTarget).testTag("grades_tutorial_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ColorTextTitle)
            }
            Text(
                text = TUTORIAL_SUBJECT,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                modifier = Modifier.weight(1f).padding(end = 4.dp),
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = GradeAggregator.format(4.0),
                style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, color = gradeColor(4.0)),
                modifier = Modifier.testTag("grades_tutorial_avg_big"),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("grades_tutorial_panel"),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Двойное нажатие по оценке удалит её",
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                    modifier = Modifier.fillMaxWidth().testTag("grades_tutorial_hint"),
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (gradeVisible) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .pulseHalo(!armed)
                            .testTag("grades_tutorial_chip")
                            .bouncyClickable {
                                if (!armed) {
                                    armed = true
                                } else {
                                    armed = false
                                    gradeVisible = false
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = if (armed) ColorDangerFill else ColorBgMain,
                            border = BorderStroke(1.dp, if (armed) ColorDangerFill else ColorBorderLight),
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "4",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (armed) Color.White else gradeColor(4.0),
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Оценка удалена. Готово!",
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        modifier = Modifier.height(28.dp).testTag("grades_tutorial_done"),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeSubjectScreen(
    subject: String,
    dao: GradeDao,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {

    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = rememberToday()

    val entriesState by dao.entriesForSubject(subject).collectAsState(initial = null)
    val loaded = entriesState != null
    val entries = entriesState ?: emptyList()
    val configs by dao.allConfigs().collectAsState(initial = emptyList())

    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }
    val config = remember(configs, subject) { configs.firstOrNull { it.subjectName == subject } }

    val pe = remember(subject) { GradeAggregator.isPhysicalEducation(subject) }
    val weightsEnabled = GradeAggregator.effectiveWeightsEnabled(subject, config?.weightsEnabled ?: true)
    val ws = remember(config) { parseWeightsList(config?.weightsJson) ?: GradeAggregator.DEFAULT_WEIGHTS }
    val mode = config?.roundingMode ?: ".0"
    val rawAvg = remember(byCategory, config, weightsEnabled) { subjectAverage(byCategory, config, weightsEnabled) }

    val avg =
        remember(rawAvg, mode) {
            rawAvg?.let { GradeAggregator.roundAvg(it, mode) }
        }

    var activeCategory by rememberSaveable(subject) { mutableStateOf(GradeCategory.REGULAR.name) }
    val inputCategory = if (pe) GradeCategory.REGULAR.name else activeCategory

    fun submit(value: Int) {
        val entry =
            GradeEntryEntity(
                subjectName = subject,
                category = inputCategory,
                value = value,
                label = "",
                dateIso = isoOf(today),
            )
        scope.launch { dao.insert(entry) }
    }

    var undoStack by remember { mutableStateOf(ArrayDeque<GradeEntryEntity>()) }
    LaunchedEffect(undoStack) {
        if (undoStack.isNotEmpty()) {
            delay(5000)
            undoStack = ArrayDeque()
        }
    }
    fun deleteGrade(e: GradeEntryEntity) {
        undoStack = ArrayDeque(undoStack).apply { addLast(e) }
        scope.launch { dao.deleteById(e.id) }
    }
    fun restoreLastGrade() {
        val e = undoStack.lastOrNull() ?: return
        undoStack = ArrayDeque(undoStack).apply { removeLast() }
        scope.launch { dao.insert(e) }
    }

    var armedId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(armedId) {
        if (armedId != null) {
            delay(DELETE_ARM_TIMEOUT_MS)
            armedId = null
        }
    }
    fun onChipTap(e: GradeEntryEntity) {
        val current = if (armedId == e.id) DeleteArmState.ARMED else DeleteArmState.NONE
        when (gradeDeleteAction(current, tapped = true, expired = false)) {
            DeleteArmState.ARMED -> armedId = e.id
            DeleteArmState.DELETED -> {
                armedId = null
                deleteGrade(e)
            }
            DeleteArmState.NONE -> armedId = null
        }
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_subject_screen"),
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(Tokens.HitTarget).testTag("grades_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ColorTextTitle)
            }
            Text(
                text = SubjectRenameStore.displayName(context, subject),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            )
            IconButton(
                onClick = onOpenSettings,
                modifier =
                Modifier
                    .size(44.dp)
                    .testTag("grades_settings_btn"),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки предмета",
                    tint = ColorTextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {

                !loaded -> Unit
                avg == null -> EmptyDots(modifier = Modifier.testTag("grades_current_avg"))
                else ->
                    Text(
                        text = GradeAggregator.format(avg, mode),
                        style =
                        TextStyle(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = gradeColor(avg),
                        ),
                        modifier = Modifier.testTag("grades_current_avg"),
                    )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("grades_input_panel"),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {

                undoStack.lastOrNull()?.let { deleted ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Tokens.HitTarget)
                            .testTag("grades_undo_row"),
                        color = ColorSurfaceHighlight,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp),
                        ) {
                            Text(
                                text = "Удалена оценка «${deleted.value}»",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontSize = 12.sp, color = ColorTextBody),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "ОТМЕНИТЬ",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorBrandBlue,
                                ),
                                modifier =
                                Modifier
                                    .testTag("grades_undo_btn")
                                    .bouncyClickable { restoreLastGrade() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val activeEntries =
                    remember(entries, inputCategory, pe) {
                        if (pe) entries else entries.filter { it.category == inputCategory }
                    }
                if (!loaded) {

                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("grades_recent_box"),
                    )
                } else if (activeEntries.isEmpty()) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("grades_recent_box"),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyDots()
                    }
                } else {
                    FlowRow(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp, max = 124.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("grades_recent_box"),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {

                        activeEntries.forEach { e ->
                            val armed = armedId == e.id
                            Box(
                                modifier =
                                Modifier
                                    .size(44.dp)
                                    .testTag("grades_recent_${e.id}")
                                    .bouncyClickable { onChipTap(e) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = if (armed) ColorDangerFill else ColorBgMain,
                                    border = BorderStroke(1.dp, if (armed) ColorDangerFill else ColorBorderLight),
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = e.value.toString(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style =
                                            TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (armed) Color.White else gradeColor(e.value.toDouble()),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!pe) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        UI_CATEGORIES.forEach { category ->
                            CategorySegment(
                                category = category,
                                count = entries.count { it.category == category.name },
                                selected = activeCategory == category.name,
                                onClick = { activeCategory = category.name },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (1..5).forEach { NumberKey(value = it, onSubmit = ::submit) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (6..9).forEach { NumberKey(value = it, onSubmit = ::submit) }
                        NumberKey(value = 10, onSubmit = ::submit)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NumberKey(
    value: Int,
    onSubmit: (Int) -> Unit,
) {
    AppCard(
        modifier = Modifier
            .weight(1f)
            .height(56.dp)
            .testTag("grades_num_$value")
            .bouncyClickable { onSubmit(value) },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = value.toString(),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
            )
        }
    }
}

@Composable
private fun RowScope.CategorySegment(
    category: GradeCategory,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (selected) ColorTopBar else ColorBgMain,
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        modifier =
        Modifier
            .weight(1f)

            .heightIn(min = Tokens.ChipScreen)
            .testTag("grades_chip_${category.name}")
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val short = CATEGORY_SHORT[category.name] ?: category.title
            Text(
                text = if (count > 0) "$short · $count" else short,

                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style =
                TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

@Composable
private fun WeightSlider(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {

    val snap: (Float, Float) -> Int = { x, w ->
        GradeAggregator.snapWeight((x / w).coerceIn(0f, 1f))
    }
    Canvas(
        modifier =
        modifier
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { onValueChange(snap(it.x, size.width.toFloat())) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { onValueChange(snap(it.x, size.width.toFloat())) },
                    onHorizontalDrag = { change, _ ->
                        onValueChange(snap(change.position.x, size.width.toFloat()))
                        change.consume()
                    },
                )
            },
    ) {
        val pad = 8.dp.toPx()
        val y = size.height / 2f
        val trackW = size.width - pad * 2
        val thumbX = pad + trackW * (value / 100f)
        val stroke = 4.dp.toPx()
        drawLine(ColorBorderLight, Offset(pad, y), Offset(pad + trackW, y), stroke, cap = StrokeCap.Round)
        if (enabled) {
            drawLine(ColorActiveBlue, Offset(pad, y), Offset(thumbX, y), stroke, cap = StrokeCap.Round)
        }
        drawCircle(if (enabled) ColorBrandFill else ColorTextMuted, 8.dp.toPx(), Offset(thumbX, y))
    }
}

@Composable
private fun EmptyDots(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .height(18.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                Box(
                    modifier =
                    Modifier
                        .size(4.dp)
                        .background(ColorTextMuted, CircleShape),
                )
            }
        }
    }
}

private val ROUNDING_MODES = listOf(".0" to ".0", ".00" to ".00", "целое" to "целое")

private fun normalizeMode(mode: String?): String =
    when (mode) {
        ".00" -> ".00"
        "целое", "1" -> "целое"
        else -> ".0"
    }

@Composable
private fun GradeSubjectSettingsScreen(
    subject: String,
    dao: GradeDao,
    onBack: () -> Unit,
) {

    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pe = remember(subject) { GradeAggregator.isPhysicalEducation(subject) }
    val entries by dao.entriesForSubject(subject).collectAsState(initial = emptyList())
    val byCategory = remember(entries) { entries.groupBy({ it.category }) { it.value } }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    var weights by remember { mutableStateOf<List<Int>?>(null) }
    var priorityJson by remember { mutableStateOf("[]") }
    var roundingMode by remember { mutableStateOf(".0") }
    var weightsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(subject) {
        val cfg = dao.getConfig(subject)
        priorityJson = cfg?.priorityJson?.ifBlank { "[]" } ?: "[]"
        weights = parseWeightsList(cfg?.weightsJson) ?: GradeAggregator.DEFAULT_WEIGHTS
        roundingMode = normalizeMode(cfg?.roundingMode)
        weightsEnabled = cfg?.weightsEnabled ?: true
    }

    Column(
        modifier =
        Modifier
            .fillMaxSize()
            .testTag("grades_settings_screen"),
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).testTag("grades_settings_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = ColorTextTitle)
            }
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = "Настройки предмета",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                )
                Text(
                    text = SubjectRenameStore.displayName(context, subject),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
            }
        }

        val ws = weights
        if (ws != null) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {

                SectionCaption("ПРИНЦИП СЧЁТА")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ROUNDING_MODES.forEach { (value, label) ->
                        RoundingSegment(
                            label = label,
                            selected = roundingMode == value,
                            tag = "grades_round_$value",
                            onClick = { roundingMode = value },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (!pe) {
                    WeightsSettingsBlock(
                        weightsEnabled = weightsEnabled,
                        onWeightsEnabledChange = { weightsEnabled = it },
                        byCategory = byCategory,
                        ws = ws,
                        roundingMode = roundingMode,
                        onWeightChange = { index, v -> weights = ws.toMutableList().apply { set(index, v) } },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorWarning),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .testTag("grades_delete_all")
                        .bouncyClickable { confirmDeleteAll = true },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Удалить все оценки",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style =
                            TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorWarning,
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                border = BorderStroke(1.dp, ColorBrandFill),
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(44.dp)
                    .testTag("grades_settings_save")
                    .bouncyClickable {
                        scope.launch {
                            dao.upsertConfig(
                                GradeConfigEntity(
                                    subjectName = subject,
                                    priorityJson = priorityJson,
                                    weightsJson = JSONArray(ws).toString(),
                                    roundingMode = roundingMode,
                                    roundUp = false,
                                    weightsEnabled = weightsEnabled,
                                ),
                            )
                            onBack()
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "СОХРАНИТЬ",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White),
                    )
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            shape = RoundedCornerShape(2.dp),
            containerColor = ColorBgMain,
            title = {
                Text(
                    text = "Удаление оценок",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                )
            },
            text = {
                Text(
                    text =
                    "Удалить все оценки по предмету „" +
                        SubjectRenameStore.displayName(context, subject) +
                        "“? Действие нельзя отменить.",
                    style = TextStyle(fontSize = 13.sp, color = ColorTextBody),
                )
            },
            confirmButton = {
                Text(
                    text = "Удалить",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorWarning),
                    modifier =
                    Modifier
                        .testTag("grades_delete_all_confirm")
                        .bouncyClickable {
                            confirmDeleteAll = false
                            scope.launch { dao.deleteBySubject(subject) }
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            dismissButton = {
                Text(
                    text = "Отмена",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 13.sp, color = ColorTextMuted),
                    modifier =
                    Modifier
                        .testTag("grades_delete_all_cancel")
                        .bouncyClickable { confirmDeleteAll = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }
}

@Composable
private fun WeightsSettingsBlock(
    weightsEnabled: Boolean,
    onWeightsEnabledChange: (Boolean) -> Unit,
    byCategory: Map<String, List<Int>>,
    ws: List<Int>,
    roundingMode: String,
    onWeightChange: (Int, Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .testTag("grades_weights_toggle")
            .bouncyClickable { onWeightsEnabledChange(!weightsEnabled) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Веса категорий",
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle),
                )
                Text(
                    text = if (weightsEnabled) "Включено" else "Выключено",
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
            Switch(
                checked = weightsEnabled,
                onCheckedChange = onWeightsEnabledChange,
                modifier = Modifier.testTag("grades_weights_switch"),
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    SectionCaption("ВЕСА")

    val avg =
        remember(byCategory, ws, roundingMode, weightsEnabled) {
            val raw =
                if (weightsEnabled) {
                    GradeAggregator.weightedAverage(byCategory, weightsMap(ws))
                } else {
                    GradeAggregator.plainAverage(byCategory)
                }
            raw?.let { GradeAggregator.roundAvg(it, roundingMode) }
        }
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("grades_weight_preview"),
        color = ColorSurfaceHighlight,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Средний балл", style = TextStyle(fontSize = 11.sp, color = ColorTextMuted))
            if (avg == null) {
                EmptyDots()
            } else {
                Text(
                    text = GradeAggregator.format(avg, roundingMode),
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = gradeColor(avg)),
                )
            }

            if (weightsEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = weightsPreviewLine(ws),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 11.sp, color = ColorTextMuted),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    UI_CATEGORIES.forEachIndexed { i, category ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = CATEGORY_SHORT[category.name] ?: category.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (weightsEnabled) ColorTextTitle else ColorTextMuted,
                ),
                modifier = Modifier.width(60.dp),
            )

            WeightSlider(
                value = ws[i],
                enabled = weightsEnabled,
                onValueChange = { v -> onWeightChange(i, v) },
                modifier = Modifier.weight(1f).testTag("grades_weight_slider_${category.name}"),
            )
            Text(
                text = "${ws[i]}%",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                style =
                TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (weightsEnabled) ColorBrandBlue else ColorTextMuted,
                ),
                modifier = Modifier.width(44.dp),
            )
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ColorBrandBlue),
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun RowScope.RoundingSegment(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (selected) ColorTopBar else ColorBgMain,
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        modifier =
        Modifier
            .weight(1f)
            .height(Tokens.ChipForm)
            .testTag(tag)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

private fun gradeColor(avg: Double?): Color = when {
    avg == null -> ColorTextMuted
    avg >= 7.0 -> ColorSuccess
    avg < 4.0 -> ColorError
    else -> ColorTextTitle
}

private fun subjectAverage(
    byCategory: Map<String, List<Int>>,
    config: GradeConfigEntity?,
    weightsEnabled: Boolean,
): Double? =
    GradeAggregator.subjectAverage(
        byCategory,
        weightsMap(parseWeightsList(config?.weightsJson) ?: GradeAggregator.DEFAULT_WEIGHTS),
        weightsEnabled,
    )

private fun parseWeightsList(json: String?): List<Int>? =
    try {
        if (json.isNullOrBlank()) {
            null
        } else {
            val arr = JSONArray(json)
            if (arr.length() != UI_CATEGORIES.size) {
                null
            } else {
                (0 until arr.length()).map { arr.getInt(it).coerceIn(0, 100) }
            }
        }
    } catch (_: Exception) {
        null
    }

private fun weightsMap(weights: List<Int>): Map<String, Int> = UI_CATEGORIES.map { it.name }.zip(weights).toMap()

private fun weightsPreviewLine(ws: List<Int>): String {
    val total = ws.sum()
    if (total == 0) return "—"
    return UI_CATEGORIES
        .mapIndexed { i, c -> ws[i] to "${(ws[i] * 100.0 / total).roundToInt()}% ${CATEGORY_SHORT[c.name] ?: c.title}" }
        .sortedByDescending { it.first }
        .joinToString(" + ") { it.second }
}

private val CATEGORY_SHORT =
    mapOf(

        GradeCategory.REGULAR.name to "Обычные",
        GradeCategory.PRACTICAL.name to "Практич.",
        GradeCategory.CONTROL.name to "Контр.",
        GradeCategory.EXAM.name to "Экзамен",
    )

private fun isoOf(cal: Calendar): String =
    String.format(
        Locale.ROOT,
        "%04d-%02d-%02d",
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
