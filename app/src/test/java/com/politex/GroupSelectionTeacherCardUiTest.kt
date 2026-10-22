package com.politex

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.politex.ui.components.GroupSelectionDialog
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.TeacherModeStore
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class GroupSelectionTeacherCardUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        TeacherModeStore.clear(context)
    }

    @After
    fun tearDown() {
        TeacherModeStore.clear(context)
    }

    private fun setDialog(
        onDismissRequest: () -> Unit = {},
        onGroupSelected: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                GroupSelectionDialog(
                    currentGroupName = "41О",
                    onDismissRequest = onDismissRequest,
                    onGroupSelected = onGroupSelected,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun openTeacherStep() {
        composeRule.onNodeWithTag("group_selection_teacher_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_selection_teacher_search").assertIsDisplayed()
    }

    @Test
    fun `смена группы - карточка преподавателя видна под кнопкой ВЫБРАТЬ`() {
        setDialog()
        composeRule.onNodeWithTag("group_selection_teacher_button").assertIsDisplayed()
        val button = composeRule.onNodeWithTag("group_picker_open_button").getBoundsInRoot()
        val card = composeRule.onNodeWithTag("group_selection_teacher_button").getBoundsInRoot()
        assertTrue("карточка не под кнопкой ВЫБРАТЬ: ${card.top} < ${button.bottom}", card.top >= button.bottom)
    }

    @Test
    fun `смена группы - тап по карточке открывает шаг преподавателя с поиском`() {
        setDialog()
        openTeacherStep()
        composeRule.onNodeWithTag("group_selection_teacher_back").assertIsDisplayed()
    }

    @Test
    fun `смена группы - назад с шага преподавателя возвращает к списку групп`() {
        setDialog()
        openTeacherStep()
        composeRule.onNodeWithTag("group_selection_teacher_back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
        composeRule.onNodeWithTag("group_selection_teacher_button").assertIsDisplayed()
        composeRule.onNodeWithTag("group_selection_teacher_search").assertDoesNotExist()
    }

    @Test
    fun `смена группы - выбор преподавателя включает режим и закрывает диалог без смены группы`() {
        var dialogVisible by mutableStateOf(true)
        var chosenGroup: String? = null
        composeRule.setContent {
            MyApplicationTheme {
                if (dialogVisible) {
                    GroupSelectionDialog(
                        currentGroupName = "41О",
                        onDismissRequest = { dialogVisible = false },
                        onGroupSelected = { chosenGroup = it },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("group_selection_teacher_button").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithTag("group_selection_teacher_item").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("group_selection_teacher_item")[0].performClick()
        composeRule.waitForIdle()
        assertTrue(TeacherModeStore.isTeacher(context))
        assertTrue((TeacherModeStore.teacherName(context) ?: "").isNotBlank())
        composeRule.onNodeWithTag("group_selection_dialog").assertDoesNotExist()
        assertNull("группа не должна меняться при выборе преподавателя", chosenGroup)
    }

    @Test
    fun `смена группы - в режиме преподавателя карточки нет`() {
        TeacherModeStore.setTeacher(context, "Иванова Анна Петровна")
        setDialog()
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
        composeRule.onNodeWithTag("group_selection_teacher_button").assertDoesNotExist()
    }
}
