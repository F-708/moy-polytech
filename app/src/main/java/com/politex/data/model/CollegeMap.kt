package com.politex.data.model

object CollegeMap {
    enum class Floor(
        val id: String,
        val title: String,
        val assetFile: String,
        val hint: String,
    ) {
        FIRST("1", "1 этаж", "map_floor1.jpg", "Актовый зал, библиотека, столовая"),
        SECOND("2", "2 этаж", "map_floor2.jpg", "Учебная часть, приёмная, преподавательская"),
        THIRD_FOURTH("3-4", "3 и 4 этажи", "map_floor34.jpg", "Один план на два этажа"),
        ;

        companion object {
            fun from(id: String?): Floor = entries.firstOrNull { it.id == id } ?: FIRST
        }
    }

    fun floorForRoom(room: String): Floor? {
        val digits = room.trim().takeWhile { it.isDigit() }
        if (digits.isEmpty()) return null
        return when (digits.first()) {
            '1' -> Floor.FIRST
            '2' -> Floor.SECOND
            '3', '4' -> Floor.THIRD_FOURTH
            else -> null
        }
    }

    fun looksLikeRoomNumber(room: String): Boolean = room.trim().firstOrNull()?.isDigit() == true
}
