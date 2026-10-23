package com.politex.widget

import com.politex.data.local.entity.LessonEntity
import com.politex.util.SubgroupPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubgroupRoomsTest {
    private fun splitLesson() = LessonEntity(
        groupName = "41О",
        dayOfWeek = 1,
        lessonNumber = 1,
        timeStart = "09:00",
        timeEnd = "09:45",
        subjectRaw = "Математика",
        roomFirst = "101",
        teacherFirst = "",
        roomSecond = "202",
        isSplit = true,
    )

    @Test
    fun `NEUTRAL - две кнопки roomFirst и roomSecond`() {
        assertEquals(listOf("101", "202"), WidgetData.chooseRooms(splitLesson(), SubgroupPref.NEUTRAL))
    }

    @Test
    fun `FIRST - одна кнопка первой подгруппы`() {
        assertEquals(listOf("101"), WidgetData.chooseRooms(splitLesson(), SubgroupPref.FIRST))
    }

    @Test
    fun `SECOND - одна кнопка второй подгруппы`() {
        assertEquals(listOf("202"), WidgetData.chooseRooms(splitLesson(), SubgroupPref.SECOND))
    }

    @Test
    fun `non-split - одна комната независимо от pref`() {
        val lesson = splitLesson().copy(isSplit = false, roomSecond = "")
        assertEquals(listOf("101"), WidgetData.chooseRooms(lesson, SubgroupPref.NEUTRAL))
        assertEquals(listOf("101"), WidgetData.chooseRooms(lesson, SubgroupPref.SECOND))
    }

    @Test
    fun `II2 NEUTRAL split - splitStyle true (две цифры-кнопки)`() {
        assertTrue(WidgetData.isSplitStyle(WidgetData.chooseRooms(splitLesson(), SubgroupPref.NEUTRAL)))
    }

    @Test
    fun `II2 FIRST и SECOND - splitStyle false (ванильный чип каб N)`() {
        assertFalse(WidgetData.isSplitStyle(WidgetData.chooseRooms(splitLesson(), SubgroupPref.FIRST)))
        assertFalse(WidgetData.isSplitStyle(WidgetData.chooseRooms(splitLesson(), SubgroupPref.SECOND)))
    }

    @Test
    fun `II2 non-split - splitStyle false`() {
        val lesson = splitLesson().copy(isSplit = false, roomSecond = "")
        assertFalse(WidgetData.isSplitStyle(WidgetData.chooseRooms(lesson, SubgroupPref.NEUTRAL)))
    }
}
