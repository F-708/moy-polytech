package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.data.local.entity.LessonEntity
import com.politex.ui.screens.isDemoTask
import com.politex.ui.screens.pulseTargetLesson
import com.politex.ui.screens.showPulse
import com.politex.util.HintFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HintFlagsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `round-trip - markDone ставит флаг, isDone читает, ключи независимы`() {
        HintFlags.resetAll(context)
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SWIPE_TASKS))
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))

        HintFlags.markDone(context, HintFlags.KEY_SWIPE_TASKS)
        assertTrue(HintFlags.isDone(context, HintFlags.KEY_SWIPE_TASKS))
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))

        HintFlags.markDone(context, HintFlags.KEY_SCHEDULE_TAPS)
        assertTrue(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))
    }

    @Test
    fun `resetAll сбрасывает оба флага`() {
        HintFlags.markDone(context, HintFlags.KEY_SWIPE_TASKS)
        HintFlags.markDone(context, HintFlags.KEY_SCHEDULE_TAPS)
        HintFlags.resetAll(context)
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SWIPE_TASKS))
        assertFalse(HintFlags.isDone(context, HintFlags.KEY_SCHEDULE_TAPS))
    }

    @Test
    fun `демо-задание - только id -1, остальное реальные задания из БД`() {
        assertTrue(isDemoTask(-1L))
        assertFalse(isDemoTask(0L))
        assertFalse(isDemoTask(1L))
        assertFalse(isDemoTask(42L))
    }

    @Test
    fun `пульс только на целевом уроке и только пока флаг не пройден`() {
        assertTrue(showPulse(flagDone = false, isTargetLesson = true))
        assertFalse(showPulse(flagDone = true, isTargetLesson = true))
        assertFalse(showPulse(flagDone = false, isTargetLesson = false))
        assertFalse(showPulse(flagDone = true, isTargetLesson = false))
    }

    private fun lesson(
        number: Int,
        room: String,
        teacher: String,
        roomSecond: String = "",
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 1,
        lessonNumber = number,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Математика",
        roomFirst = room,
        teacherFirst = teacher,
        roomSecond = roomSecond,
        dateString = "",
    )

    @Test
    fun `цель пульса в teacher-режиме - первый урок с кабинетом`() {
        val lessons = listOf(lesson(1, "", ""), lesson(2, "204", ""), lesson(3, "205", ""))
        assertEquals(2, pulseTargetLesson(lessons, teacherMode = true))
    }

    @Test
    fun `цель пульса в teacher-режиме - сплит считается и по второму кабинету`() {
        assertEquals(1, pulseTargetLesson(listOf(lesson(1, "", "", roomSecond = "204")), teacherMode = true))
    }

    @Test
    fun `цель пульса в teacher-режиме - без кабинетов цели нет`() {
        assertNull(pulseTargetLesson(listOf(lesson(1, "", "Ковалев В.П.")), teacherMode = true))
        assertNull(pulseTargetLesson(emptyList(), teacherMode = true))
    }

    @Test
    fun `цель пульса в студенческом режиме - кабинет или преподаватель, как раньше`() {
        assertEquals(1, pulseTargetLesson(listOf(lesson(1, "", "Ковалев В.П.")), teacherMode = false))
        assertEquals(2, pulseTargetLesson(listOf(lesson(1, "", ""), lesson(2, "204", "")), teacherMode = false))
        assertNull(pulseTargetLesson(listOf(lesson(1, "", "")), teacherMode = false))
    }
}
