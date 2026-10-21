package com.politex.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.local.entity.TaskReminderEntity
import com.politex.data.model.GroupInfo
import com.politex.data.model.TaskType
import com.politex.data.repository.TaskRepository
import com.politex.ui.components.AppCard
import com.politex.ui.components.AppDivider
import com.politex.ui.components.BrandActionButton
import com.politex.ui.components.DialogTitleBar
import com.politex.ui.components.ToggleChip
import com.politex.ui.theme.ColorBadgeCourse
import com.politex.ui.theme.ColorBadgeDiploma
import com.politex.ui.theme.ColorBadgeExam
import com.politex.ui.theme.ColorBadgeHW
import com.politex.ui.theme.ColorBadgeLab
import com.politex.ui.theme.ColorBadgePract
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDangerFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorSurfaceVariantLight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import com.politex.util.HintFlags
import com.politex.util.MpkCurriculum
import com.politex.util.SubjectRenameStore
import com.politex.util.subjectDisplayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(
    groupInfo: GroupInfo,
    taskRepository: TaskRepository,
    modifier: Modifier = Modifier,

    snackbarHostState: SnackbarHostState? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf<TaskType?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val reminderDao = remember { MpkDatabase.getInstance(context).taskReminderDao() }
    var editingTask by remember { mutableStateOf<StudentTaskEntity?>(null) }
    var editingReminders by remember { mutableStateOf<List<Long>>(emptyList()) }

    var deleteConfirmTask by remember { mutableStateOf<StudentTaskEntity?>(null) }

    val allTasksFlow =
        remember(groupInfo.canonicalName) {
            taskRepository.getTasksForGroup(groupInfo.canonicalName)
        }
    val allTasks: List<StudentTaskEntity> by allTasksFlow.collectAsState(initial = emptyList())

    val filteredTasks =
        remember(allTasks, selectedFilter) {
            if (selectedFilter == null) {
                allTasks
            } else {
                allTasks.filter { it.taskTypeEnum == selectedFilter }
            }
        }

    var swipeHintDone by remember { mutableStateOf(HintFlags.isDone(context, HintFlags.KEY_SWIPE_TASKS)) }
    val demoTask =
        remember(groupInfo.canonicalName, groupInfo.course) {
            StudentTaskEntity(
                id = DEMO_TASK_ID,
                groupName = groupInfo.canonicalName,
                course = groupInfo.course,
                subjectName = "Подсказка",
                taskType = TaskType.HOMEWORK.displayName,
                title = "Свайпните влево, чтобы удалить",
            )
        }
    val visibleTasks =
        remember(filteredTasks, swipeHintDone, demoTask) {
            if (swipeHintDone) filteredTasks else listOf(demoTask) + filteredTasks
        }

    val availableTypes =
        remember(groupInfo.course) {
            TaskType.getAvailableForCourse(groupInfo.course)
        }

    val usedSubjects =
        remember(allTasks) {
            allTasks.map { it.subjectName }.filter { it.isNotBlank() }.distinct()
        }

    fun deleteWithUndo(task: StudentTaskEntity) {

        if (!swipeHintDone) {
            HintFlags.markDone(context, HintFlags.KEY_SWIPE_TASKS)
            swipeHintDone = true
        }
        if (isDemoTask(task.id)) return
        coroutineScope.launch {

            val remindersSnapshot = reminderDao.getForTask(task.id)
            taskRepository.deleteTask(task)
            if (snackbarHostState == null) return@launch
            val result =
                snackbarHostState.showSnackbar(
                    message = "Задание удалено",
                    actionLabel = "ОТМЕНИТЬ",
                    duration = SnackbarDuration.Indefinite,
                )
            if (result == SnackbarResult.ActionPerformed && !isDemoTask(task.id)) {
                taskRepository.addTask(task.copy())
                if (remindersSnapshot.isNotEmpty()) reminderDao.insertAll(remindersSnapshot)
            }
        }
    }

    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandActionButton(
                    icon = Icons.Default.Add,
                    label = "ДОБАВИТЬ",
                    testTag = "add_task_top_btn",
                    onClick = { showAddDialog = true },
                )

                val chipScroll = rememberScrollState()

                val chipOffsets = remember { mutableMapOf<Int, Pair<Int, Int>>() }
                LaunchedEffect(selectedFilter) {
                    val index = if (selectedFilter == null) 0 else availableTypes.indexOf(selectedFilter) + 1
                    if (index == 0) {

                        chipScroll.animateScrollTo(0)
                    } else {
                        chipOffsets[index]?.let { (start, width) ->
                            chipScroll.animateScrollTo(

                                centerScrollOffset(start, width, chipScroll.viewportSize, chipScroll.maxValue),
                            )
                        }
                    }
                }
                Row(
                    modifier =
                    Modifier
                        .weight(1f)
                        .horizontalScroll(chipScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TaskFilterChip(
                        label = "Все",
                        isSelected = selectedFilter == null,
                        index = 0,
                        offsets = chipOffsets,
                        tag = "task_filter_all",
                        onClick = { selectedFilter = null },
                    )
                    availableTypes.forEachIndexed { i, type ->
                        TaskFilterChip(
                            label = type.shortFilterLabel(),
                            isSelected = selectedFilter == type,
                            index = i + 1,
                            offsets = chipOffsets,
                            tag = "task_filter_${type.name.lowercase()}",
                            onClick = { selectedFilter = type },
                        )
                    }
                }
            }

            AppDivider(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            if (visibleTasks.isEmpty()) {

                val filterActive = selectedFilter != null

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
                                imageVector = Icons.Default.Checklist,
                                contentDescription = null,
                                tint = ColorBrandBlue,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (filterActive) "По этому фильтру ничего нет" else "Нет активных заданий",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (filterActive) {

                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = ColorBrandFill,
                                contentColor = Color.White,
                                modifier =
                                Modifier

                                    .heightIn(min = Tokens.ButtonHeight)
                                    .bouncyClickable { selectedFilter = null }
                                    .testTag("reset_filter_btn"),
                            ) {

                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "СБРОСИТЬ ФИЛЬТР",
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,

                                            fontSize = Tokens.ButtonText,
                                            color = Color.White,
                                        ),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Нажмите «Добавить», чтобы зафиксировать домашнее задание, лабораторную или курсовую работу.",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorTextMuted,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleTasks, key = { it.id }) { task ->
                        if (isDemoTask(task.id)) {
                            DemoSwipeHintItem(
                                task = task,
                                onDelete = { deleteWithUndo(task) },
                            )
                        } else {
                            SwipeToDeleteBox(
                                onDelete = { deleteWithUndo(task) },
                            ) {
                                TaskItemCard(
                                    task = task,

                                    modifier = Modifier,
                                    onToggleCompleted = {
                                        coroutineScope.launch {
                                            taskRepository.toggleTaskCompleted(task.id, !task.isCompleted)
                                        }
                                    },

                                    onEdit = {
                                        coroutineScope.launch {
                                            editingReminders = reminderDao.getForTask(task.id).map { it.triggerAtMillis }
                                            editingTask = task
                                        }
                                    },
                                    onLongPressDelete = { deleteConfirmTask = task },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun saveTaskAndReminders(
        entity: StudentTaskEntity,
        reminderMillis: List<Long>,
        onDone: () -> Unit,
    ) {
        coroutineScope.launch {
            val id = taskRepository.saveTask(entity)
            reminderDao.replaceForTask(
                id,
                reminderMillis.map { TaskReminderEntity(taskId = id, triggerAtMillis = it) },
            )
            onDone()
        }
    }

    if (showAddDialog) {
        TaskEditDialog(
            groupInfo = groupInfo,
            availableTypes = availableTypes,
            usedSubjects = usedSubjects,
            task = null,
            initialReminders = emptyList(),
            onDismissRequest = { showAddDialog = false },
            onTaskSaved = { entity, reminders ->
                saveTaskAndReminders(entity, reminders) { showAddDialog = false }
            },
        )
    }

    editingTask?.let { task ->
        TaskEditDialog(
            groupInfo = groupInfo,
            availableTypes = availableTypes,
            usedSubjects = usedSubjects,
            task = task,
            initialReminders = editingReminders,
            onDismissRequest = { editingTask = null },
            onTaskSaved = { entity, reminders ->
                saveTaskAndReminders(entity, reminders) { editingTask = null }
            },
        )
    }

    deleteConfirmTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTask = null },
            shape = RoundedCornerShape(2.dp),
            containerColor = ColorBgMain,
            title = {
                Text(
                    text = "Удалить задание?",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorTextTitle,
                    ),
                )
            },
            confirmButton = {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorDangerFill,
                    modifier =
                    Modifier
                        .testTag("delete_confirm_btn")
                        .bouncyClickable {
                            deleteConfirmTask = null
                            deleteWithUndo(task)
                        },
                ) {
                    Text(
                        text = "УДАЛИТЬ",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = Tokens.ButtonText,
                            color = Color.White,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            },
            dismissButton = {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = Color.Transparent,
                    modifier = Modifier.bouncyClickable { deleteConfirmTask = null },
                ) {
                    Text(
                        text = "ОТМЕНА",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = Tokens.ButtonText,
                            color = ColorTextBody,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskItemCard(
    task: StudentTaskEntity,
    onToggleCompleted: () -> Unit,
    onEdit: () -> Unit,

    onLongPressDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val badgeColor =
        when (task.taskTypeEnum) {
            TaskType.HOMEWORK -> ColorBadgeHW
            TaskType.LAB_WORK -> ColorBadgeLab
            TaskType.PRACTICAL_WORK -> ColorBadgePract
            TaskType.CONTROL_WORK -> ColorBadgeExam
            TaskType.COURSE_WORK -> ColorBadgeCourse
            TaskType.DIPLOMA -> ColorBadgeDiploma
        }

    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (task.isCompleted) ColorSurfaceVariantLight else ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        shadowElevation = 0.dp,
        modifier =
        modifier
            .fillMaxWidth()

            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit,
                onLongClick = onLongPressDelete,
            )
            .testTag("task_item_${task.id}"),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Box(
                modifier =
                Modifier
                    .size(Tokens.IconHit)
                    .bouncyClickable(onClick = onToggleCompleted)
                    .testTag("task_check_${task.id}"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "Выполнено" else "Не выполнено",
                    tint = if (task.isCompleted) ColorBrandBlue else ColorTextMuted,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.subjectName.isNotBlank()) {
                        Text(
                            text = subjectDisplayName(task.subjectName),
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = ColorBrandBlue,
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = badgeColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, badgeColor),
                    ) {
                        Text(
                            text = task.taskTypeEnum.badge,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = badgeColor,
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = task.title,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) ColorTextMuted else ColorTextTitle,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.description,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (task.deadlineDate.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = ColorTextMuted,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Срок: ${formatDateForShow(task.deadlineDate)}",
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontSize = 11.sp,
                                color = ColorTextMuted,
                            ),
                        )
                    }
                }
            }

        }
    }
}

private fun TaskType.shortFilterLabel(): String =
    when (this) {
        TaskType.HOMEWORK -> "ДЗ"
        TaskType.LAB_WORK -> "Лаб"
        TaskType.PRACTICAL_WORK -> "Практ"
        TaskType.CONTROL_WORK -> "КР"
        TaskType.COURSE_WORK -> "Курс"
        TaskType.DIPLOMA -> "Дипл"
    }

@Composable
private fun TaskFilterChip(
    label: String,
    isSelected: Boolean,
    index: Int,
    offsets: MutableMap<Int, Pair<Int, Int>>,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
        color = if (isSelected) ColorTopBar else ColorBgMain,
        modifier =
        Modifier

            .height(Tokens.ButtonHeight)
            .onGloballyPositioned { offsets[index] = it.positionInParent().x.toInt() to it.size.width }
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = Tokens.ChipText,
                    color = if (isSelected) Color.White else ColorTextBody,
                ),
            )
        }
    }
}

