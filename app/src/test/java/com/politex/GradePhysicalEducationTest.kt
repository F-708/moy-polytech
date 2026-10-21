package com.politex

import com.politex.util.GradeAggregator
import com.politex.util.GradeCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradePhysicalEducationTest {
    private val delta = 1e-9
    private val pe = GradeAggregator.PHYSICAL_EDUCATION_SUBJECT

    @Test
    fun `JJ8 — физкультура распознаётся по канону, регистру, пробелам и аббревиатурам сайта`() {
        assertTrue(GradeAggregator.isPhysicalEducation(pe))
        assertTrue(GradeAggregator.isPhysicalEducation("физическая культура и здоровье"))
        assertTrue(GradeAggregator.isPhysicalEducation("  $pe  "))
        assertTrue(GradeAggregator.isPhysicalEducation("ФизКулЗ"))
        assertTrue(GradeAggregator.isPhysicalEducation("СМГрупп"))
    }

    @Test
    fun `JJ8 — другие предметы и пустая строка не физкультура`() {
        assertFalse(GradeAggregator.isPhysicalEducation("Математика"))
        assertFalse(GradeAggregator.isPhysicalEducation("Физика"))
        assertFalse(GradeAggregator.isPhysicalEducation("История"))
        assertFalse(GradeAggregator.isPhysicalEducation(""))
    }

    @Test
    fun `JJ8 — на физкультуре веса выключены всегда, даже при включённых в конфиге`() {
        assertFalse(GradeAggregator.effectiveWeightsEnabled(pe, configWeightsEnabled = true))
        assertFalse(GradeAggregator.effectiveWeightsEnabled(pe, configWeightsEnabled = false))
        assertFalse(GradeAggregator.effectiveWeightsEnabled("ФизКулЗ", configWeightsEnabled = true))
    }

    @Test
    fun `JJ8 — среднее физкультуры это простое среднее всех отметок, веса игнорируются`() {
        val byCategory = mapOf(GradeCategory.REGULAR.name to listOf(9, 7), GradeCategory.EXAM.name to listOf(4))
        val weightsEnabled = GradeAggregator.effectiveWeightsEnabled(pe, configWeightsEnabled = true)

        val avg =
            GradeAggregator.subjectAverage(
                byCategory,
                mapOf(GradeCategory.REGULAR.name to 25, GradeCategory.EXAM.name to 100),
                weightsEnabled,
            )

        assertEquals(20.0 / 3.0, avg!!, delta)
    }

    @Test
    fun `JJ8 — отметки физкультуры идут как обычные, пустая карта весов не даёт null`() {
        val byCategory = mapOf(GradeCategory.REGULAR.name to listOf(8, 10, 6))

        val avg =
            GradeAggregator.subjectAverage(
                byCategory,
                emptyMap(),
                GradeAggregator.effectiveWeightsEnabled(pe, configWeightsEnabled = true),
            )

        assertEquals(8.0, avg!!, delta)
    }

    @Test
    fun `JJ7 — per-subject тогл весов, выкл = простое среднее, вкл = взвешенное`() {
        val byCategory = mapOf(GradeCategory.REGULAR.name to listOf(8), GradeCategory.EXAM.name to listOf(6))
        val weights = mapOf(GradeCategory.REGULAR.name to 70, GradeCategory.EXAM.name to 30)

        val off =
            GradeAggregator.subjectAverage(
                byCategory,
                weights,
                GradeAggregator.effectiveWeightsEnabled("Математика", configWeightsEnabled = false),
            )
        val on =
            GradeAggregator.subjectAverage(
                byCategory,
                weights,
                GradeAggregator.effectiveWeightsEnabled("Математика", configWeightsEnabled = true),
            )

        assertEquals(7.0, off!!, delta)
        assertEquals(7.4, on!!, delta)
    }

    @Test
    fun `JJ7 — без конфигурации предмета веса включены по умолчанию`() {
        assertTrue(GradeAggregator.effectiveWeightsEnabled("Математика", configWeightsEnabled = true))
    }

    @Test
    fun `JJ7 — subjectAverage без оценок отдаёт null в обоих режимах`() {
        val weights = mapOf(GradeCategory.REGULAR.name to 50, GradeCategory.EXAM.name to 50)
        assertNull(GradeAggregator.subjectAverage(emptyMap(), weights, weightsEnabled = true))
        assertNull(GradeAggregator.subjectAverage(emptyMap(), weights, weightsEnabled = false))
    }
}
