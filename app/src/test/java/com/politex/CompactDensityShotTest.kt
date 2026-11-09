package com.politex

import android.content.Context
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.data.model.GroupInfo
import com.politex.data.repository.ScheduleRepository
import com.politex.ui.screens.ScheduleScreen
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.theme.SurfaceTone
import com.politex.ui.theme.ThemeMode
import com.politex.ui.theme.applyAppearance
import com.politex.util.Dow
import com.politex.util.ScheduleDisplayPrefs
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Плотность компактного расписания: замеры высот + скриншоты 360×780.
 *
 * Мерим живую композицию (высота карточки и зазор между карточками в px, переведённые в dp),
 * а не исходник: только так видно, что «компактно» реально стало компактнее.
 * Скриншоты пишутся с -Proborazzi.test.record=true.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp")
class CompactDensityShotTest {
    companion object {
        /** Файл замеров чистим один раз на класс, иначе он копит прогоны. */
        init {
            java.io.File("build/screenshots/w11-measure.txt").delete()
        }
    }

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val group = GroupInfo(rawName = "ИС-21", course = 2, groupNumber = 1, specialtyCode = 'О')

    private val repo: ScheduleRepository by lazy {
        ScheduleRepository(db.lessonDao())
    }

    private lateinit var db: MpkDatabase

    @Before
    fun setUpDb() {
        db =
            Room
                .inMemoryDatabaseBuilder(context, MpkDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        ScheduleDisplayPrefs.setCompact(context, false)
        applyAppearance(AppTheme.COLLEGE, ThemeMode.SYSTEM, SurfaceTone.NATIVE, systemDark = false)
    }

    /** Уроки «сегодня»: [spec] — (номер, предмет, кабинет, преподаватель). */
    private fun lessonsOf(spec: List<Triple<Int, String, String>>): List<LessonEntity> {
        // День берём тот же, что покажет экран: у него «выходной» сдвигается на ближайший
        // учебный день ([daySlots]), поэтому нельзя брать просто сегодняшний Calendar.
        val shown = com.politex.ui.util.daySlots(Calendar.getInstance(), group.course == 1) { false }.first
        val dow = Dow.of(shown)
        return spec.map { (number, subject, room) ->
            val (start, end) = CollegeBellSchedule.getTimeForLessonNumber(number, dow)
            LessonEntity(
                groupName = group.canonicalName,
                dayOfWeek = dow,
                lessonNumber = number,
                timeStart = start,
                timeEnd = end,
                subjectRaw = subject,
                roomFirst = room,
                teacherFirst = "Иванова И.И.",
                // Пустая дата = «недельное» расписание: экран подставляет его на активный день,
                // и замер не зависит от того, какое число подставит Robolectric.
                dateString = "",
            )
        }
    }

    private val singleDay = { listOf(Triple(3, "Физика", "305")) }

    private val pairDay = {
        listOf(
            Triple(1, "Математика", "240"),
            Triple(2, "Математика", "240"),
        )
    }

    private val denseDay = {
        listOf(
            Triple(1, "Математика", "240"),
            Triple(2, "Математика", "240"),
            Triple(3, "Физика", "305"),
            Triple(4, "Информатика", "112"),
            Triple(5, "Информатика", "112"),
            Triple(6, "История", "208"),
        )
    }

    private fun render(
        spec: List<Triple<Int, String, String>>,
        compact: Boolean = true,
        dark: Boolean = false,
    ) {
        ScheduleDisplayPrefs.setCompact(context, compact)
        applyAppearance(
            AppTheme.COLLEGE,
            if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            SurfaceTone.NATIVE,
            systemDark = false,
        )
        val lessons = lessonsOf(spec)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme {
                ScheduleScreen(
                    groupInfo = group,
                    scheduleRepository = repo,
                    onSyncRequest = {},
                    groupLessonsFromVm = lessons,
                    pendingTasksFromVm = emptyList(),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    /** Замер в файл: stdout теста в отчёте Gradle не гарантирован. */
    private fun measure(
        key: String,
        value: Float,
    ) {
        val file = java.io.File("build/screenshots/w11-measure.txt")
        file.parentFile?.mkdirs()
        file.appendText("$key=${"%.2f".format(value)}\n")
        println("MEASURE $key=$value")
    }

    private fun px2dp(px: Int): Float = px / composeRule.density.density

    private fun heightDp(tag: String): Float {
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        return px2dp(node.size.height)
    }

    /** Вертикальный зазор между двумя карточками уроков, dp. */
    private fun gapDp(top: String, bottom: String): Float {
        val a = composeRule.onNodeWithTag(top).getBoundsInRoot().bottom
        val b = composeRule.onNodeWithTag(bottom).getBoundsInRoot().top
        return (b - a).value
    }

    @Test
    fun `компактный одиночный урок - высота`() {
        render(singleDay())
        measure("compact-single-card", heightDp("lesson_card_3"))
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-compact-single-light.png")
    }

    @Test
    fun `компактный одиночный урок - тёмная тема`() {
        render(singleDay(), dark = true)
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-compact-single-dark.png")
    }

    @Test
    fun `компактная пара - высота`() {
        render(pairDay())
        // У объединённой пары одна карточка: тег по первому номеру урока.
        measure("compact-pair-card", heightDp("lesson_card_1"))
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-compact-pair-light.png")
    }

    @Test
    fun `компактная пара - тёмная тема`() {
        render(pairDay(), dark = true)
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-compact-pair-dark.png")
    }

    @Test
    fun `компактный плотный день - зазоры`() {
        render(denseDay())
        measure("compact-dense-gap-1-3", gapDp("lesson_card_1", "lesson_card_3"))
        measure("compact-dense-gap-3-4", gapDp("lesson_card_3", "lesson_card_4"))
        measure("compact-dense-gap-4-6", gapDp("lesson_card_4", "lesson_card_6"))
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-compact-dense-light.png")
    }

    @Test
    fun `компактный плотный день - тёмная тема`() {
        render(denseDay(), dark = true)
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-compact-dense-dark.png")
    }

    @Test
    fun `обычный режим - те же замеры для сравнения`() {
        render(denseDay(), compact = false)
        measure("regular-card", heightDp("lesson_card_3"))
        measure("regular-gap", gapDp("lesson_card_3", "lesson_card_4"))
        composeRule.onRoot().captureRoboImage("build/screenshots/w11-regular-dense-light.png")
    }

    /** Сколько карточек уроков целиком видно в области списка при 360×780. */
    @Test
    fun `сколько уроков на экран`() {
        render(denseDay())
        val listTop = composeRule.onNodeWithTag("lesson_card_1").getBoundsInRoot().top.value
        measure("viewport-available", 780f - listTop)
        measure("dense-card3-top", composeRule.onNodeWithTag("lesson_card_3").getBoundsInRoot().top.value)
        measure("dense-card6-bottom", composeRule.onNodeWithTag("lesson_card_6").getBoundsInRoot().bottom.value)
    }
}