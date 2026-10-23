package com.politex

import com.politex.ui.screens.buildGroupScheduleRoute
import com.politex.ui.screens.buildMapRoute
import com.politex.ui.screens.buildScrollRoute
import com.politex.ui.screens.buildTeacherRoute
import com.politex.ui.screens.ownerTabIndex
import com.politex.ui.screens.scrollRoutePattern
import com.politex.ui.screens.tabIndex
import com.politex.ui.screens.tabSlideDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class NavRouteTest {
    private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")

    @Test
    fun `карта из меню — голый route без параметров`() {
        assertEquals("map", buildMapRoute(null, null, false))
    }

    @Test
    fun `карта с кабинетом — только room`() {
        assertEquals("map?room=214", buildMapRoute("214", null, false))
    }

    @Test
    fun `from пустой строкой сохраняется — это маршрут от главного входа (R1)`() {
        assertEquals("map?room=321&from=", buildMapRoute("321", "", false))
    }

    @Test
    fun `карта из расписания — room и from`() {
        assertEquals("map?room=321&from=101", buildMapRoute("321", "101", false))
    }

    @Test
    fun `виджет NowNext — явный флаг маршрута (N8)`() {
        assertEquals("map?room=321&route=true", buildMapRoute("321", null, true))
    }

    @Test
    fun `виджет Today — маршрута нет (N8)`() {
        assertFalse(buildMapRoute("321", null, false).contains("route="))
    }

    @Test
    fun `X4 карта из календаря-архива — кабинет без маршрута (from=null, route=false)`() {
        val route = buildMapRoute("214", null, false)
        assertEquals("map?room=214", route)
        assertFalse(route.contains("from="))
        assertFalse(route.contains("route="))
    }

    @Test
    fun `ФИО с пробелами и кириллицей — round-trip`() {
        val name = "Иванова Мария Петровна"
        val route = buildTeacherRoute(name)
        assertTrue(route.startsWith("teacher?name="))
        assertFalse(route.contains(" "))
        assertEquals(name, decode(route.substringAfter("teacher?name=")))
    }

    @Test
    fun `группа с кириллической буквой — round-trip`() {
        val group = "41О"
        val route = buildGroupScheduleRoute(group)
        assertEquals("group_schedule?group=" + route.substringAfter("group="), route)
        assertEquals(group, decode(route.substringAfter("group=")))
    }

    @Test
    fun `расписание группы без группы — выбор группы (picker)`() {
        assertEquals("group_schedule", buildGroupScheduleRoute(null))
        assertEquals("group_schedule", buildGroupScheduleRoute(""))
    }

    @Test
    fun `scroll-аргумент — только когда виджет его прислал`() {
        assertEquals("schedule", buildScrollRoute("schedule", null))
        assertEquals("schedule", buildScrollRoute("schedule", ""))
        assertEquals("schedule?scroll=CURRENT_LESSON", buildScrollRoute("schedule", "CURRENT_LESSON"))
        assertEquals("bells?scroll=CURRENT_BELL", buildScrollRoute("bells", "CURRENT_BELL"))
    }

    @Test
    fun `паттерн вкладки объявляет scroll как опциональный аргумент`() {
        assertEquals("schedule?scroll={scroll}", scrollRoutePattern("schedule"))
        assertEquals("bells?scroll={scroll}", scrollRoutePattern("bells"))
    }

    @Test
    fun `X7 backAction - корень college ведёт на schedule`() {
        assertEquals(com.politex.ui.screens.BackAction.TO_SCHEDULE, com.politex.ui.screens.backAction("college"))
    }

    @Test
    fun `X7 backAction - корни schedule lessons bells - выход из приложения`() {
        assertEquals(com.politex.ui.screens.BackAction.EXIT, com.politex.ui.screens.backAction("schedule"))
        assertEquals(com.politex.ui.screens.BackAction.EXIT, com.politex.ui.screens.backAction("lessons"))
        assertEquals(com.politex.ui.screens.BackAction.EXIT, com.politex.ui.screens.backAction("bells"))
    }

    @Test
    fun `X7 backAction - query вкладки отбрасывается (паттерн route)`() {
        assertEquals(com.politex.ui.screens.BackAction.EXIT, com.politex.ui.screens.backAction("schedule?scroll={scroll}"))
        assertEquals(com.politex.ui.screens.BackAction.EXIT, com.politex.ui.screens.backAction("bells?scroll=CURRENT_BELL"))
    }

    @Test
    fun `X7 backAction - глубокие экраны и null - NONE (системный popBackStack)`() {
        assertEquals(com.politex.ui.screens.BackAction.NONE, com.politex.ui.screens.backAction("map?room=214"))
        assertEquals(com.politex.ui.screens.BackAction.NONE, com.politex.ui.screens.backAction("teacher?name=X"))
        assertEquals(com.politex.ui.screens.BackAction.NONE, com.politex.ui.screens.backAction("group_schedule"))
        assertEquals(com.politex.ui.screens.BackAction.NONE, com.politex.ui.screens.backAction("settings"))
        assertEquals(com.politex.ui.screens.BackAction.NONE, com.politex.ui.screens.backAction(null))
    }

    @Test
    fun `CC11 tabIndex - порядок вкладок и отброс query`() {
        assertEquals(0, tabIndex("schedule"))
        assertEquals(0, tabIndex("schedule?scroll={scroll}"))
        assertEquals(1, tabIndex("lessons"))
        assertEquals(2, tabIndex("bells"))
        assertEquals(2, tabIndex("bells?scroll=CURRENT_BELL"))
        assertEquals(3, tabIndex("college"))
    }

    @Test
    fun `CC11 tabIndex - не-таб и null дают -1`() {
        assertEquals(-1, tabIndex("map?room=214"))
        assertEquals(-1, tabIndex("college/settings"))
        assertEquals(-1, tabIndex(null))
    }

    @Test
    fun `CC11 tabSlideDir - влево-переход +1, вправо -1, не-таб 0`() {
        assertEquals(1, tabSlideDir(0, 1))
        assertEquals(1, tabSlideDir(2, 3))
        assertEquals(-1, tabSlideDir(3, 2))
        assertEquals(-1, tabSlideDir(1, 0))
        assertEquals(0, tabSlideDir(1, 1))
        assertEquals(0, tabSlideDir(-1, 2))
        assertEquals(0, tabSlideDir(2, -1))
    }

    @Test
    fun `DD4 ownerTabIndex - под-экраны Другого маппятся на вкладку 3`() {
        assertEquals(3, ownerTabIndex("college/settings"))
        assertEquals(3, ownerTabIndex("college/specialty"))
        assertEquals(3, ownerTabIndex("college/teachers"))
        assertEquals(3, ownerTabIndex("college/students"))
        assertEquals(3, ownerTabIndex("teacher?name=%D0%98%D0%B2%D0%B0%D0%BD%D0%BE%D0%B2%D0%B0"))
        assertEquals(3, ownerTabIndex("group_schedule?group=41%D0%9E"))
        assertEquals(3, ownerTabIndex("college"))
    }

    @Test
    fun `DD4 ownerTabIndex - корни вкладок сохраняют индекс, карта и неизвестное -1`() {
        assertEquals(0, ownerTabIndex("schedule"))
        assertEquals(0, ownerTabIndex("schedule?scroll={scroll}"))
        assertEquals(1, ownerTabIndex("lessons"))
        assertEquals(2, ownerTabIndex("bells?scroll=CURRENT_BELL"))
        assertEquals(-1, ownerTabIndex("map?room=214"))
        assertEquals(-1, ownerTabIndex("map"))
        assertEquals(-1, ownerTabIndex("unknown_route"))
        assertEquals(-1, ownerTabIndex(null))
    }

    @Test
    fun `DD4 tabSlideDir с под-экранов - group_schedule и settings влево на bells дают -1`() {
        assertEquals(-1, tabSlideDir(ownerTabIndex("group_schedule?group=41%D0%9E"), ownerTabIndex("bells")))
        assertEquals(-1, tabSlideDir(ownerTabIndex("college/settings"), ownerTabIndex("bells")))
        assertEquals(0, tabSlideDir(ownerTabIndex("college"), ownerTabIndex("college/settings")))
        assertEquals(0, tabSlideDir(ownerTabIndex("map?room=214"), ownerTabIndex("schedule")))
    }
}
