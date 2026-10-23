package com.politex.ui.teacher

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.AppGraph
import com.politex.data.model.GroupInfo
import com.politex.ui.screens.BellsScreen
import com.politex.ui.screens.CollegeMapScreen
import com.politex.ui.screens.GroupScheduleRoute
import com.politex.ui.screens.TeacherCardRoute
import com.politex.ui.screens.TeachersRoute
import com.politex.ui.screens.pulseHalo
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorNavSelected
import com.politex.ui.theme.ColorSurfaceHighlight
import com.politex.ui.theme.ColorTextMuted
import com.politex.update.UpdateInstallController

enum class TeacherTab { SCHEDULE, BELLS, MORE }

object TeacherNavLogic {
    const val EXTRA_OPEN_TAB = "EXTRA_OPEN_TAB"

    fun tabForExtra(raw: String?): TeacherTab = when (raw?.trim()?.lowercase()) {
        "bells", "week" -> TeacherTab.BELLS
        "more" -> TeacherTab.MORE
        else -> TeacherTab.SCHEDULE
    }
}

private sealed interface TeacherSubPage {
    data class Map(val room: String?) : TeacherSubPage

    object Bells : TeacherSubPage

    data class Group(val name: String) : TeacherSubPage

    data class FindGroup(val group: String) : TeacherSubPage

    object Teachers : TeacherSubPage

    data class TeacherCard(val name: String) : TeacherSubPage

    object Settings : TeacherSubPage
}

private val bellsStubGroup = GroupInfo(rawName = "", course = 0, groupNumber = 0, specialtyCode = 'О')

@Composable
fun TeacherNav(
    profile: TeacherProfile?,
    onPickProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(TeacherTab.SCHEDULE) }

    var subPage by remember { mutableStateOf<TeacherSubPage?>(null) }
    val updatePending by UpdateInstallController.foundUpdate.collectAsState()
    LaunchedEffect(Unit) { UpdateInstallController.restore(context) }

    LaunchedEffect(Unit) {
        val intent = (context as? Activity)?.intent ?: return@LaunchedEffect
        if (intent.hasExtra(TeacherNavLogic.EXTRA_OPEN_TAB)) {
            tab = TeacherNavLogic.tabForExtra(intent.getStringExtra(TeacherNavLogic.EXTRA_OPEN_TAB))
        }
    }

    BackHandler(enabled = subPage != null || tab != TeacherTab.SCHEDULE) {
        if (subPage != null) {
            subPage = null
        } else {
            tab = TeacherTab.SCHEDULE
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ColorBgMain,
        bottomBar = {
            NavigationBar(
                containerColor = ColorBgMain,
                tonalElevation = 0.dp,
                modifier = Modifier.testTag("teacher_bottom_nav").heightIn(min = 72.dp),
            ) {
                val tabs =
                    listOf(
                        TabSpec(TeacherTab.SCHEDULE, "Расписание", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
                        TabSpec(TeacherTab.BELLS, "График", Icons.Filled.AccessTime, Icons.Outlined.AccessTime),
                        TabSpec(TeacherTab.MORE, "Ещё", Icons.Filled.Apps, Icons.Outlined.Apps),
                    )
                tabs.forEach { spec ->
                    val selected = tab == spec.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            subPage = null
                            tab = spec.tab
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) spec.filled else spec.outlined,
                                contentDescription = spec.title,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = {
                            Text(
                                text = spec.title,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.sp,
                                ),
                            )
                        },
                        colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = ColorNavSelected,
                            selectedTextColor = ColorNavSelected,
                            indicatorColor = ColorSurfaceHighlight,
                            unselectedIconColor = ColorTextMuted,
                            unselectedTextColor = ColorTextMuted,
                        ),
                        modifier = Modifier
                            .testTag("teacher_tab_${spec.tab.name.lowercase()}")
                            .pulseHalo(spec.tab == TeacherTab.MORE && updatePending != null),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(ColorBgMain)) {
            when (val sub = subPage) {
                is TeacherSubPage.Map ->
                    CollegeMapScreen(
                        initialRoom = sub.room,
                        onBack = { subPage = null },
                    )

                TeacherSubPage.Bells -> TeacherBellsPage(onBack = { subPage = null })
                is TeacherSubPage.Group ->
                    TeacherGroupLessonsScreen(
                        group = sub.name,
                        profile = profile,
                        onBack = { subPage = null },
                    )

                is TeacherSubPage.FindGroup ->
                    GroupScheduleRoute(
                        group = sub.group,
                        groupInfo = bellsStubGroup,
                        scheduleRepository = AppGraph.scheduleRepository,
                        onGroupPicked = { g -> subPage = TeacherSubPage.FindGroup(g) },
                        onChangeGroup = { subPage = TeacherSubPage.FindGroup("") },
                        onTeacherClick = { name -> subPage = TeacherSubPage.TeacherCard(name) },
                        onRoomClick = { room, _ -> subPage = TeacherSubPage.Map(room) },
                    )

                TeacherSubPage.Teachers ->
                    TeachersRoute(
                        groupInfo = bellsStubGroup,
                        scheduleRepository = AppGraph.scheduleRepository,
                        onOpenTeacher = { teacher -> subPage = TeacherSubPage.TeacherCard(teacher.name) },
                        onBack = { subPage = null },
                        showLeads = false,
                    )

                is TeacherSubPage.TeacherCard ->
                    TeacherCardRoute(
                        teacherName = sub.name,
                        groupInfo = bellsStubGroup,
                        scheduleRepository = AppGraph.scheduleRepository,
                        onOpenRoom = { room -> subPage = TeacherSubPage.Map(room) },
                        onNotFound = { subPage = null },
                        onBack = { subPage = TeacherSubPage.Teachers },
                        showLeads = false,
                    )

                TeacherSubPage.Settings ->
                    TeacherSettingsScreen(
                        profile = profile,
                        onPickProfile = onPickProfile,
                        onBack = { subPage = null },
                    )

                null ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        when (tab) {
                            TeacherTab.SCHEDULE ->
                                TeacherHomeScreen(
                                    profile = profile,
                                    onPickProfile = onPickProfile,
                                    onRoomClick = { room -> subPage = TeacherSubPage.Map(room) },
                                    onGroupClick = { group -> subPage = TeacherSubPage.Group(group) },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )

                            TeacherTab.BELLS ->
                                BellsScreen(
                                    groupInfo = bellsStubGroup,
                                    scheduleRepository = AppGraph.scheduleRepository,
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )

                            TeacherTab.MORE ->
                                TeacherMoreScreen(
                                    profile = profile,
                                    onPickProfile = onPickProfile,
                                    onOpenBells = { subPage = TeacherSubPage.Bells },
                                    onOpenMap = { subPage = TeacherSubPage.Map(null) },
                                    onOpenGroupSchedule = { subPage = TeacherSubPage.FindGroup("") },
                                    onOpenTeachers = { subPage = TeacherSubPage.Teachers },
                                    onOpenSettings = { subPage = TeacherSubPage.Settings },
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                )
                        }
                    }
            }
        }
    }
}

private data class TabSpec(
    val tab: TeacherTab,
    val title: String,
    val filled: ImageVector,
    val outlined: ImageVector,
)
