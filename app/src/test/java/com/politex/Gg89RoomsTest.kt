package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.util.SubgroupPref
import com.politex.widget.WidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class Gg89RoomsTest {
    private fun lesson(
        split: Boolean,
        r1: String = "315",
        r2: String = "412",
        date: String = "",
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 1,
        lessonNumber = 1,
        timeStart = "08:15",
        timeEnd = "09:00",
        subjectRaw = "Математика",
        roomFirst = r1,
        teacherFirst = "Иванова А.А.",
        roomSecond = r2,
        teacherSecond = "Петрова Б.Б.",
        isSplit = split,
        dateString = date,
    )

    @Test
    fun `chooseRooms split + NEUTRAL — обе комнаты`() {
        assertEquals(listOf("315", "412"), WidgetData.chooseRooms(lesson(split = true), SubgroupPref.NEUTRAL))
    }

    @Test
    fun `chooseRooms split + FIRST — только первая подгруппа`() {
        assertEquals(listOf("315"), WidgetData.chooseRooms(lesson(split = true), SubgroupPref.FIRST))
    }

    @Test
    fun `chooseRooms split + SECOND — только вторая подгруппа`() {
        assertEquals(listOf("412"), WidgetData.chooseRooms(lesson(split = true), SubgroupPref.SECOND))
    }

    @Test
    fun `chooseRooms non-split — одна первая комната`() {
        assertEquals(listOf("315"), WidgetData.chooseRooms(lesson(split = false), SubgroupPref.NEUTRAL))
    }

    @Test
    fun `chooseRooms non-split с пустой первой — берёт вторую`() {
        assertEquals(listOf("412"), WidgetData.chooseRooms(lesson(split = false, r1 = " "), SubgroupPref.NEUTRAL))
    }

    @Test
    fun `chooseRooms split + SECOND с пустой второй — откат к первой`() {
        assertEquals(listOf("315"), WidgetData.chooseRooms(lesson(split = true, r2 = ""), SubgroupPref.SECOND))
    }

    @Test
    fun `GG8 ключ кэша содержит дату — та же минута на следующие сутки не сталкивается`() {

        val yesterday = WidgetData.lessonsCacheKey(minute = 480, group = "41О", date = "05.10.2026")
        val today = WidgetData.lessonsCacheKey(minute = 480, group = "41О", date = "06.10.2026")
        assertNotEquals(yesterday, today)
        assertEquals(yesterday, WidgetData.lessonsCacheKey(480, "41О", "05.10.2026"))
    }

    @Test
    fun `GG8 dateString с дефисами нормализуется в фильтре сегодня`() {
        val res = WidgetData.filterTodayLessons(listOf(lesson(split = false, date = "06-10-2026")), "06.10.2026")
        assertEquals(1, res.size)
    }
}
