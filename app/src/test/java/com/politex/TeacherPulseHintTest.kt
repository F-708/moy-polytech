package com.politex

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.politex.data.local.entity.LessonEntity
import com.politex.ui.screens.PulseActive
import com.politex.ui.screens.ScheduleLessonSlot
import com.politex.ui.screens.ScheduleLessonSlotCard
import com.politex.ui.screens.pulseTargetLesson
import com.politex.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class TeacherPulseHintTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun teacherLesson() =
        LessonEntity(
            groupName = "41О",
            dayOfWeek = 1,
            lessonNumber = 1,
            timeStart = "08:15",
            timeEnd = "09:55",
            subjectRaw = "Математика",
            roomFirst = "204",
            teacherFirst = "",
            dateString = "",
        )

    private fun studentLesson() =
        LessonEntity(
            groupName = "41О",
            dayOfWeek = 1,
            lessonNumber = 1,
            timeStart = "08:15",
            timeEnd = "09:55",
            subjectRaw = "Математика",
            roomFirst = "204",
            teacherFirst = "Ковалев В.П.",
            dateString = "",
        )

    private fun renderSlot(
        teacherMode: Boolean,
        lesson: LessonEntity,
    ) {
        composeRule.setContent {
            MyApplicationTheme {
                ScheduleLessonSlotCard(
                    slot = ScheduleLessonSlot.Single(lesson),
                    currentLessonNumber = null,
                    teacherMode = teacherMode,
                    pulseLessonNumber = pulseTargetLesson(listOf(lesson), teacherMode),
                    showPulseHint = true,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("lesson_card_1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `teacher-режим - чип группы виден, но не пульсирует`() {
        renderSlot(teacherMode = true, lesson = teacherLesson())

        composeRule
            .onNodeWithTag("group_chip", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(PulseActive))
    }

    @Test
    fun `teacher-режим - подсказка остаётся на кликабельном кабинете`() {
        renderSlot(teacherMode = true, lesson = teacherLesson())

        composeRule
            .onNodeWithTag("room_chip", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(PulseActive, true))
    }

    @Test
    fun `студенческий режим - прежнее поведение, кабинет и преподаватель пульсируют, группы нет`() {
        renderSlot(teacherMode = false, lesson = studentLesson())

        composeRule
            .onNodeWithTag("room_chip", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(PulseActive, true))
        composeRule
            .onNodeWithTag("teacher_chip", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(PulseActive, true))
        composeRule.onAllNodesWithTag("group_chip").assertCountEquals(0)
    }

    @Test
    fun `подсказка пройдена - пульса нет ни на чём`() {
        val lesson = teacherLesson()
        composeRule.setContent {
            MyApplicationTheme {
                ScheduleLessonSlotCard(
                    slot = ScheduleLessonSlot.Single(lesson),
                    currentLessonNumber = null,
                    teacherMode = true,
                    pulseLessonNumber = pulseTargetLesson(listOf(lesson), true),
                    showPulseHint = false,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("lesson_card_1").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule
            .onNodeWithTag("room_chip", useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(PulseActive))
    }
}
