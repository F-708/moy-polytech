package com.politex

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.TaskRepository
import com.politex.ui.screens.BellsScreen
import com.politex.ui.screens.CollegeScreen
import com.politex.ui.screens.ScheduleScreen
import com.politex.ui.screens.SettingsScreen
import com.politex.ui.screens.TasksScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.TextStylePageTitle
import com.politex.util.GroupParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class UiRenderSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val group = GroupParser.parse("41О")!!

    @Test
    fun `светлая тема единственная и рендерится`() {
        composeRule.setContent {
            MyApplicationTheme {
                Text(text = "Мой Политех", style = TextStylePageTitle)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран расписания рендерится`() {
        val repo = ScheduleRepository(MpkDatabase.getInstance(context).lessonDao())
        composeRule.setContent {
            MyApplicationTheme {
                ScheduleScreen(groupInfo = group, scheduleRepository = repo, onSyncRequest = {})
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран звонков рендерится`() {
        val repo = ScheduleRepository(MpkDatabase.getInstance(context).lessonDao())
        composeRule.setContent {
            MyApplicationTheme {
                BellsScreen(groupInfo = group, scheduleRepository = repo)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран другого рендерится`() {

        composeRule.setContent {
            MyApplicationTheme {
                CollegeScreen(groupInfo = group)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран заданий рендерится`() {
        val repo = TaskRepository(MpkDatabase.getInstance(context).taskDao(), MpkDatabase.getInstance(context).taskReminderDao())
        composeRule.setContent {
            MyApplicationTheme {
                TasksScreen(groupInfo = group, taskRepository = repo)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `экран настроек рендерится`() {
        composeRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    groupInfo = group,
                    onGroupChanged = {},
                )
            }
        }
        composeRule.waitForIdle()
    }
}
