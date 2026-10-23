package com.politex

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.repository.ScheduleRepository
import com.politex.ui.screens.CollegeScreen
import com.politex.ui.screens.ScheduleScreen
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.DateFormats
import com.politex.util.GroupParser
import com.politex.util.TeacherModeStore
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class TeacherModeComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val group = GroupParser.parse("41О")!!

    @After
    fun tearDown() {
        TeacherModeStore.clear(context)
    }

    private fun todayLesson(): LessonEntity = LessonEntity(
        groupName = "41О",
        dayOfWeek = 1,
        lessonNumber = 1,
        timeStart = "09:00",
        timeEnd = "09:45",
        subjectRaw = "Математика",
        roomFirst = "214",
        teacherFirst = "Иванова А.П.",
        dateString = DateFormats.dotDate(Calendar.getInstance()),
    )

    private fun renderSchedule(lesson: LessonEntity) {
        val repo = ScheduleRepository(MpkDatabase.getInstance(context).lessonDao())
        composeRule.setContent {
            MyApplicationTheme {
                ScheduleScreen(
                    groupInfo = group,
                    scheduleRepository = repo,
                    onSyncRequest = {},
                    groupLessonsFromVm = listOf(lesson),
                    pendingTasksFromVm = emptyList(),
                    selectedDateString = lesson.dateString,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `расписание в режиме преподавателя показывает группу вместо преподавателя`() {
        TeacherModeStore.setTeacher(context, "Иванова Анна Петровна")
        renderSchedule(todayLesson())
        composeRule.onNodeWithText("41О").assertIsDisplayed()
        composeRule.onNodeWithText("Иванова А.П.").assertDoesNotExist()
    }

    @Test
    fun `расписание в студенческом режиме показывает преподавателя и не показывает группу`() {
        TeacherModeStore.clear(context)
        renderSchedule(todayLesson())
        composeRule.onNodeWithText("Иванова А.П.").assertIsDisplayed()
        composeRule.onNodeWithText("41О").assertDoesNotExist()
    }

    @Test
    fun `меню колледжа в режиме преподавателя скрывает Мою специальность`() {
        TeacherModeStore.setTeacher(context, "Иванова Анна Петровна")
        composeRule.setContent {
            MyApplicationTheme {
                CollegeScreen(groupInfo = group)
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Моя специальность").assertCountEquals(0)
        composeRule.onNodeWithText("Преподаватели").assertIsDisplayed()
    }

    @Test
    fun `меню колледжа в студенческом режиме показывает Мою специальность`() {
        TeacherModeStore.clear(context)
        composeRule.setContent {
            MyApplicationTheme {
                CollegeScreen(groupInfo = group)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Моя специальность").assertIsDisplayed()
    }

    @Test
    fun `настройки в режиме преподавателя скрывают подгруппу и показывают выход из режима`() {
        TeacherModeStore.setTeacher(context, "Иванова Анна Петровна")
        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(groupInfo = group, onGroupChanged = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("subgroup_pref_card").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("teacher_mode_exit_card"))
        composeRule.onNodeWithTag("teacher_mode_exit_card").assertIsDisplayed()
        composeRule.onNodeWithText("СМЕНИТЬ РОЛЬ").assertIsDisplayed()
        composeRule.onNodeWithText("Иванова Анна Петровна", substring = true).assertIsDisplayed()
    }

    @Test
    fun `настройки в студенческом режиме показывают подгруппу и не показывают выход из режима`() {
        TeacherModeStore.clear(context)
        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(groupInfo = group, onGroupChanged = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("subgroup_pref_card"))
        composeRule.onNodeWithTag("subgroup_pref_card").assertIsDisplayed()
        composeRule.onAllNodesWithTag("teacher_mode_exit_card").assertCountEquals(0)
    }
}
