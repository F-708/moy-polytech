package com.politex.data.model

import java.util.Calendar

enum class BellScheduleType(
    val title: String,
    val subtitle: String,
) {
    STANDARD("Основное (Пн-Ср, Пт)", "12 уроков"),
    THURSDAY("Четверг (Инфочас)", "С информационным часом"),
    SATURDAY("Суббота", "8 уроков"),
}

data class BellItem(
    val lessonNumber: Int,
    val pairNumber: Int? = null,
    val title: String,
    val start: String,
    val end: String,
    val breakAfterMinutes: Int = 10,
    val isBigBreak: Boolean = false,
    val isInfoHour: Boolean = false,
) {
    val displayRange: String get() = "$start – $end"

    val startMinutes: Int get() {
        val parts = start.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    val endMinutes: Int get() {
        val parts = end.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}

object CollegeBellSchedule {
    const val DISCLAIMER: String =
        "Справочная информация: утвержденное расписание звонков колледжа МГПК на 2026 г. Сокращённые звонки не синхронизируются автоматически."

    val STANDARD_BELLS: List<BellItem> =
        listOf(
            BellItem(1, 1, "1 урок", "08:15", "09:00", breakAfterMinutes = 10),
            BellItem(2, 1, "2 урок", "09:10", "09:55", breakAfterMinutes = 25, isBigBreak = true),
            BellItem(3, 2, "3 урок", "10:20", "11:05", breakAfterMinutes = 10),
            BellItem(4, 2, "4 урок", "11:15", "12:00", breakAfterMinutes = 25, isBigBreak = true),
            BellItem(5, 3, "5 урок", "12:25", "13:10", breakAfterMinutes = 10),
            BellItem(6, 3, "6 урок", "13:20", "14:05", breakAfterMinutes = 10),
            BellItem(7, 4, "7 урок", "14:15", "15:00", breakAfterMinutes = 10),
            BellItem(8, 4, "8 урок", "15:10", "15:55", breakAfterMinutes = 10),
            BellItem(9, 5, "9 урок", "16:05", "16:50", breakAfterMinutes = 10),
            BellItem(10, 5, "10 урок", "17:00", "17:45", breakAfterMinutes = 10),
            BellItem(11, 6, "11 урок", "17:55", "18:40", breakAfterMinutes = 10),
            BellItem(12, 6, "12 урок", "18:50", "19:35", breakAfterMinutes = 0),
        )

    val THURSDAY_BELLS: List<BellItem> =
        listOf(
            BellItem(1, 1, "1 урок", "08:15", "09:00", breakAfterMinutes = 10),
            BellItem(2, 1, "2 урок", "09:10", "09:55", breakAfterMinutes = 25, isBigBreak = true),
            BellItem(3, 2, "3 урок", "10:20", "11:05", breakAfterMinutes = 10),
            BellItem(4, 2, "4 урок", "11:15", "12:00", breakAfterMinutes = 25, isBigBreak = true),
            BellItem(5, 3, "5 урок", "12:25", "13:10", breakAfterMinutes = 10),
            BellItem(6, 3, "6 урок", "13:20", "14:05", breakAfterMinutes = 10),
            BellItem(0, null, "Информационный час", "14:15", "14:35", breakAfterMinutes = 10, isInfoHour = true),
            BellItem(7, 4, "7 урок", "14:45", "15:30", breakAfterMinutes = 10),
            BellItem(8, 4, "8 урок", "15:40", "16:25", breakAfterMinutes = 10),
            BellItem(9, 5, "9 урок", "16:35", "17:20", breakAfterMinutes = 10),
            BellItem(10, 5, "10 урок", "17:30", "18:15", breakAfterMinutes = 10),
            BellItem(11, 6, "11 урок", "18:25", "19:10", breakAfterMinutes = 10),
            BellItem(12, 6, "12 урок", "19:20", "20:05", breakAfterMinutes = 0),
        )

    val SATURDAY_BELLS: List<BellItem> =
        listOf(
            BellItem(1, 1, "1 урок", "08:15", "09:00", breakAfterMinutes = 10),
            BellItem(2, 1, "2 урок", "09:10", "09:55", breakAfterMinutes = 10),
            BellItem(3, 2, "3 урок", "10:05", "10:50", breakAfterMinutes = 10),
            BellItem(4, 2, "4 урок", "11:00", "11:45", breakAfterMinutes = 10),
            BellItem(5, 3, "5 урок", "11:55", "12:40", breakAfterMinutes = 10),
            BellItem(6, 3, "6 урок", "12:50", "13:35", breakAfterMinutes = 10),
            BellItem(7, 4, "7 урок", "13:45", "14:30", breakAfterMinutes = 10),
            BellItem(8, 4, "8 урок", "14:40", "15:25", breakAfterMinutes = 0),
        )

    val BELLS: List<BellItem> = STANDARD_BELLS

    fun getTypeForDay(dayOfWeek: Int): BellScheduleType = when (dayOfWeek) {
        4 -> BellScheduleType.THURSDAY
        6 -> BellScheduleType.SATURDAY
        else -> BellScheduleType.STANDARD
    }

    fun getBellsForType(type: BellScheduleType): List<BellItem> = when (type) {
        BellScheduleType.STANDARD -> STANDARD_BELLS
        BellScheduleType.THURSDAY -> THURSDAY_BELLS
        BellScheduleType.SATURDAY -> SATURDAY_BELLS
    }

    fun getBellsForDay(dayOfWeek: Int): List<BellItem> = getBellsForType(getTypeForDay(dayOfWeek))

    fun getCurrentSlot(
        currentMinutes: Int,
        dayOfWeek: Int = com.politex.util.Dow.of(Calendar.getInstance()),
    ): BellItem? {
        val bells = getBellsForDay(dayOfWeek)
        return bells.firstOrNull { currentMinutes in it.startMinutes..it.endMinutes }
    }

    fun getTimeForLessonNumber(
        number: Int,
        dayOfWeek: Int = 1,
    ): Pair<String, String> {
        val lesson = getBellsForDay(dayOfWeek).find { it.lessonNumber == number }
        if (lesson != null) {
            return Pair(lesson.start, lesson.end)
        }
        return Pair("08:15", "09:00")
    }
}
