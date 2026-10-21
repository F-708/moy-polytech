package com.politex

import com.politex.data.model.TaskType
import com.politex.util.GroupParser
import com.politex.util.MpkCurriculum
import com.politex.util.SubjectFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Step1LogicTest {
    @Test
    fun testGroupParser_validGroups() {
        val group11E = GroupParser.parse("11Э")
        assertNotNull(group11E)
        assertEquals(1, group11E!!.course)
        assertEquals(1, group11E.groupNumber)
        assertEquals('Э', group11E.specialtyCode)
        assertTrue(group11E.hasSaturdayClasses)
        assertFalse(group11E.canHaveCourseWork)
        assertFalse(group11E.canHaveDiploma)

        val group23O = GroupParser.parse("23О")
        assertNotNull(group23O)
        assertEquals(2, group23O!!.course)
        assertEquals(3, group23O.groupNumber)
        assertEquals('О', group23O.specialtyCode)
        assertFalse(group23O.hasSaturdayClasses)
        assertTrue(group23O.canHaveCourseWork)
        assertFalse(group23O.canHaveDiploma)

        val group31T = GroupParser.parse("31Т")
        assertNotNull(group31T)
        assertEquals(3, group31T!!.course)
        assertEquals(1, group31T.groupNumber)
        assertEquals('Т', group31T.specialtyCode)
        assertFalse(group31T.hasSaturdayClasses)
        assertTrue(group31T.canHaveCourseWork)
        assertFalse(group31T.canHaveDiploma)

        val group41O = GroupParser.parse("41О")
        assertNotNull(group41O)
        assertEquals(4, group41O!!.course)
        assertEquals(1, group41O.groupNumber)
        assertEquals('О', group41O.specialtyCode)
        assertFalse(group41O.hasSaturdayClasses)
        assertTrue(group41O.canHaveCourseWork)
        assertTrue(group41O.canHaveDiploma)
    }

    @Test
    fun testGroupParser_latinInputNormalization() {
        val groupWithLatin = GroupParser.parse("41O")
        assertNotNull(groupWithLatin)
        assertEquals('О', groupWithLatin!!.specialtyCode)
        assertEquals(4, groupWithLatin.course)
    }

    @Test
    fun testGroupParser_invalidGroups() {
        assertNull(GroupParser.parse("51О"))
        assertNull(GroupParser.parse("01О"))
        assertNull(GroupParser.parse("40О"))
        assertNull(GroupParser.parse("41Z"))
        assertNull(GroupParser.parse("random"))
        assertNull(GroupParser.parse(""))
    }

    @Test
    fun testMpkCurriculum_allTenSpecialtiesExist() {
        val requiredSpecialties = listOf('О', 'Н', 'Э', 'П', 'Т', 'Г', 'Д', 'А', 'С', 'М')
        assertEquals(10, requiredSpecialties.size)

        for (code in requiredSpecialties) {
            val specialty = MpkCurriculum.getSpecialty(code)
            assertNotNull("Специальность '$code' должна быть в справочнике", specialty)
            assertTrue(specialty!!.fullName.isNotBlank())
            assertTrue(specialty.cipher.isNotBlank())

            val courses = specialty.subjectsByCourse.keys
            assertTrue("У специальности '$code' должен быть хотя бы один курс", courses.isNotEmpty())
            for (course in courses) {
                val subjects = specialty.subjectsByCourse[course]!!
                if (course in specialty.incompleteCourses) {
                    assertTrue(
                        "Курс $course специальности '$code' помечен повреждённым, список должен быть пустым",
                        subjects.isEmpty(),
                    )
                } else {
                    assertTrue("Список предметов '$code' курса $course не должен быть пустым", subjects.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun testSubjectFormatter_shortNames() {
        assertEquals("Охр. труда", SubjectFormatter.getShortName("Охрана труда"))
        assertEquals("Инф. технол.", SubjectFormatter.getShortName("Информационные технологии"))
        assertEquals("Электротех. мат.", SubjectFormatter.getShortName("Электротехнические материалы"))
        assertEquals("ТОЭ", SubjectFormatter.getShortName("Теоретические основы электротехники"))
        assertEquals("Физкультура", SubjectFormatter.getShortName("Физическая культура и здоровье"))
        assertEquals("Основы права", SubjectFormatter.getShortName("Основы права"))
    }

    @Test
    fun testSubjectFormatter_acronyms() {
        assertEquals("ОП", SubjectFormatter.getAcronym("Основы права"))
        assertEquals("ТОЭ", SubjectFormatter.getAcronym("Теоретические основы электротехники"))
        assertEquals("ОТ", SubjectFormatter.getAcronym("Охрана труда"))
        assertEquals("ИТ", SubjectFormatter.getAcronym("Информационные технологии"))
        assertEquals("ФКиЗ", SubjectFormatter.getAcronym("Физическая культура и здоровье"))
        assertEquals("ЭТМ", SubjectFormatter.getAcronym("Электротехнические материалы"))
    }

    @Test
    fun testSubjectFormatter_normalizationAndFuzzyMatch() {
        val raw1 = "лек. Охрана труда ауд. 203"
        assertEquals("Охрана труда", SubjectFormatter.normalize(raw1))

        val raw2 = "Информационные технологии (1 п/г)"
        assertEquals("Информационные технологии", SubjectFormatter.normalize(raw2))

        val raw3 = "Теоретические основы электротехники"
        assertEquals("Теоретические основы электротехники", SubjectFormatter.normalize(raw3))
    }

    @Test
    fun testTaskType_availablePerCourse() {
        val course1Tasks = TaskType.getAvailableForCourse(1)
        assertFalse(course1Tasks.contains(TaskType.COURSE_WORK))
        assertFalse(course1Tasks.contains(TaskType.DIPLOMA))

        val course2Tasks = TaskType.getAvailableForCourse(2)
        assertTrue(course2Tasks.contains(TaskType.COURSE_WORK))
        assertFalse(course2Tasks.contains(TaskType.DIPLOMA))

        val course4Tasks = TaskType.getAvailableForCourse(4)
        assertTrue(course4Tasks.contains(TaskType.COURSE_WORK))
        assertTrue(course4Tasks.contains(TaskType.DIPLOMA))
    }
}
