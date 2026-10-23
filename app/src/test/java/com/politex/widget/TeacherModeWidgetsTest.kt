package com.politex.widget

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.politex.R
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.TeacherLessonEntity
import com.politex.util.DateFormats
import com.politex.util.TeacherModeStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31, 33, 35])
class TeacherModeWidgetsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val db get() = MpkDatabase.getInstance(ctx)

    private val teacherName = "Тестова Анна Петровна"
    private val surnameKey = "тестова"

    private var realTz: TimeZone = TimeZone.getDefault()

    private val today: String get() = DateFormats.dotDate(Calendar.getInstance())

    private val tomorrow: String
        get() {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            return DateFormats.dotDate(cal)
        }

    @Before
    fun setUp() = runBlocking {
        realTz = TimeZone.getDefault()
        freezeAtTuesday1030()
        TeacherModeStore.clear(ctx)
        db.teacherLessonDao().deleteForDate(today)
        db.teacherLessonDao().deleteForDate(tomorrow)
        WidgetUpdateHelper.getPrefs(ctx).edit()
            .remove("teacher_today_synced_${surnameKey}_$today")
            .remove("teacher_today_sync_attempt_${surnameKey}_$today")
            .apply()
        WidgetData.invalidateCache()
        TeacherWidgetData.invalidate()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
    }

    @After
    fun tearDown() = runBlocking {
        db.teacherLessonDao().deleteForDate(today)
        db.teacherLessonDao().deleteForDate(tomorrow)
        TeacherModeStore.clear(ctx)
        WidgetData.invalidateCache()
        TeacherWidgetData.invalidate()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
        TimeZone.setDefault(realTz)
    }

    private fun row(
        date: String,
        dow: Int,
        number: Int,
        subject: String,
        groups: String,
        room: String,
    ) = TeacherLessonEntity(
        teacherName = "Тестова А.П.",
        surnameKey = surnameKey,
        dateString = date,
        dayOfWeek = dow,
        lessonNumber = number,
        groups = groups,
        subject = subject,
        room = room,
    )

    private fun seedToday() = runBlocking {
        db.teacherLessonDao().insertLessons(
            listOf(
                row(today, 2, 2, "Математика", "11А, 21Б", "204"),
                row(today, 2, 3, "Физика", "31О", "118"),
                row(today, 2, 4, "Химия", "41О", "214"),
            ),
        )
        TeacherWidgetData.invalidate()
        BellCountdownWidgetProvider.invalidateLastLessonCache()
    }

    @Test
    fun `в студенческом режиме teacherModeSnapshot пустой`() {
        WidgetUpdateHelper.getPrefs(ctx).edit().putString("selected_group", "41О").apply()
        assertNull(TeacherWidgetData.teacherModeSnapshot(ctx))
    }

    @Test
    fun `снапшот собирает уроки преподавателя из teacher_lessons`() = runBlocking {
        TeacherModeStore.setTeacher(ctx, teacherName)
        seedToday()

        val snap = TeacherWidgetData.teacherModeSnapshot(ctx)
        assertNotNull(snap)
        assertEquals(today, snap!!.dateString)
        assertEquals(2, snap.dow)
        assertEquals(listOf(2, 3, 4), snap.lessons.map { it.lessonNumber })

        val third = snap.lessons.first { it.lessonNumber == 3 }
        assertEquals(listOf("31О"), third.groups)
        assertEquals("118", third.room)
        assertEquals("10:20", third.start)
        assertEquals("11:05", third.end)

        val entities = snap.toEntities()
        assertEquals("11А, 21Б", entities.first().groupName)
        assertEquals("", entities.first().teacherFirst)
    }

    @Test
    fun `снапшот находит следующую пару на завтра`() = runBlocking {
        TeacherModeStore.setTeacher(ctx, teacherName)
        db.teacherLessonDao().insertLessons(listOf(row(tomorrow, 3, 1, "Математика", "11А", "100")))
        TeacherWidgetData.invalidate()

        val snap = TeacherWidgetData.teacherModeSnapshot(ctx)
        assertNotNull(snap)
        assertTrue(snap!!.lessons.isEmpty())
        assertEquals(tomorrow, snap.next?.dateString)
        assertEquals(3, snap.next?.dow)
        assertEquals(1, snap.next?.lesson?.lessonNumber)
    }

    @Test
    fun `NowNext в режиме преподавателя показывает текущий и следующий уроки с группами`() {
        TeacherModeStore.setTeacher(ctx, teacherName)
        seedToday()

        val root = NowNextWidgetProvider.buildViews(ctx, 1).apply(ctx, null)

        assertEquals(View.GONE, root.findViewById<View>(R.id.tv_empty).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.ll_now_block).visibility)
        assertEquals(
            "Физика • 31О",
            root.findViewById<TextView>(R.id.tv_now_subject).text.toString(),
        )
        assertEquals(
            "10:20–11:05",
            root.findViewById<TextView>(R.id.tv_now_details).text.toString(),
        )
        assertEquals(
            "Химия • 41О",
            root.findViewById<TextView>(R.id.tv_next_subject).text.toString(),
        )
    }

    @Test
    fun `NowNext в режиме преподавателя без уроков показывает следующую пару`() = runBlocking {
        TeacherModeStore.setTeacher(ctx, teacherName)
        db.teacherLessonDao().insertLessons(listOf(row(tomorrow, 3, 1, "Математика", "11А", "100")))
        TeacherWidgetData.invalidate()

        val root = NowNextWidgetProvider.buildViews(ctx, 1).apply(ctx, null)

        assertEquals(View.GONE, root.findViewById<View>(R.id.ll_now_block).visibility)
        val empty = root.findViewById<TextView>(R.id.tv_empty).text.toString()
        assertTrue("пустое состояние обязано начинаться с «Сегодня уроков нет»: $empty", empty.startsWith("Сегодня уроков нет"))
        assertTrue("пустое состояние обязано анонсировать следующую пару: $empty", empty.contains("Следующая — ср 08:15"))
        assertTrue("в пустом состоянии виден кабинет: $empty", empty.contains("каб. 100"))
    }

    @Test
    fun `Today в режиме преподавателя показывает фамилию в шапке и группы в строках`() {
        TeacherModeStore.setTeacher(ctx, teacherName)
        seedToday()

        val root = TodayScheduleWidgetProvider.buildViews(ctx, 1).apply(ctx, null)

        assertEquals(
            "СЕГОДНЯ • Тестова",
            root.findViewById<TextView>(R.id.tv_today_header).text.toString(),
        )
        assertEquals(View.GONE, root.findViewById<View>(R.id.tv_today_empty).visibility)

        val rows = root.findViewById<LinearLayout>(R.id.today_rows)
        assertEquals(3, rows.childCount)
        val subjects = (0 until rows.childCount).map {
            rows.getChildAt(it).findViewById<TextView>(R.id.row_subject).text.toString()
        }
        assertEquals(
            listOf("Математика • 11А, 21Б", "Физика • 31О", "Химия • 41О"),
            subjects,
        )
        val times = (0 until rows.childCount).map {
            rows.getChildAt(it).findViewById<TextView>(R.id.row_time).text.toString()
        }
        assertEquals(listOf("09:10–09:55", "10:20–11:05", "11:15–12:00"), times)
    }

    @Test
    fun `последний урок дня в режиме преподавателя считается по урокам преподавателя`() {
        TeacherModeStore.setTeacher(ctx, teacherName)
        seedToday()

        assertEquals(4, BellCountdownWidgetProvider.lastGroupLessonForToday(ctx, Calendar.getInstance()))

        val info = BellCountdownWidgetProvider.countdownInfo(ctx, CountdownMode.GROUP_LESSONS)
        assertNotNull(info)
        assertEquals(35L, info!!.first)
        assertEquals("до звонка", info.second)
    }

    @Test
    fun `без уроков преподавателя последний урок равен null`() = runBlocking {
        TeacherModeStore.setTeacher(ctx, teacherName)
        db.teacherLessonDao().insertLessons(listOf(row(tomorrow, 3, 1, "Математика", "11А", "100")))
        TeacherWidgetData.invalidate()
        BellCountdownWidgetProvider.invalidateLastLessonCache()

        assertNull(BellCountdownWidgetProvider.lastGroupLessonForToday(ctx, Calendar.getInstance()))
    }

    @Test
    fun `ensureTodaySchedule в режиме преподавателя помечает день синхронизированным без сети`() = runBlocking {
        TeacherModeStore.setTeacher(ctx, teacherName)
        seedToday()

        WidgetUpdateHelper.ensureTodaySchedule(ctx)

        assertTrue(
            WidgetUpdateHelper.getPrefs(ctx).getBoolean("teacher_today_synced_${surnameKey}_$today", false),
        )
    }

    private fun freezeAtTuesday1030() {
        val real = System.currentTimeMillis()
        val target =
            Calendar.getInstance(BASE_TZ).apply {
                timeInMillis = real
                set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        while (target.timeInMillis <= real) target.add(Calendar.DAY_OF_YEAR, 7)
        val delta = (target.timeInMillis - real).toInt()
        TimeZone.setDefault(
            object : TimeZone() {
                init {
                    id = "TEACHER_WIDGETS/FROZEN"
                }

                override fun getOffset(
                    era: Int,
                    year: Int,
                    month: Int,
                    day: Int,
                    dayOfWeek: Int,
                    milliseconds: Int,
                ): Int = BASE_TZ.getOffset(era, year, month, day, dayOfWeek, milliseconds) + delta

                override fun getOffset(millis: Long): Int = BASE_TZ.getOffset(millis) + delta

                override fun setRawOffset(offsetMillis: Int) = Unit

                override fun getRawOffset(): Int = BASE_TZ.rawOffset + delta

                override fun useDaylightTime(): Boolean = false

                override fun inDaylightTime(date: java.util.Date): Boolean = false
            },
        )
    }

    private companion object {
        val BASE_TZ: TimeZone = TimeZone.getDefault()
    }
}