internal fun centerScrollOffset(
    chipStart: Int,
    chipWidth: Int,
    viewport: Int,
    maxScroll: Int,
): Int = (chipStart + chipWidth / 2 - viewport / 2).coerceIn(0, maxScroll)

private val SwipeFlingThreshold = 12.dp

private val SwipeReturnSpring: FiniteAnimationSpec<Float> =
    spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)

internal fun shouldDelete(
    offsetX: Float,
    widthPx: Float,
    lastFrameDeltaPx: Float,
    flingThresholdPx: Float,
): Boolean =
    widthPx > 0f &&
        (-offsetX >= widthPx * 0.35f || lastFrameDeltaPx <= -flingThresholdPx)

@Composable
private fun SwipeToDeleteBox(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,

    demoFraction: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }

    val collapse = remember { Animatable(1f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableIntStateOf(0) }
    var startHeight by remember { mutableStateOf(Dp.Unspecified) }
    var deleting by remember { mutableStateOf(false) }
    var lastDragDelta by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .then(if (deleting) Modifier.height(startHeight * collapse.value) else Modifier)
            .graphicsLayer { alpha = collapse.value }
            .onSizeChanged {
                if (!deleting) {
                    widthPx = it.width.toFloat()
                    heightPx = it.height
                }
            }
            .pointerInput(deleting) {
                if (deleting) return@pointerInput
                val flingThresholdPx = with(density) { SwipeFlingThreshold.toPx() }
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (shouldDelete(offsetX.value, widthPx, lastDragDelta, flingThresholdPx)) {
                            deleting = true
                            startHeight = with(density) { heightPx.toDp() }
                            scope.launch {
                                offsetX.animateTo(-widthPx, tween(durationMillis = 150))
                                collapse.animateTo(0f, tween(durationMillis = 200))
                                onDelete()
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f, SwipeReturnSpring) }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, SwipeReturnSpring) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    lastDragDelta = dragAmount
                    scope.launch { offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-widthPx, 0f)) }
                }
            },
    ) {
        Box(
            modifier =
            Modifier
                .matchParentSize()

                .clip(RoundedCornerShape(2.dp))
                .background(ColorDangerFill),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = Color.White,
                modifier =
                Modifier
                    .padding(end = 20.dp)
                    .size(20.dp),
            )
        }
        Box(
            modifier =
            Modifier.graphicsLayer {
                translationX = offsetX.value + demoFraction() * size.width
            },
        ) {
            content()
        }
    }
}

