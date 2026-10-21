package com.politex

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.ui.components.currentRoomLessonNumber
import com.politex.ui.components.filterRoomLessonsForDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomScheduleLogicTest {
    private fun lesson(
        lessonNumber: Int,
        roomFirst: String,
        roomSecond: String = "",
        groupName: String = "41О",
        dateString: String = "",
        dayOfWeek: Int = 1,
        teacherFirst: String = "Иванова А.А.",
        teacherSecond: String = "",
    ) = LessonEntity(
        groupName = groupName,
        dayOfWeek = dayOfWeek,
        lessonNumber = lessonNumber,
        timeStart = "08:15",
        timeEnd = "09:55",
        subjectRaw = "Иностранный язык",
        roomFirst = roomFirst,
        teacherFirst = teacherFirst,
        roomSecond = roomSecond,
        teacherSecond = teacherSecond,
        isSplit = roomSecond.isNotBlank(),
        dateString = dateString,
    )

    @Test
    fun `фильтр по кабинету roomFirst и roomSecond (дробные пары)`() {
        val lessons =
            listOf(
                lesson(1, roomFirst = "215"),
                lesson(2, roomFirst = "101"),
                lesson(3, roomFirst = "101", roomSecond = "215"),
                lesson(4, roomFirst = "302"),
            )
        val result = filterRoomLessonsForDate(lessons, "215", "22.09.2026")
        assertEquals(listOf(1, 3), result.map { it.lessonNumber })
    }

    @Test
    fun `точная дата приоритетнее пустого dateString`() {
        val lessons =
            listOf(
                lesson(1, roomFirst = "215", dateString = "22.09.2026"),
                lesson(2, roomFirst = "215", dateString = ""),
                lesson(3, roomFirst = "215", dateString = "25.09.2026"),
            )
        val result = filterRoomLessonsForDate(lessons, "215", "22.09.2026")
        assertEquals(listOf(1), result.map { it.lessonNumber })
    }

    @Test
    fun `фолбэк на пустой dateString когда точных нет`() {
        val lessons =
            listOf(
                lesson(2, roomFirst = "215", dateString = ""),
                lesson(1, roomFirst = "215", dateString = ""),
            )
        val result = filterRoomLessonsForDate(lessons, "215", "22.09.2026")

        assertEquals(listOf(1, 2), result.map { it.lessonNumber })
    }

    @Test
    fun `пустой кабинет или нет совпадений — пустой список`() {
        assertTrue(filterRoomLessonsForDate(emptyList(), "215", "22.09.2026").isEmpty())
        assertTrue(
            filterRoomLessonsForDate(
                listOf(lesson(1, roomFirst = "101")),
                "215",
                "22.09.2026",
            ).isEmpty(),
        )
        assertTrue(
            filterRoomLessonsForDate(
                listOf(lesson(1, roomFirst = "215")),
                "",
                "22.09.2026",
            ).isEmpty(),
        )
    }

    @Test
    fun `текущая пара по звонкам — внутри урока и на перемене`() {

        assertEquals(3, currentRoomLessonNumber(10 * 60 + 30, 1))

        assertEquals(null, currentRoomLessonNumber(10 * 60 + 5, 1))

        assertEquals(null, currentRoomLessonNumber(7 * 60, 1))

        assertEquals(1, currentRoomLessonNumber(8 * 60 + 30, 6))
    }

    @Test
    fun `инфочас четверга не считается парой`() {

        assertEquals(null, currentRoomLessonNumber(14 * 60 + 20, 4))
    }

    @Test
    fun `W8 - плоский список без группировки, несколько групп в одном слоте, сортировка по уроку`() {
        val lessons =
            listOf(
                lesson(2, roomFirst = "215", groupName = "22П"),
                lesson(1, roomFirst = "215", groupName = "31О"),
                lesson(1, roomFirst = "215", groupName = "41О"),
            )
        val result = filterRoomLessonsForDate(lessons, "215", "22.09.2026")

        assertEquals(listOf(1, 1, 2), result.map { it.lessonNumber })
        assertEquals(listOf("31О", "41О", "22П"), result.map { it.groupName })
    }

    @Test
    fun `подтверждение источника слота — CollegeBellSchedule getCurrentSlot`() {

        val slot = CollegeBellSchedule.getCurrentSlot(8 * 60 + 45, 1)
        assertEquals(1, slot?.lessonNumber)
    }

    @Test
    fun `339 - split-урок где кабинет во второй подгруппе виден в диалоге`() {
        val lessons =
            listOf(
                lesson(7, roomFirst = "340", roomSecond = "339", groupName = "23М", dateString = "21.10.2026", dayOfWeek = 3),
                lesson(1, roomFirst = "101", groupName = "11А", dateString = "21.10.2026", dayOfWeek = 3),
            )
        val result = filterRoomLessonsForDate(lessons, "339", "21.10.2026")
        assertEquals(listOf(7), result.map { it.lessonNumber })
        assertEquals("23М", result.single().groupName)
    }

    @Test
    fun `339 - две группы в одном кабинете на одну дату видны обе`() {
        val lessons =
            listOf(
                lesson(3, roomFirst = "339", groupName = "41Н", dateString = "21.10.2026", dayOfWeek = 3, teacherFirst = "Мешалкина И.В."),
                lesson(5, roomFirst = "339", groupName = "21М", dateString = "21.10.2026", dayOfWeek = 3, teacherFirst = "Мешалкина И.В."),
            )
        val result = filterRoomLessonsForDate(lessons, "339", "21.10.2026")
        assertEquals(listOf(3, 5), result.map { it.lessonNumber })
        assertEquals(listOf("41Н", "21М"), result.map { it.groupName })
    }

    @Test
    fun `339 - кабинет записан как 339х340 сплитом - диалог возвращает все 4 урока`() {
        val lessons =
            listOf(
                lesson(3, roomFirst = "339", groupName = "41Н", dateString = "21.10.2026", dayOfWeek = 3, teacherFirst = "Мешалкина И.В."),
                lesson(4, roomFirst = "339", groupName = "41Н", dateString = "21.10.2026", dayOfWeek = 3, teacherFirst = "Мешалкина И.В."),
                lesson(5, roomFirst = "339", groupName = "21М", dateString = "21.10.2026", dayOfWeek = 3, teacherFirst = "Мешалкина И.В."),
                lesson(7, roomFirst = "339", roomSecond = "340", groupName = "23М", dateString = "21.10.2026", dayOfWeek = 3, teacherFirst = "Мешалкина И.В."),
            )
        val result = filterRoomLessonsForDate(lessons, "339", "21.10.2026")
        assertEquals(listOf(3, 4, 5, 7), result.map { it.lessonNumber })
        assertEquals(listOf("41Н", "41Н", "21М", "23М"), result.map { it.groupName })
    }
}
