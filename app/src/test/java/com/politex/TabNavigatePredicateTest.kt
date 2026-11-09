package com.politex

import com.politex.ui.screens.buildScrollRoute
import com.politex.ui.screens.buildMapRoute
import com.politex.ui.screens.buildTeacherRoute
import com.politex.ui.screens.routeQueryArgs
import com.politex.ui.screens.scrollRoutePattern
import com.politex.ui.screens.shouldNavigateToTab
import com.politex.ui.viewmodel.ARG_SCROLL
import com.politex.ui.viewmodel.ROUTE_BELLS
import com.politex.ui.viewmodel.ROUTE_COLLEGE
import com.politex.ui.viewmodel.ROUTE_LESSONS
import com.politex.ui.viewmodel.ROUTE_MAP
import com.politex.ui.viewmodel.ROUTE_SCHEDULE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Переход не нужен, мы уже здесь» — предикат, которым [shouldNavigateToTab] защищает
 * повторное нажатие на открытую вкладку от пересоздания экрана.
 */
class TabNavigatePredicateTest {
    private val emptyArgs = emptyMap<String, String?>()

    @Test
    fun `та же вкладка без query - перехода нет`() {
        assertFalse(shouldNavigateToTab(ROUTE_BELLS, emptyArgs, ROUTE_BELLS))
        assertFalse(shouldNavigateToTab(ROUTE_SCHEDULE, emptyArgs, ROUTE_SCHEDULE))
        assertFalse(shouldNavigateToTab(ROUTE_COLLEGE, emptyArgs, ROUTE_COLLEGE))
    }

    @Test
    fun `шаблон маршрута со scroll сравнивается по базе`() {
        // У табов этот шаблон и лежит в currentDestination.route.
        assertFalse(shouldNavigateToTab(scrollRoutePattern(ROUTE_BELLS), emptyArgs, ROUTE_BELLS))
        assertFalse(shouldNavigateToTab(scrollRoutePattern(ROUTE_SCHEDULE), emptyArgs, ROUTE_SCHEDULE))
    }

    @Test
    fun `другая вкладка - переход нужен`() {
        assertTrue(shouldNavigateToTab(ROUTE_BELLS, emptyArgs, ROUTE_COLLEGE))
        assertTrue(shouldNavigateToTab(ROUTE_LESSONS, emptyArgs, ROUTE_SCHEDULE))
        assertTrue(shouldNavigateToTab(null, emptyArgs, ROUTE_SCHEDULE))
    }

    @Test
    fun `под-экран Другого не считается открытой вкладкой`() {
        assertTrue(shouldNavigateToTab("college/settings", emptyArgs, ROUTE_COLLEGE))
        assertTrue(shouldNavigateToTab(ROUTE_COLLEGE, emptyArgs, "college/settings"))
    }

    @Test
    fun `запрос прокрутки на открытой вкладке - не повод пересоздавать экран`() {
        val target = buildScrollRoute(ROUTE_BELLS, "CURRENT_BELL")
        assertFalse(shouldNavigateToTab(scrollRoutePattern(ROUTE_BELLS), emptyArgs, target))
        assertFalse(shouldNavigateToTab(ROUTE_BELLS, emptyArgs, target))
    }

    @Test
    fun `другие аргументы того же экрана - переход нужен`() {
        assertTrue(
            shouldNavigateToTab(
                ROUTE_MAP,
                mapOf("room" to "214", "from" to null, "route" to "false"),
                buildMapRoute("321", null, false),
            ),
        )
        assertFalse(
            shouldNavigateToTab(
                ROUTE_MAP,
                mapOf("room" to "214", "from" to null, "route" to "false"),
                buildMapRoute("214", null, false),
            ),
        )
    }

    @Test
    fun `неизвестное текущее значение аргумента трактуется как переход`() {
        assertTrue(shouldNavigateToTab(ROUTE_MAP, null, buildMapRoute("321", null, false)))
        assertTrue(shouldNavigateToTab(ROUTE_MAP, emptyMap(), buildMapRoute("321", null, false)))
    }

    @Test
    fun `query-параметры разбираются и декодируются`() {
        assertEquals(mapOf(ARG_SCROLL to "CURRENT_BELL"), routeQueryArgs(buildScrollRoute(ROUTE_BELLS, "CURRENT_BELL")))
        assertEquals(emptyMap<String, String>(), routeQueryArgs(ROUTE_BELLS))
        val teacher = routeQueryArgs(buildTeacherRoute("Иванова Мария Петровна"))
        assertEquals("Иванова Мария Петровна", teacher["name"])
    }

    @Test
    fun `обычный переход по табу по-прежнему делается`() {
        assertTrue(shouldNavigateToTab(ROUTE_SCHEDULE, emptyArgs, ROUTE_BELLS))
        assertTrue(shouldNavigateToTab(ROUTE_BELLS, emptyArgs, ROUTE_LESSONS))
        assertTrue(shouldNavigateToTab(ROUTE_LESSONS, emptyArgs, ROUTE_COLLEGE))
    }
}