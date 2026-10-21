package com.politex

import com.politex.data.model.CollegeBellSchedule
import com.politex.data.model.TaskType
import com.politex.util.GroupParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step2UiLogicTest {
    @Test
    fun `test bell schedule business rules`() {
        assertEquals(12, CollegeBellSchedule.BELLS.size)
        assertTrue(CollegeBellSchedule.DISCLAIMER.contains("расписание звонков колледжа МГПК на 2026 г."))

        val firstLesson = CollegeBellSchedule.getTimeForLessonNumber(1, 1)
        assertEquals("08:15", firstLesson.first)
        assertEquals("09:00", firstLesson.second)

        val twelfthLesson = CollegeBellSchedule.getTimeForLessonNumber(12, 1)
        assertEquals("18:50", twelfthLesson.first)
        assertEquals("19:35", twelfthLesson.second)

        val slotAt9am = CollegeBellSchedule.getCurrentSlot(9 * 60, dayOfWeek = 2)
        assertNotNull(slotAt9am)
        assertEquals(1, slotAt9am?.lessonNumber)
    }

    @Test
    fun `test task types availability per course`() {
        val course1Tasks = TaskType.getAvailableForCourse(1)
        assertFalse(course1Tasks.contains(TaskType.COURSE_WORK))
        assertFalse(course1Tasks.contains(TaskType.DIPLOMA))
        assertTrue(course1Tasks.contains(TaskType.HOMEWORK))

        val course2Tasks = TaskType.getAvailableForCourse(2)
        assertTrue(course2Tasks.contains(TaskType.COURSE_WORK))
        assertFalse(course2Tasks.contains(TaskType.DIPLOMA))

        val course4Tasks = TaskType.getAvailableForCourse(4)
        assertTrue(course4Tasks.contains(TaskType.COURSE_WORK))
        assertTrue(course4Tasks.contains(TaskType.DIPLOMA))
        assertEquals("ДЗ", TaskType.HOMEWORK.badge)
    }

    @Test
    fun `test group parsing saturday and diploma rules`() {
        val group11E = GroupParser.parse("11Э")
        assertNotNull(group11E)
        assertTrue(group11E?.hasSaturdayClasses == true)
        assertFalse(group11E?.canHaveDiploma == true)

        val group41O = GroupParser.parse("41О")
        assertNotNull(group41O)
        assertFalse(group41O?.hasSaturdayClasses == true)
        assertTrue(group41O?.canHaveCourseWork == true)
        assertTrue(group41O?.canHaveDiploma == true)
    }
}
