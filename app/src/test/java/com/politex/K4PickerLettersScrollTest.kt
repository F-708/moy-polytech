package com.politex

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.politex.ui.screens.GroupSchedulePicker
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.GroupCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class K4PickerLettersScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val manyLettersCatalog =
        listOf("11А", "11Б", "11В", "11Г", "11Д", "11Е", "11Ж", "11О")
    private val fewLettersCatalog = listOf("41О", "41Д")
    private val group41O = "41О"

    private val assetCatalog: List<String> =
        sequenceOf(
            File("src/main/assets/group_codes.csv"),
            File("app/src/main/assets/group_codes.csv"),
        ).first { it.exists() }
            .readLines()
            .let { GroupCatalog.parse(it).all() }

    private fun setPicker(
        catalog: List<String>,
        course: Int,
        onGroupSelected: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                GroupSchedulePicker(
                    selectedCourse = course,
                    onCourseSelected = {},
                    catalogCodes = catalog,
                    onGroupSelected = onGroupSelected,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `длинный ряд букв переносится и все буквы доступны`() {
        setPicker(manyLettersCatalog, 1)
        composeRule.onNodeWithTag("group_picker_letters_row").assertExists()
        listOf('А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ж', 'О').forEach { letter ->
            composeRule.onNodeWithTag("group_picker_letter_$letter").assertExists()
        }
    }

    @Test
    fun `стрелок и градиентов горизонтального скролла больше нет`() {
        setPicker(manyLettersCatalog, 1)
        composeRule.onNodeWithTag("group_picker_letters_more_start").assertDoesNotExist()
        composeRule.onNodeWithTag("group_picker_letters_more_end").assertDoesNotExist()
    }

    @Test
    fun `мало букв - все буквы видны без индикаторов`() {
        setPicker(fewLettersCatalog, 4)
        composeRule.onNodeWithTag("group_picker_letter_О").assertExists()
        composeRule.onNodeWithTag("group_picker_letter_Д").assertExists()
        composeRule.onNodeWithTag("group_picker_letters_more_end").assertDoesNotExist()
        composeRule.onNodeWithTag("group_picker_letters_more_start").assertDoesNotExist()
    }

    @Test
    fun `41О - кнопка подтверждения всегда видима и активна при валидном коде`() {
        setPicker(assetCatalog, 4)
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertExists()
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("group_picker_number_1").performClick()
        composeRule.onNodeWithTag("group_picker_letter_${group41O[2]}").performClick()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains(group41O)
        composeRule.onNodeWithTag("group_picker_valid_icon").assertExists()
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertHasClickAction()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h480dp")
    fun `41О - кнопка видна даже на низком экране`() {
        setPicker(assetCatalog, 4)
        composeRule.onNodeWithTag("group_picker_number_1").performClick()
        composeRule.onNodeWithTag("group_picker_letter_${group41O[2]}").performClick()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains(group41O)
        composeRule
            .onNodeWithTag("group_picker_open_button")
            .assertExists()
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun `выбор 41О отдаёт код группы`() {
        var picked = ""
        setPicker(assetCatalog, 4) { picked = it }
        composeRule.onNodeWithTag("group_picker_number_1").performClick()
        composeRule.onNodeWithTag("group_picker_letter_${group41O[2]}").performClick()
        composeRule.onNodeWithTag("group_picker_open_button").performClick()
        assertEquals(group41O, picked)
    }

    @Test
    fun `выбор номера и буквы не сломан - код собирается и валидируется`() {
        setPicker(manyLettersCatalog, 1)
        composeRule.onNodeWithTag("group_picker_number_1").performClick()
        composeRule.onNodeWithTag("group_picker_letter_А").performClick()
        composeRule.onNodeWithTag("group_picker_code").assertTextContains("11А")
        composeRule.onNodeWithTag("group_picker_valid_icon").assertExists()
    }
}