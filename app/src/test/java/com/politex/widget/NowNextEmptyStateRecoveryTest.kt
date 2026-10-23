package com.politex.widget

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.politex.R
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31, 33, 35])
class NowNextEmptyStateRecoveryTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dao = MpkDatabase.getInstance(ctx).lessonDao()
    private val group = "41О"

    @Before
    fun setUp() = runBlocking {
        WidgetUpdateHelper.setSelectedGroup(ctx, group)
        dao.clearLessonsForGroup(group)
        WidgetData.invalidateCache()
    }

    private fun seedCurrentLesson() = runBlocking {
        val now = WidgetData.minuteOfDay(ctx)
        val start = (now - 10).coerceIn(0, 23 * 60)
        val end = (now + 10).coerceIn(start + 1, 24 * 60 - 1)
        dao.clearLessonsForGroup(group)
        dao.insertLessons(
            listOf(
                LessonEntity(
                    groupName = group,
                    dayOfWeek = WidgetData.dayOfWeek(ctx),
                    lessonNumber = 1,
                    timeStart = "%02d:%02d".format(start / 60, start % 60),
                    timeEnd = "%02d:%02d".format(end / 60, end % 60),
                    subjectRaw = "Математика",
                    roomFirst = "101",
                    teacherFirst = "",
                    dateString = WidgetData.todayDateString(),
                ),
            ),
        )
        WidgetData.invalidateCache()
    }

    private fun clearLessons() = runBlocking {
        dao.clearLessonsForGroup(group)
        WidgetData.invalidateCache()
    }

    @Test
    fun `пустое состояние прячет блоки и показывает текст`() {
        clearLessons()
        val views = NowNextWidgetProvider.buildViews(ctx, 1)
        val root = views.apply(ctx, null)
        assertEquals(View.GONE, root.findViewById<View>(R.id.ll_now_block).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.tv_empty).visibility)
    }

    @Test
    fun `после пустого состояния блоки возвращаются и показывают урок`() {
        clearLessons()
        val empty = NowNextWidgetProvider.buildViews(ctx, 1)
        val root = empty.apply(ctx, null)
        assertEquals(View.GONE, root.findViewById<View>(R.id.ll_now_block).visibility)

        seedCurrentLesson()
        val fresh = NowNextWidgetProvider.buildViews(ctx, 1)
        fresh.reapply(ctx, root)

        assertEquals(
            "ll_now_block залип в GONE — виджет навсегда показывает «Уроки закончились»",
            View.VISIBLE,
            root.findViewById<View>(R.id.ll_now_block).visibility,
        )
        assertEquals(
            "ll_next_block залип в GONE",
            View.VISIBLE,
            root.findViewById<View>(R.id.ll_next_block).visibility,
        )
        assertEquals(View.GONE, root.findViewById<View>(R.id.tv_empty).visibility)
        val nowSubject = root.findViewById<TextView>(R.id.tv_now_subject).text.toString()
        assertTrue(
            "ожидался предмет текущего урока, получено «$nowSubject»",
            nowSubject.isNotBlank() && nowSubject != "—",
        )
    }

    @Test
    fun `строки с непарсящимся временем отбрасываются`() {
        val broken = LessonEntity(
            groupName = group,
            dayOfWeek = 1,
            lessonNumber = 1,
            timeStart = "??",
            timeEnd = "",
            subjectRaw = "Физика",
            roomFirst = "",
            teacherFirst = "",
            dateString = "16.10.2026",
        )
        assertTrue(
            "битое время должно отбрасываться, а не давать ложное «Уроки закончились»",
            WidgetData.filterTodayLessons(listOf(broken), "16.10.2026").isEmpty(),
        )
    }

    @Test
    fun `бюджет сетевого синка меньше окна goAsync`() {
        assertTrue(
            "SYNC_BUDGET_MS обязан оставлять запас до ~10с лимита goAsync",
            WidgetUpdateHelper.SYNC_BUDGET_MS < 9_000L,
        )
    }
}
