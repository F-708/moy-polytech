package com.politex

import com.politex.ui.components.FirstRunStep
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.ROUTE_LESSONS
import com.politex.ui.viewmodel.tabsForMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeacherModeTabsTest {

    @Test
    fun `студенческий режим - все четыре вкладки`() {
        assertEquals(
            listOf(AppTab.SCHEDULE, AppTab.TASKS, AppTab.BELLS, AppTab.COLLEGE),
            tabsForMode(isTeacher = false),
        )
    }

    @Test
    fun `режим преподавателя - без вкладки Уроки`() {
        val tabs = tabsForMode(isTeacher = true)
        assertEquals(listOf(AppTab.SCHEDULE, AppTab.BELLS, AppTab.COLLEGE), tabs)
        assertFalse(tabs.any { it.route == ROUTE_LESSONS })
    }

    @Test
    fun `порядок вкладок преподавателя совпадает с порядком AppTab`() {
        assertEquals(AppTab.entries.filter { it != AppTab.TASKS }, tabsForMode(isTeacher = true))
    }

    @Test
    fun `маршруты вкладок уникальны в обоих режимах`() {
        listOf(false, true).forEach { mode ->
            val routes = tabsForMode(mode).map { it.route }
            assertEquals(routes.size, routes.distinct().size)
        }
    }

    @Test
    fun `у каждой вкладки непустой заголовок`() {
        (tabsForMode(true) + tabsForMode(false)).forEach { assertTrue(it.title.isNotBlank()) }
    }

    @Test
    fun `шаги онбординга содержат ветку преподавателя`() {
        assertEquals(
            listOf("GROUP", "SUBGROUP", "TEACHER"),
            FirstRunStep.entries.map { it.name },
        )
    }
}
