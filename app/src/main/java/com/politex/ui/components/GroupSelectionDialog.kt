package com.politex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.theme.Tokens
import com.politex.ui.teacher.TeacherOnboardingLogic
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberTeachers
import com.politex.util.GroupCatalog
import com.politex.util.GroupParser
import com.politex.util.SubgroupPref
import com.politex.util.TeacherModeStore

internal enum class FirstRunStep { GROUP, SUBGROUP, TEACHER }

internal enum class GroupChangeStep { GROUP, TEACHER }

internal val firstRunSubgroupOptions: List<Pair<SubgroupPref, String>> =
    listOf(
        SubgroupPref.FIRST to "Первая",
        SubgroupPref.SECOND to "Вторая",
        SubgroupPref.NEUTRAL to "Нейтрально",
    )

internal fun groupDialogMaxHeight(
    availableHeight: Dp,
    minGap: Dp = 12.dp,
    fraction: Float = 0.88f,
): Dp {
    if (availableHeight <= minGap * 2f) return 0.dp
    val byFraction = availableHeight * fraction
    val byGap = availableHeight - minGap * 2f
    return if (byFraction < byGap) byFraction else byGap
}

@Composable
fun FirstRunGroupDialog(
    onComplete: (group: String, subgroup: SubgroupPref) -> Unit,
    onTeacherSelected: (teacherName: String) -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(FirstRunStep.GROUP) }
    var chosenGroup by rememberSaveable { mutableStateOf("") }
    var selectedCourse by rememberSaveable { mutableIntStateOf(1) }
    var subgroup by rememberSaveable { mutableStateOf(SubgroupPref.NEUTRAL) }
    val catalogCodes = remember { GroupCatalog.load(context).all() }

    GroupPickerDialogFrame(
        testTag = "first_run_dialog",
        onDismissRequest = { },
        dismissOnBackPress = false,
        dismissOnClickOutside = false,
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                    Modifier
                        .size(32.dp)
                        .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text =
                        when (step) {
                            FirstRunStep.GROUP -> "Выбор группы"
                            FirstRunStep.SUBGROUP -> "Выбор подгруппы"
                            FirstRunStep.TEACHER -> "Выбор преподавателя"
                        },
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = ColorTextTitle,
                        ),
                    )
                    Text(
                        text =
                        when (step) {
                            FirstRunStep.GROUP -> "Шаг 1 из 2"
                            FirstRunStep.SUBGROUP -> "Шаг 2 из 2 · группа $chosenGroup"
                            FirstRunStep.TEACHER -> "Режим преподавателя · без группы"
                        },
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = ColorTextMuted,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        content = {
            when (step) {
                FirstRunStep.GROUP ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.politex.ui.screens.GroupSchedulePicker(
                                selectedCourse = selectedCourse,
                                onCourseSelected = { selectedCourse = it },
                                catalogCodes = catalogCodes,
                                confirmLabel = "ДАЛЕЕ",
                                note = "Можно потом изменить",
                                onGroupSelected = { group ->
                                    chosenGroup = group
                                    step = FirstRunStep.SUBGROUP
                                },
                            )
                        }
                        TeacherModeCard(
                            tag = "first_run_teacher_button",
                            onClick = { step = FirstRunStep.TEACHER },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                FirstRunStep.TEACHER ->
                    TeacherSearchStep(
                        searchTag = "first_run_teacher_search",
                        itemTag = "first_run_teacher_item",
                        backTag = "first_run_teacher_back",
                        onTeacherSelected = onTeacherSelected,
                        onBack = { step = FirstRunStep.GROUP },
                    )
                FirstRunStep.SUBGROUP ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "По подгруппе строятся маршруты и работают виджеты. Можно потом изменить.",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp,
                                    color = ColorTextMuted,
                                ),
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "ПОДГРУППА",
                                style =
                                androidx.compose.ui.text.TextStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = Tokens.SectionHeader,
                                    color = ColorTextMuted,
                                ),
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            val pairOptions =
                                firstRunSubgroupOptions.filter { it.first != SubgroupPref.NEUTRAL }
                            val neutralOption =
                                firstRunSubgroupOptions.first { it.first == SubgroupPref.NEUTRAL }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                pairOptions.forEach { (pref, label) ->
                                    FirstRunOptionChip(
                                        label = label,
                                        selected = subgroup == pref,
                                        tag = "first_run_subgroup_${pref.name.lowercase()}",
                                        modifier = Modifier.weight(1f),
                                        onClick = { subgroup = pref },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            FirstRunOptionChip(
                                label = neutralOption.second,
                                selected = subgroup == neutralOption.first,
                                tag = "first_run_subgroup_${neutralOption.first.name.lowercase()}",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { subgroup = neutralOption.first },
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            border = BorderStroke(1.dp, ColorBrandBlue),
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Tokens.ButtonHeight)
                                .bouncyClickable { onComplete(chosenGroup, subgroup) }
                                .testTag("first_run_done_button"),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 12.dp),
                            ) {
                                Text(
                                    text = "ГОТОВО",
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
            }
        },
    )
}

