package com.politex

import com.politex.data.model.GroupInfo
import com.politex.ui.screens.subjectListFor
import com.politex.util.MpkCurriculum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectListForTest {
    private val group =
        GroupInfo(
            rawName = "41О",
            course = 4,
            groupNumber = 1,
            specialtyCode = 'О',
        )

    @Test
    fun `куррикулум группы полностью входит в список`() {
        val curriculum = MpkCurriculum.getSubjects(group.specialtyCode, group.course)
        val result = subjectListFor(group, emptyList())
        assertTrue("все предметы куррикулума на месте", result.containsAll(curriculum))
    }

    @Test
    fun `использованные предметы добавляются к куррикулуму`() {
        val result = subjectListFor(group, listOf("Репетитор", "ТОЭ"))
        assertTrue(result.contains("Репетитор"))
        assertTrue(result.contains("ТОЭ"))
    }

    @Test
    fun `список distinct sorted и без пустых`() {
        val curriculum = MpkCurriculum.getSubjects(group.specialtyCode, group.course)
        val used = listOf("Репетитор", curriculum.first(), "", "   ", "Репетитор")
        val result = subjectListFor(group, used)

        assertEquals("без дублей", result.size, result.distinct().size)
        assertTrue("пустые отфильтрованы", result.none { it.isBlank() })
        assertEquals("отсортировано", result.sorted(), result)
    }

    @Test
    fun `предмет уже из куррикулума не дублируется`() {
        val curriculum = MpkCurriculum.getSubjects(group.specialtyCode, group.course)
        val dup = curriculum.first()
        val result = subjectListFor(group, listOf(dup, dup))
        assertEquals(1, result.count { it == dup })
    }
}
