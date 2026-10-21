package com.politex.data.model

import android.content.Context
import com.politex.content.ContentAssets
import org.json.JSONArray
import org.json.JSONObject

data class MapPoint(
    val x: Float,
    val y: Float,
)

data class MapRoom(
    val id: String,
    val number: String,
    val title: String,
    val note: String,
    val status: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,

    val door: MapPoint? = null,

    val numScale: Float = 1f,
)

data class MapCorridor(
    val id: String,
    val points: List<MapPoint>,
    val width: Float,
)

data class MapNode(
    val id: String,
    val x: Float,
    val y: Float,
    val links: List<String>,
)

data class MapLabel(
    val id: String,
    val x: Float,
    val y: Float,
    val text: String,
    val size: Float,
)

data class MapCallout(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val targetId: String,
)

data class MapEntrance(
    val id: String,
    val x: Float,
    val y: Float,
    val kind: String,
    val dir: Float,
    val title: String,

    val usable: Boolean = true,

    val links: List<String> = emptyList(),
)

data class FloorMap(
    val rooms: List<MapRoom> = emptyList(),
    val corridors: List<MapCorridor> = emptyList(),
    val nodes: List<MapNode> = emptyList(),
    val labels: List<MapLabel> = emptyList(),
    val callouts: List<MapCallout> = emptyList(),
    val entrances: List<MapEntrance> = emptyList(),
) {
    fun room(number: String): MapRoom? = number.trim().takeIf { it.isNotEmpty() }?.let { n ->
        rooms.firstOrNull { it.number.equals(n, ignoreCase = true) }
    }
}

class CollegeMapData(
    private val floors: Map<String, FloorMap>,
) {
    fun floor(id: String): FloorMap = floors[id] ?: FloorMap()

    val isEmpty: Boolean get() = floors.values.all { it.rooms.isEmpty() }

    companion object {
        private const val ASSET = "college_map.json"

        fun load(context: Context): CollegeMapData = try {
            ContentAssets
                .open(context, ASSET)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .let(::parse)
        } catch (_: Exception) {
            CollegeMapData(emptyMap())
        }

        fun parse(json: String): CollegeMapData {
            val root = JSONObject(json)
            val floorsObj = root.optJSONObject("floors") ?: return CollegeMapData(emptyMap())
            val result = mutableMapOf<String, FloorMap>()

            floorsObj.keys().forEach { fid ->
                val f = floorsObj.optJSONObject(fid) ?: return@forEach
                result[fid] =
                    FloorMap(
                        rooms =
                        f.optJSONArray("rooms").mapObjects { o ->
                            MapRoom(
                                id = o.optString("id"),
                                number = o.optString("number"),
                                title = o.optString("title"),
                                note = o.optString("note"),
                                status = o.optString("status", "normal").ifBlank { "normal" },
                                x = o.optDouble("x").toFloat(),
                                y = o.optDouble("y").toFloat(),
                                w = o.optDouble("w").toFloat(),
                                h = o.optDouble("h").toFloat(),
                                door = o.optJSONArray("door")?.takeIf { it.length() >= 2 }
                                    ?.let { MapPoint(it.optDouble(0).toFloat(), it.optDouble(1).toFloat()) },
                                numScale = o.optDouble("numScale", 1.0).toFloat().coerceIn(0.1f, 3f),
                            )
                        },
                        corridors =
                        f.optJSONArray("corridors").mapObjects { o ->
                            MapCorridor(
                                id = o.optString("id"),
                                points = o.optJSONArray("points").mapPoints(),
                                width = o.optDouble("width", 0.012).toFloat(),
                            )
                        },
                        nodes =
                        f.optJSONArray("nodes").mapObjects { o ->
                            MapNode(
                                id = o.optString("id"),
                                x = o.optDouble("x").toFloat(),
                                y = o.optDouble("y").toFloat(),
                                links = o.optJSONArray("links").mapStrings(),
                            )
                        },
                        labels =
                        f.optJSONArray("labels").mapObjects { o ->
                            MapLabel(
                                id = o.optString("id"),
                                x = o.optDouble("x").toFloat(),
                                y = o.optDouble("y").toFloat(),
                                text = o.optString("text"),
                                size = o.optDouble("size", 16.0).toFloat(),
                            )
                        },
                        callouts =
                        f.optJSONArray("callouts").mapObjects { o ->
                            MapCallout(
                                id = o.optString("id"),
                                text = o.optString("text"),
                                x = o.optDouble("x").toFloat(),
                                y = o.optDouble("y").toFloat(),
                                targetId = o.optString("targetId"),
                            )
                        },
                        entrances =
                        f.optJSONArray("entrances").mapObjects { o ->
                            MapEntrance(
                                id = o.optString("id"),
                                x = o.optDouble("x").toFloat(),
                                y = o.optDouble("y").toFloat(),
                                kind = o.optString("kind", "backup").ifBlank { "backup" },
                                dir = o.optDouble("dir", 0.0).toFloat(),
                                title = o.optString("title"),

                                usable = o.optBoolean("usable", true),

                                links = o.optJSONArray("links").mapStrings(),
                            )
                        },
                    )
            }
            return CollegeMapData(result)
        }
    }
}

private inline fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = ArrayList<T>(length())
    for (i in 0 until length()) optJSONObject(i)?.let { out.add(block(it)) }
    return out
}

private fun JSONArray?.mapPoints(): List<MapPoint> {
    if (this == null) return emptyList()
    val out = ArrayList<MapPoint>(length())
    for (i in 0 until length()) {
        val p = optJSONArray(i) ?: continue
        if (p.length() >= 2) out.add(MapPoint(p.optDouble(0).toFloat(), p.optDouble(1).toFloat()))
    }
    return out
}

private fun JSONArray?.mapStrings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
    return out
}
