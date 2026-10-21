package com.politex

import com.politex.ui.screens.LessonsSection
import com.politex.ui.screens.targetHubSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRoutingTest {

    @Test
    fun `входящий материал всегда открывает секцию материалов`() {
        assertEquals(LessonsSection.MATERIALS, targetHubSection(LessonsSection.TASKS, true))
        assertEquals(LessonsSection.MATERIALS, targetHubSection(LessonsSection.GRADES, true))
        assertEquals(LessonsSection.MATERIALS, targetHubSection(LessonsSection.MATERIALS, true))
    }

    @Test
    fun `без входящего материала выбор секции не меняется`() {
        assertEquals(LessonsSection.TASKS, targetHubSection(LessonsSection.TASKS, false))
        assertEquals(LessonsSection.GRADES, targetHubSection(LessonsSection.GRADES, false))
        assertEquals(LessonsSection.MATERIALS, targetHubSection(LessonsSection.MATERIALS, false))
    }

    @Test
    fun `teacher mode не принимает входящие материалы`() {
        assertTrue(acceptIntake("standard", false))
        assertTrue(acceptIntake("admin", false))
        assertFalse(acceptIntake("standard", true))
        assertFalse(acceptIntake("teacher", false))
    }

    @Test
    fun `переход на уже открытые уроки не пересоздаёт экран`() {
        assertFalse(shouldNavigateToLessons("lessons"))
        assertFalse(shouldNavigateToLessons("lessons?scroll=TOP"))
        assertTrue(shouldNavigateToLessons("schedule"))
        assertTrue(shouldNavigateToLessons("college/settings"))
        assertTrue(shouldNavigateToLessons(null))
    }
}