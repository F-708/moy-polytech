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
class RealMapRouteOrthogonalityTest {
    private val mapData: CollegeMapData by lazy {
        CollegeMapData.load(ApplicationProvider.getApplicationContext())
    }

    private fun floorsMap(): Map<String, FloorMap> = CollegeMap.Floor.entries.associate { it.id to mapData.floor(it.id) }

    private fun room(number: String): MapRoom {
        val floorId = CollegeMap.floorForRoom(number)?.id ?: error("$number: неизвестный этаж")
        return mapData.floor(floorId).room(number) ?: error("комната $number не найдена на этаже $floorId")
    }

    private val eps = 1e-4f

    @Test
    fun `JJ16 - все полилинии маршрутов строго ортогональны без зигзагов и с точными концами`() {
        val floors = floorsMap()
        val pairs = listOf(
            "107" to "123",
            "101" to "103",
            "238" to "241",
            "131" to "241",
            "136" to "332",
            "309" to "405",
            "331" to "301",
        )
        for ((aNum, bNum) in pairs) {
            val from = room(aNum)
            val to = room(bNum)
            val r = CollegeRouteFinder.findMultiFloorRoute(from, to, floors)
            assertNotNull("маршрут $aNum→$bNum должен строиться", r)
            for (fp in r!!.floorPaths) {
                val floor = floors.getValue(fp.floorId)
                val poly = CollegeRouteFinder.routePolyline(fp.points, floor)
                assertTrue("$aNum→$bNum эт ${fp.floorId}: полилиния не пустая", poly.isNotEmpty())
                for (i in 1 until poly.size) {
                    val dx = Math.abs(poly[i].x - poly[i - 1].x) * CollegeRouteFinder.PLAN_ASPECT
                    val dy = Math.abs(poly[i].y - poly[i - 1].y)
                    assertTrue(
                        "$aNum→$bNum эт ${fp.floorId}: сегмент $i (${poly[i - 1]}→${poly[i]}) не ортогонален в plan-px",
                        dx < eps || dy < eps,
                    )
                }
                for (i in 2 until poly.size) {
                    val a = poly[i - 2]
                    val b = poly[i - 1]
                    val c = poly[i]
                    val abx = b.x - a.x
                    val aby = b.y - a.y
                    val cross = abx * (c.y - b.y) - aby * (c.x - b.x)
                    val dot = abx * (c.x - b.x) + aby * (c.y - b.y)
                    assertFalse(
                        "$aNum→$bNum эт ${fp.floorId}: зигзаг вышел-вернулся $a→$b→$c",
                        Math.abs(cross) < eps && dot < 0f,
                    )
                }
                for (pt in poly) {
                    assertTrue("$aNum→$bNum эт ${fp.floorId}: точка $pt вне плана", pt.x in 0f..1f && pt.y in 0f..1f)
                }
            }
            val firstPoly = CollegeRouteFinder.routePolyline(r.floorPaths.first().points, floors.getValue(r.floorPaths.first().floorId))
            val lastPoly = CollegeRouteFinder.routePolyline(r.floorPaths.last().points, floors.getValue(r.floorPaths.last().floorId))
            val expectStart = from.door ?: MapPoint(from.x, from.y)
            val expectEnd = to.door ?: MapPoint(to.x, to.y)
            assertEquals("$aNum→$bNum: старт ровно в точке подключения", expectStart.x, firstPoly.first().x, eps)
            assertEquals(expectStart.y, firstPoly.first().y, eps)
            assertEquals("$aNum→$bNum: финиш ровно в точке подключения", expectEnd.x, lastPoly.last().x, eps)
            assertEquals(expectEnd.y, lastPoly.last().y, eps)
        }
    }

    @Test
    fun `JJ16 - сдвинутые полосы маршрута остаются ортогональными`() {
        val floors = floorsMap()
        val r = CollegeRouteFinder.findMultiFloorRoute(room("309"), room("405"), floors)
        assertNotNull("309→405 должен строиться", r)
        val repeated = r!!.floorPaths.filter { it.floorId == "3-4" }
        assertTrue("ожидаются повторные визиты на план 3-4", repeated.size >= 2)
        val floor = floors.getValue("3-4")
        for (fp in repeated) {
            val poly = CollegeRouteFinder.routePolyline(fp.points, floor)
            for (off in listOf(6f, -6f, 12f)) {
                val shifted = com.politex.ui.screens.offsetPolyline(poly, 1376f, 768f, off)
                for (i in 1 until shifted.size) {
                    val dx = Math.abs(shifted[i].x - shifted[i - 1].x) * 1376f
                    val dy = Math.abs(shifted[i].y - shifted[i - 1].y) * 768f
                    assertTrue(
                        "полоса со сдвигом $off: сегмент $i (${shifted[i - 1]}→${shifted[i]}) не ортогонален в px",
                        dx < 0.2f || dy < 0.2f,
                    )
                }
            }
        }
    }
}