@Composable
internal fun TeacherModeCard(
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorSurfaceHighlight,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.ButtonHeight)
            .bouncyClickable(onClick = onClick)
            .testTag(tag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier =
                Modifier
                    .size(34.dp)
                    .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Я ПРЕПОДАВАТЕЛЬ",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ColorTextTitle,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Расписание преподавателя вместо группы",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        color = ColorTextMuted,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
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
internal fun TeacherSearchStep(
    searchTag: String,
    itemTag: String,
    backTag: String,
    onTeacherSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val teachers = rememberTeachers()
        var teacherQuery by rememberSaveable { mutableStateOf("") }
        val teacherResults =
            remember(teachers, teacherQuery) {
                TeacherOnboardingLogic.teacherSearch(teachers, teacherQuery)
            }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Начните вводить фамилию — покажем подходящих преподавателей.",
            style =
            androidx.compose.ui.text.TextStyle(
                fontSize = 13.sp,
                color = ColorTextMuted,
            ),
        )
        Spacer(modifier = Modifier.height(10.dp))
        SearchBar(
            value = teacherQuery,
            onValueChange = { teacherQuery = it },
            placeholder = "Фамилия, имя или предмет",
            testTag = searchTag,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            when {
                teachers.isEmpty() -> CenteredLoader()
                teacherResults.isEmpty() ->
                    Text(
                        text = "Никого не нашли по «${teacherQuery.trim()}»",
                        style =
                        androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            color = ColorTextMuted,
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                else ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(teacherResults) { teacher ->
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = ColorBgMain,
                                border = BorderStroke(1.dp, ColorBorderLight),
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Tokens.HitTarget)
                                    .bouncyClickable { onTeacherSelected(teacher.name) }
                                    .testTag(itemTag),
                            ) {
                                Column(
                                    modifier =
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = teacher.name,
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = ColorTextTitle,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val details =
                                        listOf(teacher.position, teacher.subjects)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" • ")
                                    if (details.isNotBlank()) {
                                        Text(
                                            text = details,
                                            style =
                                            androidx.compose.ui.text.TextStyle(
                                                fontSize = 11.sp,
                                                color = ColorTextMuted,
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = ColorBgMain,
            border = BorderStroke(1.dp, ColorBorderLight),
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.ButtonHeight)
                .bouncyClickable(onClick = onBack)
                .testTag(backTag),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                Text(
                    text = "НАЗАД",
                    style =
                    androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = ColorTextMuted,
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun FirstRunOptionChip(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style =
                androidx.compose.ui.text.TextStyle(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (selected) Color.White else ColorTextBody,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun GroupPickerDialogFrame(
    testTag: String,
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
        DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            decorFitsSystemWindows = false,
        ),
    ) {
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .testTag("${testTag}_window"),
        ) {
            BoxWithConstraints(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = imeBottom)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = ColorBgMain,
                    border = BorderStroke(1.dp, ColorBorderLight),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = groupDialogMaxHeight(maxHeight))
                        .testTag(testTag),
                ) {
                    Column(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Spacer(modifier = Modifier.height(14.dp))
                        header()
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) {
                            content()
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun GroupSelectionDialog(
    currentGroupName: String,
    onDismissRequest: () -> Unit,
    showClose: Boolean = true,
    onGroupSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(GroupChangeStep.GROUP) }
    var selectedCourse by remember {
        mutableIntStateOf(GroupParser.parse(currentGroupName)?.course ?: 1)
    }
    val catalogCodes = remember { GroupCatalog.load(context).all() }
    val teacherModeRevision = TeacherModeStore.revision.intValue
    val isTeacherMode =
        remember(context, teacherModeRevision) {
            TeacherModeStore.isTeacher(context)
        }

    GroupPickerDialogFrame(
        testTag = "group_selection_dialog",
        onDismissRequest = onDismissRequest,
        header = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                        Modifier
                            .size(32.dp)
                            .background(ColorBrandFill, RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text =
                            when (step) {
                                GroupChangeStep.GROUP -> "Выбор группы"
                                GroupChangeStep.TEACHER -> "Выбор преподавателя"
                            },
                            style =
                            androidx.compose.ui.text.TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ColorTextTitle,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        when (step) {
                            GroupChangeStep.GROUP ->
                                if (currentGroupName.isNotBlank()) {
                                    Text(
                                        text = "Сейчас: $currentGroupName",
                                        style =
                                        androidx.compose.ui.text.TextStyle(
                                            fontSize = 11.sp,
                                            color = ColorTextMuted,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            GroupChangeStep.TEACHER ->
                                Text(
                                    text = "Режим преподавателя · без группы",
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontSize = 11.sp,
                                        color = ColorTextMuted,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                        }
                    }
                }
                if (showClose) {
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(Tokens.HitTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = ColorTextMuted,
                        )
                    }
                }
            }
        },
        content = {
            when (step) {
                GroupChangeStep.GROUP ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            com.politex.ui.screens.GroupSchedulePicker(
                                selectedCourse = selectedCourse,
                                onCourseSelected = { selectedCourse = it },
                                catalogCodes = catalogCodes,
                                confirmLabel = "ВЫБРАТЬ",
                                onGroupSelected = onGroupSelected,
                            )
                        }
                        if (!isTeacherMode) {
                            TeacherModeCard(
                                tag = "group_selection_teacher_button",
                                onClick = { step = GroupChangeStep.TEACHER },
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                GroupChangeStep.TEACHER ->
                    TeacherSearchStep(
                        searchTag = "group_selection_teacher_search",
                        itemTag = "group_selection_teacher_item",
                        backTag = "group_selection_teacher_back",
                        onTeacherSelected = { name ->
                            TeacherModeStore.setTeacher(context, name)
                            onDismissRequest()
                        },
                        onBack = { step = GroupChangeStep.GROUP },
                    )
            }
        },
    )
}
