package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.ui.util.RouteStartResolver
import com.politex.util.SubgroupPref
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStartResolverTest {
    private fun lesson(
        n: Int,
        start: String,
        room: String,
        room2: String = "",
        split: Boolean = false,
    ) = LessonEntity(
        groupName = "11А",
        dayOfWeek = 1,
        lessonNumber = n,
        timeStart = start,
        timeEnd = start,
        subjectRaw = "Математика",
        roomFirst = room,
        teacherFirst = "",
        roomSecond = room2,
        isSplit = split,
    )

    private val lessons =
        listOf(
            lesson(1, "08:30", "118"),
            lesson(2, "09:25", "223"),
            lesson(3, "10:35", "320"),
        )

    @Test
    fun `II1 тап по 3-му уроку — старт от кабинета 2-го даже утром до всех уроков`() {

        assertEquals(
            RouteStartResolver.RouteStart.From("223"),
            RouteStartResolver.startForLesson(lessons, lessons[2], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `II1 тап по 1-му уроку — главный вход`() {
        assertEquals(
            RouteStartResolver.RouteStart.From(""),
            RouteStartResolver.startForLesson(lessons, lessons[0], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `II1 тап по 2-му уроку — старт от кабинета 1-го`() {
        assertEquals(
            RouteStartResolver.RouteStart.From("118"),
            RouteStartResolver.startForLesson(lessons, lessons[1], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `II1 у предыдущего только roomSecond — берётся roomSecond`() {
        val onlySecond = listOf(lesson(1, "08:30", "", "118"), lesson(2, "09:25", "", "223"))
        assertEquals(
            RouteStartResolver.RouteStart.From("118"),
            RouteStartResolver.startForLesson(onlySecond, onlySecond[1], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `II1 предыдущий без кабинета пропускается — старт от более раннего`() {
        val day = listOf(lesson(1, "08:30", "118"), lesson(2, "09:25", " "), lesson(3, "10:35", "320"))
        assertEquals(
            RouteStartResolver.RouteStart.From("118"),
            RouteStartResolver.startForLesson(day, day[2], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `II1 пустой день — главный вход`() {
        assertEquals(
            RouteStartResolver.RouteStart.From(""),
            RouteStartResolver.startForLesson(emptyList(), lessons[0], SubgroupPref.NEUTRAL),
        )
    }

    private val splitDay =
        listOf(
            lesson(1, "08:30", "118", "224", split = true),
            lesson(2, "09:25", "320"),
            lesson(3, "10:35", "401", "402", split = true),
        )

    @Test
    fun `GG2 prev non-split — from = комната предыдущего, без вопроса`() {

        assertEquals(
            RouteStartResolver.RouteStart.From("320"),
            RouteStartResolver.startForLesson(splitDay, splitDay[2], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG2 prev split + NEUTRAL — вопрос с двумя комнатами ПРЕДЫДУЩЕГО урока`() {
        assertEquals(
            RouteStartResolver.RouteStart.Ask("118", "224"),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG3 prev split + FIRST — from = roomFirst предыдущего, без вопроса`() {
        assertEquals(
            RouteStartResolver.RouteStart.From("118"),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], SubgroupPref.FIRST),
        )
    }

    @Test
    fun `II1 prev split + SECOND — from = roomSecond предыдущего`() {
        assertEquals(
            RouteStartResolver.RouteStart.From("224"),
            RouteStartResolver.startForLesson(splitDay, splitDay[1], SubgroupPref.SECOND),
        )
    }

    @Test
    fun `GG2 нет предыдущего — главный вход даже для split-текущего`() {
        val day = listOf(lesson(1, "08:30", "401", "402", split = true))
        assertEquals(
            RouteStartResolver.RouteStart.From(""),
            RouteStartResolver.startForLesson(day, day[0], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `GG2 String-совместимость — Ask схлопывается в roomFirst`() {
        assertEquals(
            "118",
            RouteStartResolver.startForLesson(splitDay, splitDay[1]),
        )
    }

    @Test
    fun `GG2 prev split с одной незаполненной комнатой — не вопрос, а комната`() {
        val day = listOf(lesson(1, "08:30", "118", split = true), lesson(2, "09:25", "320"))
        assertEquals(
            RouteStartResolver.RouteStart.From("118"),
            RouteStartResolver.startForLesson(day, day[1], SubgroupPref.NEUTRAL),
        )
    }
}
