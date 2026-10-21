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
import com.politex.ui.screens.GroupScheduleScreen
import com.politex.ui.theme.MyApplicationTheme
import com.politex.util.ScheduleDisplayPrefs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp")
class GroupScheduleRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: MpkDatabase

    private fun lesson(
        group: String,
        dow: Int,
        number: Int,
        start: String,
        end: String,
    ) = LessonEntity(
        groupName = group,
        dayOfWeek = dow,
        lessonNumber = number,
        timeStart = start,
        timeEnd = end,
        subjectRaw = "Математика",
        roomFirst = "204",
        teacherFirst = "Ковалев В.П.",
        dateString = "",
    )

    private fun weekLessons(group: String): List<LessonEntity> =
        (1..6).flatMap { dow ->
            listOf(
                lesson(group, dow, 1, "08:15", "09:55"),
                lesson(group, dow, 2, "09:55", "11:30"),
            )
        }

    private fun seed(
        group: String,
        lessons: List<LessonEntity>,
    ) {
        runBlocking {
            db.lessonDao().clearLessonsForGroup(group)
            db.lessonDao().insertLessons(lessons)
        }
    }

    private fun renderScreen(
        group: String,
        compact: Boolean = false,
    ) {
        ScheduleDisplayPrefs.setCompact(context, compact)
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
            composeRule.onAllNodesWithTag("lesson_card_1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        ScheduleDisplayPrefs.setCompact(context, false)
    }

    @After
    fun tearDown() {
        ScheduleDisplayPrefs.setCompact(context, false)
    }

    @Test
    fun `чужая группа объединяет подряд идущие одинаковые пары`() {
        seed("41О", weekLessons("41О"))
        renderScreen("41О")
        composeRule.onNodeWithTag("lesson_card_1").assertIsDisplayed()
        composeRule.onAllNodesWithTag("lesson_card_2").assertCountEquals(0)
        composeRule.onNodeWithText("08:15 – 09:55").assertIsDisplayed()
    }

    @Test
    fun `компактный режим применяется на вкладке чужой группы`() {
        seed("42О", weekLessons("42О"))
        renderScreen("42О", compact = true)
        composeRule.onNodeWithTag("lesson_card_1").assertIsDisplayed()
        composeRule.onAllNodesWithText("08:15 – 09:55").assertCountEquals(0)
    }

    @Test
    fun `переключение компактности обновляет карточки без пересоздания экрана`() {
        seed("43О", weekLessons("43О"))
        renderScreen("43О")
        composeRule.onNodeWithText("08:15 – 09:55").assertIsDisplayed()

        composeRule.runOnIdle { ScheduleDisplayPrefs.setCompact(context, true) }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("08:15 – 09:55").assertCountEquals(0)
        composeRule.onNodeWithTag("lesson_card_1").assertIsDisplayed()
    }
}