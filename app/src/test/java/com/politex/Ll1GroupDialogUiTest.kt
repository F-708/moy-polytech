package com.politex

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.politex.ui.components.FirstRunGroupDialog
import com.politex.ui.components.GroupSelectionDialog
import com.politex.ui.components.groupDialogMaxHeight
import com.politex.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private val group41O = "41О"

private fun assertDp(expected: Dp, actual: Dp) {
    assertEquals(expected.value, actual.value, 0.05f)
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.pick41O() {
    onNodeWithTag("group_schedule_course_4").performClick()
    onNodeWithTag("group_picker_number_1").performScrollTo().performClick()
    onNodeWithTag("group_picker_letter_${group41O[2]}").performScrollTo().performClick()
    waitForIdle()
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.assertVerticalGaps(tag: String) {
    val root = onNodeWithTag("${tag}_window").getBoundsInRoot()
    val dialog = onNodeWithTag(tag).getBoundsInRoot()
    val topGap = dialog.top - root.top
    val bottomGap = root.bottom - dialog.bottom
    assertTrue("окно $tag без зазора сверху: $topGap", topGap > 0.dp)
    assertTrue("окно $tag без зазора снизу: $bottomGap", bottomGap > 0.dp)
    assertTrue("зазор сверху меньше 12dp: $topGap", topGap >= 12.dp)
    assertTrue("зазор снизу меньше 12dp: $bottomGap", bottomGap >= 12.dp)
    assertTrue(
        "зазоры не равны: $topGap сверху и $bottomGap снизу",
        abs(topGap.value - bottomGap.value) <= 2f,
    )
    assertTrue("окно $tag шире экрана", dialog.left >= root.left && dialog.right <= root.right)
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setFirstRun() {
    setContent {
        MyApplicationTheme {
            FirstRunGroupDialog(
                onComplete = { _, _ -> },
                onTeacherSelected = {},
            )
        }
    }
    waitForIdle()
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.setGroupChange() {
    setContent {
        MyApplicationTheme {
            GroupSelectionDialog(
                currentGroupName = group41O,
                onDismissRequest = {},
                onGroupSelected = {},
            )
        }
    }
    waitForIdle()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h480dp")
class Ll1GroupDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `онбординг - 41О и кнопка ДАЛЕЕ видима и активна на низком экране`() {
        composeRule.setFirstRun()
        composeRule.onNodeWithTag("first_run_dialog").assertExists()
        composeRule.pick41O()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains(group41O)
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("ДАЛЕЕ")
    }

    @Test
    fun `онбординг - кнопка вне скролла и остаётся видимой после прокрутки букв`() {
        composeRule.setFirstRun()
        composeRule.pick41O()
        composeRule.onNodeWithTag("group_picker_preview").performScrollTo()
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun `смена группы - кнопка ВЫБРАТЬ видима и активна на низком экране`() {
        composeRule.setGroupChange()
        composeRule.onNodeWithTag("group_selection_dialog").assertExists()
        composeRule.pick41O()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains(group41O)
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("ВЫБРАТЬ")
    }

    @Test
    fun `онбординг - на низком экране окно не прижато к краям и не обрезано`() {
        composeRule.setFirstRun()
        composeRule.assertVerticalGaps("first_run_dialog")
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
        composeRule.onNodeWithTag("first_run_teacher_button").assertIsDisplayed()
    }

    @Test
    fun `смена группы - на низком экране окно не прижато к краям и не обрезано`() {
        composeRule.setGroupChange()
        composeRule.assertVerticalGaps("group_selection_dialog")
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
    }

    @Test
    fun `онбординг - после прокрутки букв кнопка и карточка преподавателя видны`() {
        composeRule.setFirstRun()
        composeRule.pick41O()
        composeRule.onNodeWithTag("group_picker_letters_row").performScrollTo()
        composeRule.assertVerticalGaps("first_run_dialog")
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
        composeRule.onNodeWithTag("first_run_teacher_button").assertIsDisplayed()
    }

    @Test
    fun `подгруппа - кнопка ГОТОВО видна вне скролла на низком экране`() {
        composeRule.setFirstRun()
        composeRule.pick41O()
        composeRule.onNodeWithTag("group_picker_open_button").performClick()
        composeRule.onNodeWithTag("first_run_done_button").assertIsDisplayed()
        composeRule.assertVerticalGaps("first_run_dialog")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class Ll1GroupDialogTallUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `онбординг - на высоком экране окно центрировано и не растянуто на весь экран`() {
        composeRule.setFirstRun()
        composeRule.assertVerticalGaps("first_run_dialog")
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
        composeRule.onNodeWithTag("first_run_teacher_button").assertIsDisplayed()
    }

    @Test
    fun `смена группы - на высоком экране окно центрировано и кнопка видна`() {
        composeRule.setGroupChange()
        composeRule.assertVerticalGaps("group_selection_dialog")
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
    }

    @Test
    fun `онбординг - 41О и кнопка ДАЛЕЕ видны на высоком экране`() {
        composeRule.setFirstRun()
        composeRule.pick41O()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains(group41O)
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("ДАЛЕЕ")
    }

    @Test
    fun `онбординг - после прокрутки букв кнопка остаётся видимой на высоком экране`() {
        composeRule.setFirstRun()
        composeRule.pick41O()
        composeRule.onNodeWithTag("group_picker_preview").performScrollTo()
        composeRule.assertVerticalGaps("first_run_dialog")
        composeRule.onNodeWithTag("group_picker_open_button").assertIsDisplayed()
    }
}

class Ll2GroupDialogInsetsTest {

    @Test
    fun `экран 480dp - окно 422dp и равные зазоры по 29dp`() {
        val window = 480.dp
        val dialog = groupDialogMaxHeight(window)
        assertDp(422.4.dp, dialog)
        assertDp(28.8.dp, (window - dialog) / 2f)
    }

    @Test
    fun `экран 800dp - окно 704dp и равные зазоры по 48dp`() {
        val window = 800.dp
        val dialog = groupDialogMaxHeight(window)
        assertDp(704.dp, dialog)
        assertDp(48.dp, (window - dialog) / 2f)
    }

    @Test
    fun `нулевые insets не дают нулевого зазора`() {
        val window = 480.dp
        val dialog = groupDialogMaxHeight(window)
        assertTrue(dialog > 0.dp)
        assertTrue((window - dialog) / 2f >= 12.dp)
    }

    @Test
    fun `клавиатура уменьшает окно и добавляет отступ снизу`() {
        val screen = 800.dp
        val ime = 300.dp
        val available = screen - ime
        val dialog = groupDialogMaxHeight(available)
        assertDp(440.dp, dialog)
        val topGap = (available - dialog) / 2f
        assertDp(30.dp, topGap)
        assertTrue("низ должен уйти выше клавиатуры", topGap + ime > ime)
        assertTrue("окно не должно раздуваться под клавиатуру", dialog < screen)
    }

    @Test
    fun `на маленькой высоте гарантирован зазор 12dp`() {
        val available = 200.dp
        val dialog = groupDialogMaxHeight(available)
        assertDp(176.dp, dialog)
        assertDp(12.dp, (available - dialog) / 2f)
    }

    @Test
    fun `высота окна не превышает 90 процентов`() {
        listOf(480.dp, 800.dp, 240.dp, 600.dp).forEach { window ->
            assertTrue("$window", groupDialogMaxHeight(window) <= window * 0.9f)
        }
    }

    @Test
    fun `нулевая и микроскопическая высота не дают отрицательного окна`() {
        assertDp(0.dp, groupDialogMaxHeight(0.dp))
        assertDp(0.dp, groupDialogMaxHeight(20.dp))
    }
}
