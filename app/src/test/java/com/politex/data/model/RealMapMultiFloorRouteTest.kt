package com.politex.data.model

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealMapMultiFloorRouteTest {
    private val mapData: CollegeMapData by lazy {
        CollegeMapData.load(ApplicationProvider.getApplicationContext())
    }

    private fun floorsMap(): Map<String, FloorMap> = CollegeMap.Floor.entries.associate { it.id to mapData.floor(it.id) }

    private fun room(number: String): MapRoom {
        val floorId = CollegeMap.floorForRoom(number)?.id ?: error("$number: неизвестный этаж")
        return mapData.floor(floorId).room(number) ?: error("комната $number не найдена на этаже $floorId")
    }

    @Test
    fun `данные карты загружаются из ассета`() {

        assertFalse("карта не должна быть пустой (ассет не загрузился?)", mapData.isEmpty)
        assertTrue("на 1 этаже должны быть комнаты", mapData.floor("1").rooms.isNotEmpty())
    }

    @Test
    fun `usable-разметка и ручные links лестниц читаются из карты`() {
        val floors = floorsMap()
        val allStairs = floors.values.flatMap { it.entrances }.filter { it.kind == "stairs" }
        assertTrue("лестниц должно быть несколько", allStairs.size >= 8)

        val withLinks = allStairs.filter { it.usable && it.links.isNotEmpty() }
        assertTrue(
            "лестниц с ручными links должно быть большинство (разметка заказчика), было ${withLinks.size}",
            withLinks.size >= 8,
        )

        val ids = allStairs.map { it.id }.toSet()
        val dangling = withLinks.flatMap { it.links }.filter { it !in ids }
        assertTrue("битые links: $dangling", dangling.isEmpty())
    }

    @Test
    fun `маршрут 1 этаж - 2 этаж строится через пригодную лестницу`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("131"), room("241"), floorsMap())
        assertNotNull("маршрут 131→241 должен строиться", r)
        assertTrue(r!!.crossesFloors)
        assertEquals(listOf("1", "2"), r.floorPaths.map { it.floorId })
        assertTrue(r.floorPaths.all { it.points.size >= 2 })
        assertTrue("дистанция положительная", r.distance > 0f)
    }

    @Test
    fun `маршрут 1 этаж - 3 этаж проходит через два этажа`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("136"), room("332"), floorsMap())
        assertNotNull("маршрут 136→332 должен строиться", r)
        assertEquals(listOf("1", "2", "3-4"), r!!.floorPaths.map { it.floorId })
    }

    @Test
    fun `маршрут внутри одного этажа — один сегмент`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("238"), room("241"), floorsMap())
        assertNotNull(r)
        assertFalse(r!!.crossesFloors)
        assertEquals(1, r.floorPaths.size)
    }

    @Test
    fun `маршрут по аварийной лестнице невозможен`() {

        val r = CollegeRouteFinder.findMultiFloorRoute(room("118"), room("223"), floorsMap())
        if (r != null) {
            val stairsUsed =
                r.floorPaths.flatMap { fp ->
                    floorsMap()[fp.floorId]
                        ?.entrances
                        ?.filter { it.kind == "stairs" && !it.usable }
                        ?.filter { s -> fp.points.any { p -> Math.abs(p.x - s.x) < 1e-3f && Math.abs(p.y - s.y) < 1e-3f } }
                        ?.map { it.id } ?: emptyList()
                }
            assertTrue("аварийные лестницы не должны использоваться: $stairsUsed", stairsUsed.isEmpty())
        }
    }

    @Test
    fun `главный вход достижим как точка маршрута`() {
        val f1 = mapData.floor("1")
        val main = f1.entrances.firstOrNull { it.kind == "main" }
        assertNotNull("главный вход должен быть в данных", main)

        val startRoom =
            MapRoom(
                id = "entrance",
                number = "100",
                title = "",
                note = "",
                status = "normal",
                x = main!!.x,
                y = main.y,
                w = 0.01f,
                h = 0.01f,
            )
        val r = CollegeRouteFinder.findMultiFloorRoute(startRoom, room("301"), floorsMap())
        assertNotNull("маршрут от главного входа до 301 должен строиться", r)
    }

    @Test
    fun `W40c - маршрут 309 на 405 строится через лестницу у 335`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("309"), room("405"), floorsMap())
        assertNotNull("309→405 должен строиться (links e_8yy3eev↔e_wf8mlfr)", r)
        assertTrue(r!!.crossesFloors)

        assertEquals(listOf("3-4", "2", "3-4", "3-4"), r.floorPaths.map { it.floorId })
    }

    @Test
    fun `W40a - маршрут 107 на 123 идёт вдоль коридора без крюка`() {
        val r = CollegeRouteFinder.findMultiFloorRoute(room("107"), room("123"), floorsMap())
        assertNotNull("107→123 должен строиться", r)
        assertFalse(r!!.crossesFloors)
        assertTrue("длина должна быть ~0.21 без крюка (было ~0.39), получилось ${r.distance}", r.distance < 0.25f)

        val pts = r.floorPaths[0].points
        assertTrue(pts.size >= 3)
        assertTrue(
            "путь должен идти вдоль коридора y≈0.49, точки ${pts.take(3)}",
            pts.drop(1).dropLast(1).all { it.y in 0.44f..0.56f },
        )
    }

    @Test
    fun `W40a - дверь комнаты читается из ассета`() {
        val r107 = mapData.floor("1").room("107")
        assertNotNull(r107)
        assertNotNull("у 107 должна быть дверь в разметке", r107!!.door)
    }

    @Test
    fun `X26 - все комнаты без двери достижимы от главного входа`() {
        val floors = floorsMap()
        val main = mapData.floor("1").entrances.first { it.kind == "main" }
        val entrance =
            MapRoom(
                id = "entrance", number = "100", title = "", note = "", status = "normal",
                x = main.x, y = main.y, w = 0.01f, h = 0.01f,
            )
        val doorNull =
            CollegeMap.Floor.entries.flatMap { f ->
                mapData.floor(f.id).rooms.filter { it.number.isNotBlank() && it.status != "nonexistent" && it.door == null }
            }
        assertTrue("в данных должны быть комнаты без двери", doorNull.isNotEmpty())
        for (r in doorNull) {
            val route = CollegeRouteFinder.findMultiFloorRoute(r, entrance, floors)
            assertNotNull("комната ${r.number} (door=null) должна быть достижима от входа", route)
        }
    }

    @Test
    fun `X23 - маршрут стартует и финиширует в двери когда она есть`() {
        val floors = floorsMap()
        val from = room("101")
        val to = room("103")
        assertNotNull("у 101 должна быть дверь", from.door)
        assertNotNull("у 103 должна быть дверь", to.door)
        val r = CollegeRouteFinder.findMultiFloorRoute(from, to, floors)
        assertNotNull("101→103 должен строиться", r)
        val firstPt = r!!.floorPaths.first().points.first()
        val lastPt = r.floorPaths.last().points.last()
        assertEquals(from.door!!.x, firstPt.x, 1e-4f)
        assertEquals(from.door!!.y, firstPt.y, 1e-4f)
        assertEquals(to.door!!.x, lastPt.x, 1e-4f)
        assertEquals(to.door!!.y, lastPt.y, 1e-4f)
    }

    @Test
    fun `X23 - маршрут стартует в центре когда двери нет`() {
        val floors = floorsMap()
        val from = room("107-1")
        assertTrue("у 107-1 нет двери", from.door == null)
        val to = room("101")
        val r = CollegeRouteFinder.findMultiFloorRoute(from, to, floors)
        assertNotNull("107-1→101 должен строиться", r)
        val firstPt = r!!.floorPaths.first().points.first()
        assertEquals("старт в центре (door=null)", from.x, firstPt.x, 1e-4f)
        assertEquals(from.y, firstPt.y, 1e-4f)
    }

    @Test
    fun `Y7 - полилинии маршрутов ортогональны в plan-px и не пробивают кабинеты`() {
        val floors = floorsMap()
        val main = mapData.floor("1").entrances.first { it.kind == "main" }
        val entrance =
            MapRoom(
                id = "entrance", number = "100", title = "", note = "", status = "normal",
                x = main.x, y = main.y, w = 0.01f, h = 0.01f,
            )
        val pairs = listOf("107" to "123", "131" to "241", "309" to "405", "331" to "301")
        for ((a, b) in pairs) {
            val from = room(a)
            val r = CollegeRouteFinder.findMultiFloorRoute(from, room(b), floors)
            assertNotNull("маршрут $a→$b должен строиться", r)
            for (fp in r!!.floorPaths) {
                val floor = floors.getValue(fp.floorId)
                val poly = CollegeRouteFinder.routePolyline(fp.points, floor)
                for (i in 1 until poly.size) {
                    val dx = Math.abs(poly[i].x - poly[i - 1].x) * CollegeRouteFinder.PLAN_ASPECT
                    val dy = Math.abs(poly[i].y - poly[i - 1].y)
                    assertTrue(
                        "$a→$b эт ${fp.floorId}: сегмент $i (${poly[i - 1]}→${poly[i]}) не ортогонален в plan-px",
                        dx < 1e-4f || dy < 1e-4f,
                    )
                }
                val walls =
                    floor.rooms
                        .filter {
                            it.status != "nonexistent" && it.status != "passage" && it.w > 0f && it.h > 0f &&
                                it.number != a && it.number != b
                        }.map { floatArrayOf(it.x - it.w / 2f, it.y - it.h / 2f, it.x + it.w / 2f, it.y + it.h / 2f) }
                for (i in 1 until poly.size) {
                    for (w in walls) {
                        assertFalse(
                            "$a→$b эт ${fp.floorId}: нога $i пробивает кабинет",
                            CollegeRouteFinder.segCrossesRect(poly[i - 1].x, poly[i - 1].y, poly[i].x, poly[i].y, w),
                        )
                    }
                }
            }
        }
        assertNotNull(entrance)
    }
}
