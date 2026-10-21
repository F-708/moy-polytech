package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.screens.GroupRoomResult
import com.politex.ui.screens.findGroupRoomNow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroupRoomLookupTest {
    private fun lesson(
        number: Int,
        roomFirst: String,
        roomSecond: String = "",
        isSplit: Boolean = false,
        dateString: String = "01.09.2026",
        timeStart: String = "",
    ) = LessonEntity(
        groupName = "41О",
        dayOfWeek = 1,
        lessonNumber = number,
        timeStart = timeStart,
        timeEnd = "",
        subjectRaw = "Тест",
        roomFirst = roomFirst,
        teacherFirst = "",
        roomSecond = roomSecond,
        teacherSecond = "",
        isSplit = isSplit,
        dateString = dateString,
    )

    @Test
    fun currentLesson_roomFromSlot() {

        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214"), lesson(5, "101")),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("214", res!!.room)
        assertEquals(3, res.lessonNumber)
        assertEquals(true, res.isNow)
    }

    @Test
    fun currentSlotButGroupHasNoLesson_fallsToNext() {

        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(5, "101")),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("101", res!!.room)
        assertEquals(5, res.lessonNumber)
        assertEquals(false, res.isNow)
    }

    @Test
    fun splitLesson_prefersRoomFirst() {

        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214", "305", isSplit = true)),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertEquals("214", res!!.room)
    }

    @Test
    fun roomFirstBlank_fallsToRoomSecond() {
        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "", "305")),
                currentMinutes = 10 * 60 + 30,
                dayOfWeek = 1,
            )
        assertEquals("305", res!!.room)
    }

    @Test
    fun betweenLessons_nextLessonRoom() {

        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214"), lesson(5, "101")),
                currentMinutes = 11 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("101", res!!.room)
        assertEquals(5, res.lessonNumber)
        assertEquals(false, res.isNow)
    }

    @Test
    fun afterAllLessons_null() {

        assertNull(
            findGroupRoomNow(listOf(lesson(3, "214")), 20 * 60, 1),
        )
    }

    @Test
    fun beforeAllLessons_firstLesson() {

        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(3, "214"), lesson(1, "102")),
                currentMinutes = 7 * 60 + 30,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("102", res!!.room)
        assertEquals(1, res.lessonNumber)
        assertEquals(false, res.isNow)
    }

    @Test
    fun saturdayBellsUsed() {

        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(7, "305"), lesson(8, "101")),
                currentMinutes = 14 * 60,
                dayOfWeek = 6,
            )
        assertNotNull(res)
        assertEquals("305", res!!.room)
        assertEquals(true, res.isNow)
    }

    @Test
    fun emptyLessons_null() {
        assertNull(findGroupRoomNow(emptyList(), 10 * 60, 1))
    }

    @Test
    fun noRoomOnLesson_null() {

        assertNull(findGroupRoomNow(listOf(lesson(3, "")), 10 * 60 + 30, 1))
    }

    @Test
    fun timeStartFallbackWhenBellMissing() {

        val res =
            findGroupRoomNow(
                lessons = listOf(lesson(11, "214", timeStart = "17:55")),
                currentMinutes = 12 * 60,
                dayOfWeek = 1,
            )
        assertNotNull(res)
        assertEquals("214", res!!.room)
        assertEquals(11, res.lessonNumber)
    }

    @Test
    fun bellSlotMapsCorrectly() {

        val bells = CollegeBellSchedule.getBellsForDay(1)
        assertEquals(3, bells.firstOrNull { 10 * 60 + 30 in it.startMinutes..it.endMinutes }?.lessonNumber)
    }

    @Test
    fun resultShape_isSerializableDataClass() {

        val r = GroupRoomResult(room = "214", lessonNumber = 3, isNow = true)
        assertEquals("214", r.room)
    }
}
