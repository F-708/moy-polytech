package com.politex.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollegeMultiFloorRouteTest {
    private fun room(
        number: String,
        x: Float,
        y: Float,
    ) = MapRoom(
        id = number,
        number = number,
        title = "",
        note = "",
        status = "normal",
        x = x,
        y = y,
        w = 0.02f,
        h = 0.02f,
    )

    private fun stair(
        id: String,
        x: Float,
        y: Float,
        links: List<String> = emptyList(),
    ) = MapEntrance(id = id, x = x, y = y, kind = "stairs", dir = 0f, title = "Лестница", links = links)

    private fun floor(stairs: List<MapEntrance>) = FloorMap(
        corridors =
        listOf(
            MapCorridor(
                "c",
                listOf(
                    MapPoint(0.05f, 0.5f),
                    MapPoint(0.25f, 0.5f),
                    MapPoint(0.75f, 0.5f),
                    MapPoint(0.95f, 0.5f),
                ),
                0.012f,
            ),
        ),
        nodes =
        listOf(
            MapNode("nL", 0.25f, 0.5f, listOf("nR")),
            MapNode("nR", 0.75f, 0.5f, listOf("nL")),
        ),
        entrances = stairs,
    )

    private fun floors() = mapOf(
        "1" to floor(listOf(stair("s1a", 0.25f, 0.5f), stair("s1b", 0.75f, 0.5f))),
        "2" to floor(listOf(stair("s2a", 0.25f, 0.5f), stair("s2b", 0.75f, 0.5f))),
        "3-4" to floor(listOf(stair("s3a", 0.25f, 0.5f), stair("s3b", 0.75f, 0.5f))),
    )

    @Test
    fun `один этаж — обычный маршрут, без лестниц`() {
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("101", 0.1f, 0.3f),
                room("105", 0.9f, 0.3f),
                floors(),
            )
        assertNotNull(r)
        assertFalse("один этаж — не мультиэтажный", r!!.crossesFloors)
        assertEquals(1, r.floorPaths.size)
        assertEquals("1", r.floorPaths[0].floorId)
    }

    @Test
    fun `соседние этажи 1-2 идут через общую лестницу`() {
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("101", 0.1f, 0.3f),
                room("205", 0.9f, 0.3f),
                floors(),
            )
        assertNotNull("маршрут 1→2 должен найтись", r)
        assertTrue(r!!.crossesFloors)
        assertEquals(listOf("1", "2"), r.floorPaths.map { it.floorId })

        assertTrue(r.floorPaths.all { it.points.size >= 2 })
    }

    @Test
    fun `через два этажа 1-3 — три сегмента в правильном порядке`() {
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("101", 0.1f, 0.3f),
                room("305", 0.9f, 0.3f),
                floors(),
            )
        assertNotNull(r)
        assertEquals(listOf("1", "2", "3-4"), r!!.floorPaths.map { it.floorId })
    }

    @Test
    fun `обратное направление 3-1 — этажи в убывающем порядке`() {
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("305", 0.9f, 0.3f),
                room("101", 0.1f, 0.3f),
                floors(),
            )
        assertNotNull(r)
        assertEquals(listOf("3-4", "2", "1"), r!!.floorPaths.map { it.floorId })
    }

    @Test
    fun `аварийная лестница не используется`() {
        val f =
            mapOf(
                "1" to floor(listOf(stair("s1a", 0.25f, 0.5f))),
                "2" to floor(listOf(stair("s2a", 0.25f, 0.5f))),
            )
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("101", 0.1f, 0.3f),
                room("205", 0.9f, 0.3f),
                f,
                isUsableStair = { it.id != "s2a" },
            )
        assertNull("по аварийной лестнице маршрута быть не должно", r)
    }

    @Test
    fun `выбирается ближайшая к старту лестница`() {
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("101", 0.1f, 0.3f),
                room("205", 0.9f, 0.3f),
                floors(),
            )
        assertNotNull(r)

        val f1 = r!!.floorPaths.first { it.floorId == "1" }
        val lastOnF1 = f1.points.last()
        assertTrue(
            "старт у x=0.1 должен вести к лестнице x≈0.25, получилось ${lastOnF1.x}",
            lastOnF1.x < 0.5f,
        )
    }

    @Test
    fun `несуществующий этаж — null`() {
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("101", 0.1f, 0.3f),
                room("905", 0.9f, 0.3f),
                floors(),
            )
        assertNull(r)
    }

    @Test
    fun `points плоский список содержит старт и финиш`() {
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("101", 0.1f, 0.3f),
                room("205", 0.9f, 0.3f),
                floors(),
            )
        assertNotNull(r)
        val all = r!!.points
        assertTrue(all.size >= 4)
    }

    private fun corridor(
        id: String,
        x0: Float,
        x1: Float,
        y: Float,
    ) = MapCorridor(id, listOf(MapPoint(x0, y), MapPoint((x0 + x1) / 2f, y), MapPoint(x1, y)), 0.012f)

    @Test
    fun `корпуса на одном плане связаны через другой этаж (331 в 301)`() {
        val floors =
            mapOf(
                "2" to
                    FloorMap(
                        corridors = listOf(corridor("f2c", 0.05f, 0.95f, 0.5f)),
                        entrances =
                        listOf(
                            stair("s2c2", 0.25f, 0.5f, listOf("s34c2")),
                            stair("s2c1", 0.75f, 0.5f, listOf("s34c1")),
                        ),
                    ),
                "3-4" to
                    FloorMap(

                        corridors = listOf(corridor("c2", 0.05f, 0.45f, 0.5f), corridor("c1", 0.55f, 0.95f, 0.5f)),
                        entrances =
                        listOf(
                            stair("s34c2", 0.25f, 0.5f, listOf("s2c2")),
                            stair("s34c1", 0.75f, 0.5f, listOf("s2c1")),
                        ),
                    ),
            )
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("331", 0.1f, 0.3f),
                room("301", 0.9f, 0.3f),
                floors,
            )
        assertNotNull("переход между корпусами должен идти через 2 этаж", r)
        assertEquals(listOf("3-4", "2", "3-4"), r!!.floorPaths.map { it.floorId })
    }

    @Test
    fun `вход на 4 этаж только через связанную лестницу (план 3-4)`() {
        val floors =
            mapOf(
                "3-4" to
                    FloorMap(
                        corridors = listOf(corridor("c3", 0.05f, 0.45f, 0.5f), corridor("c4", 0.7f, 0.95f, 0.2f)),
                        entrances =
                        listOf(
                            stair("s3", 0.25f, 0.5f, listOf("s4")),
                            stair("s4", 0.75f, 0.2f, listOf("s3")),
                        ),
                    ),
            )
        val r =
            CollegeRouteFinder.findMultiFloorRoute(
                room("335", 0.1f, 0.3f),
                room("401", 0.85f, 0.12f),
                floors,
            )
        assertNotNull("на 4 этаж должен вести маршрут через лестницу", r)

        assertEquals(listOf("3-4", "3-4"), r!!.floorPaths.map { it.floorId })

        val all = r.points
        assertTrue("путь должен пройти через лестницу s3", all.any { Math.abs(it.x - 0.25f) < 1e-3f && Math.abs(it.y - 0.5f) < 1e-3f })
        assertTrue("путь должен пройти через лестницу s4", all.any { Math.abs(it.x - 0.75f) < 1e-3f && Math.abs(it.y - 0.2f) < 1e-3f })
    }
}
