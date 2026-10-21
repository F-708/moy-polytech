package com.politex.ui.screens

import com.politex.data.local.entity.LessonEntity
import com.politex.ui.util.RouteStartResolver
import com.politex.util.SubgroupPref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveRouteSourceTest {

    @Test
    fun `запиненный маршрут - тап только выбирает кабинет и не трогает маршрут`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = true,
                builderFields = BuilderFields(active = false, a = "", b = ""),
                tapRoom = "301",
            )
        assertEquals("источник остаётся запиненный маршрут", RouteSource.PINNED, res.source)
        assertEquals("301", res.target)
        assertNull("поля конструктора не трогаем", res.pairA)
        assertNull(res.pairB)
        assertFalse("маршрут не перестраивается", res.build)
    }

    @Test
    fun `ручной запиненный маршрут - тап не перестраивает`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = true,
                builderFields = BuilderFields(active = false, a = "249", b = "301"),
                tapRoom = "100",
            )
        assertEquals(RouteSource.PINNED, res.source)
        assertEquals("100", res.target)
        assertFalse("построенный маршрут живёт до Отменить", res.build)
    }

    @Test
    fun `нет маршрута и нет конструктора - тап просто выбирает`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = false, a = "", b = ""),
                tapRoom = "214",
            )
        assertEquals(RouteSource.NONE, res.source)
        assertEquals("214", res.target)
        assertFalse(res.build)
    }

    @Test
    fun `конструктор - первый тап заполняет A когда пусто, без build`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "", b = ""),
                tapRoom = "249",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertEquals("249", res.target)
        assertEquals("249", res.pairA)
        assertNull("B не трогаем", res.pairB)
        assertFalse("пока только A — не строим", res.build)
    }

    @Test
    fun `конструктор - второй тап заполняет B и сразу строит`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = ""),
                tapRoom = "301",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertEquals("301", res.target)
        assertNull("A уже заполнено — не трогаем", res.pairA)
        assertEquals("301", res.pairB)
        assertTrue("обе непустые → build", res.build)
    }

    @Test
    fun `конструктор - тап по тому же кабинету что в A не дублирует`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = ""),
                tapRoom = "249",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertEquals("249", res.target)
        assertNull("не дублируем в A", res.pairA)
        assertNull("не дублируем в B", res.pairB)
        assertFalse(res.build)
    }

    @Test
    fun `конструктор - тап по кабинету уже в B не дублирует и не перестраивает`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = "301"),
                tapRoom = "301",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertNull(res.pairA)
        assertNull(res.pairB)
        assertFalse("тот же кабинет — без build", res.build)
    }

    @Test
    fun `конструктор - тап по третьему кабинету перезаписывает B и строит`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "249", b = "301"),
                tapRoom = "100",
            )
        assertEquals(RouteSource.BUILDER, res.source)
        assertNull(res.pairA)
        assertEquals("100", res.pairB)
        assertTrue(res.build)
    }

    @Test
    fun `конструктор - пробелы в A трактуются как пусто`() {
        val res =
            resolveRouteSource(
                pinnedRouteActive = false,
                builderFields = BuilderFields(active = true, a = "   ", b = ""),
                tapRoom = "249",
            )
        assertEquals("249", res.pairA)
        assertFalse(res.build)
    }

    private fun lesson(
        n: Int,
        start: String,
        room: String,
        room2: String = "",
        split: Boolean = false,
    ) = LessonEntity(
        groupName = "11А",
        dayOfWeek = 1,
        lessonNumber = n,
        timeStart = start,
        timeEnd = start,
        subjectRaw = "Математика",
        roomFirst = room,
        teacherFirst = "",
        roomSecond = room2,
        isSplit = split,
    )

    private val day =
        listOf(
            lesson(1, "08:30", "118", "224", split = true),
            lesson(2, "09:25", "320"),
            lesson(3, "10:35", "401"),
        )

    @Test
    fun `HH1 fromRoom = кабинет предыдущего урока попадает в маршрут карты`() {
        val from = RouteStartResolver.startRoomForLesson(day, day[2], SubgroupPref.NEUTRAL)
        assertEquals("320", from)
        val route = buildMapRoute("401", from, showRoute = false)
        assertTrue("запись навигации несёт from=$from: $route", route.contains("from=320"))
        assertTrue(route.contains("room=401"))
    }

    @Test
    fun `II1 утро до всех уроков — тап по 3-му даёт from = кабинет 2-го, время не влияет`() {
        assertEquals("320", RouteStartResolver.startRoomForLesson(day, day[2], SubgroupPref.NEUTRAL))
    }

    @Test
    fun `HH1 предыдущий split + SECOND — fromRoom = roomSecond без вопроса`() {
        val from = RouteStartResolver.startRoomForLesson(day, day[1], SubgroupPref.SECOND)
        assertEquals("224", from)
        assertTrue(buildMapRoute("320", from, showRoute = false).contains("from=224"))
    }

    @Test
    fun `HH1 предыдущий split + NEUTRAL — схлопывается в roomFirst (виджеты, без диалога)`() {
        assertEquals(
            "118",
            RouteStartResolver.startRoomForLesson(day, day[1], SubgroupPref.NEUTRAL),
        )
    }

    @Test
    fun `HH1 нет предыдущего — пустая строка (главный вход), String-совместимость прежняя`() {
        assertEquals("", RouteStartResolver.startRoomForLesson(day, day[0], SubgroupPref.NEUTRAL))
        assertEquals(
            RouteStartResolver.startForLesson(day, day[1]),
            RouteStartResolver.startRoomForLesson(day, day[1], SubgroupPref.NEUTRAL),
        )
    }
}
