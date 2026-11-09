package com.politex

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.TaskType
import com.politex.ui.screens.ScheduleLessonSlot
import com.politex.ui.screens.ScheduleLessonSlotCard
import com.politex.ui.screens.mergedLessonSlots
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.Dow
import com.politex.util.HomeworkMarkers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class ScheduleHomeworkBadgeRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val anchorDay: Calendar =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 2, 0, 0, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

    private val dow: Int = Dow.of(anchorDay)
    private val dayIso: String = HomeworkMarkers.dayIsoOf(anchorDay)

    private fun at(
        dayOffset: Int,
        hour: Int,
    ): Long =
        (anchorDay.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis

    private fun lesson(
        number: Int,
        start: String,
        end: String,
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = dow,
        lessonNumber = number,
        timeStart = start,
        timeEnd = end,
        subjectRaw = "Математика",
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = "",
    )

    private val pairOne = lesson(1, "08:15", "09:55")
    private val pairTwo = lesson(2, "09:55", "11:30")
    private val lessons = listOf(pairOne, pairTwo)
    private val weekly = mapOf(dow to lessons)

    private fun task(isCompleted: Boolean = false) = StudentTaskEntity(
        groupName = "41О",
        course = 4,
        subjectName = "Математика",
        taskType = TaskType.HOMEWORK.displayName,
        title = "ДЗ",
        isCompleted = isCompleted,
        createdAt = at(0, 8),
    )

    private fun day(
        isToday: Boolean = false,
        isPast: Boolean = false,
    ) = HomeworkMarkers.DayContext(dayIso, isToday, isPast)

    private fun badgesFor(dayContext: HomeworkMarkers.DayContext, task: StudentTaskEntity, nowMillis: Long) =
        HomeworkMarkers.markerLessonTypes(
            dayLessons = lessons,
            day = dayContext,
            nowMillis = nowMillis,
            weeklyLessonsByDow = weekly,
            pendingTasks = listOf(task),
        )

    private fun render(homeworkByLesson: Map<Int, List<TaskType>>) {
        composeRule.setContent {
            MyApplicationTheme {
                Column {
                    mergedLessonSlots(lessons, teacherMode = false).forEach { slot ->
                        ScheduleLessonSlotCard(
                            slot = slot,
                            currentLessonNumber = null,
                            homeworkByLesson = homeworkByLesson,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `сдвоенная пара одного предмета — бейдж на обоих уроках`() {
        val badges = badgesFor(day(), task(), at(0, 12))
        assertEquals(setOf(1, 2), badges.keys)

        render(badges)

        composeRule.onNodeWithTag("homework_badge_1").assertIsDisplayed()
        composeRule.onNodeWithTag("homework_badge_2").assertIsDisplayed()
    }

    @Test
    fun `выполненное задание — бейджей в карточках нет`() {
        val badges = badgesFor(day(), task(isCompleted = true), at(0, 12))
        assertTrue(badges.isEmpty())

        render(badges)

        composeRule.onNodeWithTag("homework_badge_1").assertDoesNotExist()
        composeRule.onNodeWithTag("homework_badge_2").assertDoesNotExist()
    }

    @Test
    fun `вчерашний день — бейджей нет ни по флагу, ни календарно`() {
        val byFlag = badgesFor(day(isPast = true), task(), at(0, 12))
        assertTrue(byFlag.isEmpty())

        val byCalendar = badgesFor(day(), task(), at(1, 12))
        assertTrue(byCalendar.isEmpty())

        render(byFlag)
        composeRule.onNodeWithTag("homework_badge_1").assertDoesNotExist()
        composeRule.onNodeWithTag("homework_badge_2").assertDoesNotExist()
    }

    @Test
    fun `merged-прогон не теряет бейджи`() {
        val slots = mergedLessonSlots(lessons, teacherMode = false)
        assertTrue("уроки 1 и 2 одного предмета должны склеиться в merged-слот", slots.any { it is ScheduleLessonSlot.Merged })

        val badges = badgesFor(day(), task(), at(0, 12))
        render(badges)

        val mergedLessons =
            slots
                .filterIsInstance<ScheduleLessonSlot.Merged>()
                .flatMap { it.lessons }
                .map { it.lessonNumber }
        assertEquals(listOf(1, 2), mergedLessons)
        mergedLessons.forEach { number ->
            composeRule.onNodeWithTag("homework_badge_$number").assertIsDisplayed()
        }
    }
}