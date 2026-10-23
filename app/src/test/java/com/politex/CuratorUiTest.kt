package com.politex

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.repository.ScheduleRepository
import com.politex.data.repository.Student
import com.politex.ui.screens.GroupScheduleScreen
import com.politex.ui.screens.StudentCardScreen
import com.politex.ui.screens.resolveCardCurator
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.CuratorStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class CuratorUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MpkDatabase

    @Before
    fun setUp() {
        db = Room
            .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        CuratorStore.clearAll(context)
    }

    @After
    fun tearDown() {
        CuratorStore.clearAll(context)
        db.close()
    }

    private fun seedLessons(group: String) {
        runBlocking {
            db.lessonDao().clearLessonsForGroup(group)
            db.lessonDao().insertLessons(
                (1..6).map { dow ->
                    LessonEntity(
                        groupName = group,
                        dayOfWeek = dow,
                        lessonNumber = 1,
                        timeStart = "08:15",
                        timeEnd = "09:55",
                        subjectRaw = "Математика",
                        roomFirst = "204",
                        teacherFirst = "Ковалев В.П.",
                        dateString = "",
                    )
                },
            )
        }
    }

    private fun renderGroupScreen(group: String) {
        val repo = ScheduleRepository(db.lessonDao())
        composeRule.setContent {
            MyApplicationTheme {
                GroupScheduleScreen(
                    scheduleRepository = repo,
                    onBack = {},
                    selectedGroupName = group,
                    ownGroupName = group,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("group_schedule_caption").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun student(
        group: String,
        curator: String,
    ) = Student(
        fullName = "Тестовый Тест Тестович",
        group = group,
        course = "4",
        specialtyCode = group.last().toString(),
        specialty = "Тестовая специальность",
        funding = "бюджет",
        dormitory = "",
        curator = curator,
        room = "",
    )

    private fun renderStudentCard(student: Student) {
        seedLessons(student.group)
        val repo = ScheduleRepository(db.lessonDao())
        composeRule.setContent {
            MyApplicationTheme {
                StudentCardScreen(
                    student = student,
                    scheduleRepository = repo,
                    onBack = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("СПЕЦИАЛЬНОСТЬ").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `шапка расписания группы показывает куратора когда store знает группу`() {
        seedLessons("41О")
        CuratorStore.record(mapOf("41О" to "Иванова А.Б."))
        renderGroupScreen("41О")
        composeRule.onNodeWithTag("group_schedule_curator_line").assertIsDisplayed()
        composeRule.onNodeWithText("Куратор: Иванова А.Б.").assertIsDisplayed()
    }

    @Test
    fun `шапка расписания группы без строки куратора когда store не знает группу`() {
        seedLessons("42Э")
        renderGroupScreen("42Э")
        composeRule.onNodeWithTag("group_schedule_caption").assertIsDisplayed()
        composeRule.onAllNodesWithTag("group_schedule_curator_line").assertCountEquals(0)
    }

    @Test
    fun `карточка студента — куратор из store приоритетнее CSV`() {
        CuratorStore.record(mapOf("41О" to "Иванова А.Б."))
        renderStudentCard(student("41О", "Старая Ж.В."))
        composeRule.onNodeWithText("Иванова А.Б.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Старая Ж.В.").assertCountEquals(0)
    }

    @Test
    fun `карточка студента — фолбэк на CSV когда store пуст`() {
        renderStudentCard(student("41О", "Старая Ж.В."))
        composeRule.onNodeWithText("КУРАТОР").assertIsDisplayed()
        composeRule.onNodeWithText("Старая Ж.В.").assertIsDisplayed()
    }

    @Test
    fun `resolveCardCurator — store затем CSV затем null`() {
        CuratorStore.record(mapOf("41О" to "Иванова А.Б."))
        assertEquals("Иванова А.Б.", resolveCardCurator("41О", "Старая Ж.В."))
        assertEquals("Старая Ж.В.", resolveCardCurator("21М", "Старая Ж.В."))
        assertEquals("Старая Ж.В.", resolveCardCurator("21М", " Старая Ж.В. "))
        assertNull(resolveCardCurator("21М", "   "))
    }
}
