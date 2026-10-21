package com.politex.ui.util

import com.politex.data.repository.nextSchoolDay
import com.politex.data.repository.nextSchoolDayWithFallback
import com.politex.util.Dow
import java.util.Calendar

fun daySlots(
    today: Calendar,
    saturdayIsSchoolDay: Boolean,
    hasScheduleForDate: (Calendar) -> Boolean,
): Pair<Calendar, Calendar> {
    val left =
        (today.clone() as Calendar).apply {
            when (Dow.of(today)) {
                6 -> if (!saturdayIsSchoolDay) add(Calendar.DAY_OF_YEAR, 2)
                7 -> add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    val target = nextSchoolDayWithFallback(left, saturdayIsSchoolDay, hasScheduleForDate)
    return left to (target.date ?: nextSchoolDay(left, saturdayIsSchoolDay))
}