internal const val DEMO_TASK_ID = -1L

internal fun isDemoTask(id: Long): Boolean = id == DEMO_TASK_ID

@Composable
private fun DemoSwipeHintItem(
    task: StudentTaskEntity,
    onDelete: () -> Unit,
) {
    val demoOffset = remember { Animatable(0f) }
    LaunchedEffect(demoOffset) {
        while (isActive) {
            delay(800)
            repeat(2) {
                demoOffset.animateTo(-0.4f, tween(700))
                demoOffset.animateTo(0f, tween(700))
                delay(200)
            }
            delay(2500)
        }
    }
    SwipeToDeleteBox(
        onDelete = onDelete,
        demoFraction = { demoOffset.value },
    ) {

        TaskItemCard(
            task = task,
            onToggleCompleted = {},
            onEdit = {},
            onLongPressDelete = {},
        )
    }
}

@Composable
fun TaskEditDialog(
    groupInfo: GroupInfo,
    availableTypes: List<TaskType>,
    usedSubjects: List<String> = emptyList(),
    task: StudentTaskEntity? = null,
    initialReminders: List<Long> = emptyList(),
    onDismissRequest: () -> Unit,
    onTaskSaved: (entity: StudentTaskEntity, reminderMillis: List<Long>) -> Unit,
) {
    val isEdit = task != null
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    val context = LocalContext.current

    val currentYear =
        remember {
            java.util.Calendar
                .getInstance()
                .get(java.util.Calendar.YEAR)
        }
    var selectedType by remember { mutableStateOf(task?.taskTypeEnum ?: TaskType.HOMEWORK) }

    val subjectOptions =
        remember(groupInfo.specialtyCode, groupInfo.course, usedSubjects) {
            subjectListFor(groupInfo, usedSubjects)
        }
    val defaultSubject = subjectOptions.firstOrNull() ?: "Охрана труда"

    val subjectInList = task != null && subjectOptions.contains(task.subjectName)
    var selectedSubject by remember {
        mutableStateOf(if (subjectInList) (task?.subjectName ?: defaultSubject) else defaultSubject)
    }
    var subjectExpanded by remember { mutableStateOf(false) }
    var isCustomSubject by remember { mutableStateOf(task != null && !subjectInList) }
    var customSubject by remember {
        mutableStateOf(if (task != null && !subjectInList) task.subjectName else "")
    }
    val effectiveSubject = if (isCustomSubject) customSubject.trim() else selectedSubject

    var reminders by remember { mutableStateOf(initialReminders) }
    var showReminderComposer by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,

        properties = DialogProperties(dismissOnClickOutside = false),
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {

            DialogTitleBar(
                text = if (isEdit) "Редактировать задание" else "Новое задание",
                onClose = onDismissRequest,
            )
        },
        text = {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()

                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(

                    text = "Категория: ${selectedType.displayName}",
                    style =
                    androidx.compose.ui.text
                        .TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(availableTypes) { type ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                            color = if (isSelected) ColorTopBar else ColorBgMain,
                            contentColor = if (isSelected) Color.White else ColorTextBody,
                            modifier =
                            Modifier
                                .height(Tokens.ChipForm)
                                .bouncyClickable { selectedType = type },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = type.badge,
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                    ),
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ToggleChip("Из списка", !isCustomSubject) { isCustomSubject = false }
                    ToggleChip("Другое…", isCustomSubject) { isCustomSubject = true }
                }

                if (isCustomSubject) {
                    OutlinedTextField(
                        value = customSubject,
                        onValueChange = { customSubject = it },
                        label = { Text("Своё название", fontSize = 12.sp) },
                        placeholder = { Text("Например: Репетитор, Секции", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(2.dp),
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("custom_subject_field"),
                    )
                } else {

                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(1.dp, ColorBorderLight),
                        color = ColorBgMain,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .bouncyClickable { subjectExpanded = true }
                            .testTag("subject_field"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = SubjectRenameStore.displayName(context, selectedSubject).ifBlank { "Предмет" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    color = if (selectedSubject.isBlank()) ColorTextMuted else ColorTextTitle,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Выбрать предмет",
                                tint = ColorTextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Что нужно сделать", fontSize = 12.sp) },
                    placeholder = { Text("Например: Упр. 4, Отчет по лабе №2", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Напоминания:",
                    style =
                    androidx.compose.ui.text
                        .TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                )

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(2.dp, ColorBorderLight),
                    color = ColorBgMain,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .bouncyClickable { showReminderComposer = !showReminderComposer }
                        .testTag("add_reminder_chip"),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Добавить напоминание",
                            tint = ColorBrandBlue,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                reminders.forEachIndexed { index, millis ->
                    ReminderChip(
                        label = formatReminderChip(millis),
                        onRemove = { reminders = reminders.filterIndexed { i, _ -> i != index } },
                    )
                }

                if (showReminderComposer) {
                    ReminderComposer(
                        initialDateIso = todayIso(),
                        currentYear = currentYear,
                        onDismiss = { showReminderComposer = false },
                        onAdd = { millis ->
                            reminders = reminders + millis
                            showReminderComposer = false
                        },
                    )
                }
            }
        },
        confirmButton = {

            val canSave = title.isNotBlank() && effectiveSubject.isNotBlank()
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier =
                Modifier
                    .testTag("task_edit_confirm")
                    .alpha(if (canSave) 1f else 0.4f)
                    .bouncyClickable(enabled = canSave) {

                    val dueIso = task?.deadlineDate ?: ""

                    val entity =
                        if (task == null) {
                            StudentTaskEntity(
                                groupName = groupInfo.canonicalName,
                                course = groupInfo.course,
                                subjectName = effectiveSubject,
                                taskType = selectedType.displayName,
                                title = title.trim(),
                                description = description.trim(),
                                deadlineDate = dueIso,
                                createdAt = System.currentTimeMillis(),
                            )
                        } else {
                            task.copy(
                                subjectName = effectiveSubject,
                                taskType = selectedType.displayName,
                                title = title.trim(),
                                description = description.trim(),
                                deadlineDate = dueIso,
                            )
                        }
                    onTaskSaved(entity, reminders)
                },
            ) {
                Text(
                    text = if (isEdit) "СОХРАНИТЬ" else "ДОБАВИТЬ",
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.White,
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        },
        dismissButton = {
            Surface(
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, ColorBorderLight),
                color = Color.Transparent,
                modifier = Modifier.bouncyClickable { onDismissRequest() },
            ) {
                Text(
                    text = "ОТМЕНА",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorTextBody,
                    ),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        },
    )

    if (subjectExpanded) {
        SubjectPickerDialog(
            options = subjectOptions,
            selected = selectedSubject,
            onDismiss = { subjectExpanded = false },
            onPick = {
                selectedSubject = it
                subjectExpanded = false
            },
        )
    }
}

internal fun formatDateForShow(raw: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(raw.trim()) ?: return raw
    return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
}

internal fun reminderMillisFrom(
    iso: String,
    hour: Int,
    minute: Int,
    plusDays: Int,
): Long? {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").find(iso.trim()) ?: return null
    val cal =
        java.util.Calendar.getInstance().apply {
            clear()
            set(m.groupValues[1].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[3].toInt(), hour, minute, 0)
            if (plusDays != 0) add(java.util.Calendar.DAY_OF_YEAR, plusDays)
        }
    return cal.timeInMillis
}

internal fun formatReminderChip(millis: Long): String {
    val c =
        java.util.Calendar
            .getInstance()
            .apply { timeInMillis = millis }
    return String.format(
        java.util.Locale.ROOT,
        "%02d.%02d %02d:%02d",
        c.get(java.util.Calendar.DAY_OF_MONTH),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.HOUR_OF_DAY),
        c.get(java.util.Calendar.MINUTE),
    )
}

internal fun todayIso(): String {
    val c = java.util.Calendar.getInstance()
    return String.format(
        java.util.Locale.ROOT,
        "%04d-%02d-%02d",
        c.get(java.util.Calendar.YEAR),
        c.get(java.util.Calendar.MONTH) + 1,
        c.get(java.util.Calendar.DAY_OF_MONTH),
    )
}

@Composable
private fun ReminderChip(
    label: String,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.5f)),
        color = ColorBrandBlue.copy(alpha = 0.12f),
        contentColor = ColorBrandBlue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp),
        ) {
            Text(
                text = label,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = ColorBrandBlue,
                ),
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onRemove, modifier = Modifier.size(Tokens.IconHit)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить напоминание",
                    tint = ColorBrandBlue,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorTopBar else ColorBorderLight),
        color = if (selected) ColorTopBar else ColorBgMain,
        contentColor = if (selected) Color.White else ColorTextBody,
        modifier =
        Modifier
            .height(Tokens.ChipForm)
            .bouncyClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                text = label,
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

@Composable
private fun ReminderComposer(
    initialDateIso: String,
    currentYear: Int,
    onDismiss: () -> Unit,
    onAdd: (Long) -> Unit,
) {
    var dateIso by remember { mutableStateOf(initialDateIso) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customTime by remember { mutableStateOf(false) }
    var presetHour by remember { mutableIntStateOf(18) }
    var presetMinute by remember { mutableIntStateOf(0) }
    var hourInput by remember { mutableStateOf("18") }
    var minuteInput by remember { mutableStateOf("00") }
    val presets = remember { listOf(8 to 0, 12 to 0, 18 to 0, 21 to 0) }

    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, ColorBrandBlue.copy(alpha = 0.4f)),
        color = ColorSurfaceHighlight,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable { showDatePicker = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = ColorBrandBlue,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Дата: ${formatDateForShow(dateIso)}",
                        style =
                        androidx.compose.ui.text
                            .TextStyle(fontSize = 13.sp, color = ColorTextTitle),
                    )
                }
            }

            Text(
                text = "Время:",
                style =
                androidx.compose.ui.text
                    .TextStyle(fontSize = 12.sp, color = ColorTextMuted),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(presets) { preset ->
                    val (h, m) = preset
                    val selected = !customTime && presetHour == h && presetMinute == m
                    TimeChip(
                        label = String.format(java.util.Locale.ROOT, "%02d:%02d", h, m),
                        selected = selected,
                        onClick = {
                            customTime = false
                            presetHour = h
                            presetMinute = m
                        },
                    )
                }
                item {
                    TimeChip(label = "Своё", selected = customTime, onClick = { customTime = true })
                }
            }

            if (customTime) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,

                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    OutlinedTextField(
                        value = hourInput,
                        onValueChange = { hourInput = it.filter(Char::isDigit).take(2) },
                        label = { Text("ЧЧ", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        text = ":",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ColorTextTitle,
                        ),
                    )
                    OutlinedTextField(
                        value = minuteInput,
                        onValueChange = { minuteInput = it.filter(Char::isDigit).take(2) },
                        label = { Text("ММ", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.width(72.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    border = BorderStroke(1.dp, ColorBorderLight),
                    color = Color.Transparent,
                    modifier = Modifier.bouncyClickable(onClick = onDismiss),
                ) {
                    Text(
                        text = "ОТМЕНА",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ColorTextBody,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                val h = if (customTime) hourInput.toIntOrNull() ?: -1 else presetHour
                val m = if (customTime) minuteInput.toIntOrNull() ?: -1 else presetMinute
                val canAdd = h in 0..23 && m in 0..59
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBrandFill,
                    modifier =
                    Modifier
                        .testTag("reminder_composer_confirm")
                        .alpha(if (canAdd) 1f else 0.4f)
                        .bouncyClickable(enabled = canAdd) {
                            reminderMillisFrom(dateIso, h, m, 0)?.let(onAdd)
                        },
                ) {
                    Text(
                        text = "ДОБАВИТЬ",
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        com.politex.ui.components.SimpleDatePickerDialog(
            initialDateIso = dateIso,
            minYear = currentYear,
            maxYear = currentYear + 4,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                dateIso = it
                showDatePicker = false
            },
        )
    }
}

internal fun subjectListFor(
    groupInfo: GroupInfo,
    used: List<String>,
): List<String> =
    (MpkCurriculum.getSubjects(groupInfo.specialtyCode, groupInfo.course) + used)
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

@Composable
private fun SubjectPickerDialog(
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(2.dp),
        containerColor = ColorBgMain,
        title = {
            Text(
                text = "Предмет",
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ColorTextTitle,
                ),
            )
        },
        text = {
            LazyColumn(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .testTag("subject_picker_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options) { subject ->
                    val isSelected = subject == selected
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(2.dp, ColorBorderLight),
                        color = if (isSelected) ColorTopBar else ColorBgMain,
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .bouncyClickable { onPick(subject) }
                            .testTag("subject_option_$subject"),
                    ) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Text(
                                text = SubjectRenameStore.displayName(context, subject),
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 14.sp,
                                    color = if (isSelected) Color.White else ColorTextTitle,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}
