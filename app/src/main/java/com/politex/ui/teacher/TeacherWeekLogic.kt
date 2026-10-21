package com.politex.ui.teacher

import com.politex.data.repository.TeacherDayLesson
import com.politex.util.DateFormats
import com.politex.util.Dow
import java.util.Calendar

object TeacherWeekLogic {

    fun mondayToSaturday(any: Calendar): List<Calendar> {
        val mon = any.clone() as Calendar
        mon.add(Calendar.DAY_OF_YEAR, -(Dow.of(any) - 1))
        return (0..5).map { i -> (mon.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) } }
    }

    fun weekDates(any: Calendar): List<String> = mondayToSaturday(any).map { DateFormats.dotDate(it) }

    fun shiftWeek(
        week: List<Calendar>,
        days: Int,
    ): List<Calendar> = week.map { (it.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) } }

    fun outsideCacheWindow(
        date: Calendar,
        now: Calendar = Calendar.getInstance(),
        keepDays: Int = 60,
    ): Boolean {
        val from = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -keepDays) }
        val to = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, keepDays) }
        return date.before(from) || date.after(to)
    }

    data class WeekLoad(
        val totalLessons: Int,
        val totalMinutes: Int,
        val bySubject: List<Pair<String, Int>>,
        val byGroup: List<Pair<String, Int>>,
    )

    fun aggregateLoad(days: List<List<TeacherDayLesson>>): WeekLoad {
        val timed = days.flatten().filter { it.startMinutes >= 0 && it.endMinutes > it.startMinutes }
        val bySubject =
            timed
                .groupBy { it.subject.ifBlank { "Пара" } }
                .mapValues { (_, ls) -> ls.sumOf { it.endMinutes - it.startMinutes } }
        val byGroup =
            timed
                .flatMap { l -> l.groups.map { g -> g to (l.endMinutes - l.startMinutes) } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, ms) -> ms.sum() }
        return WeekLoad(
            totalLessons = days.sumOf { it.size },
            totalMinutes = timed.sumOf { it.endMinutes - it.startMinutes },
            bySubject = sortDesc(bySubject),
            byGroup = sortDesc(byGroup),
        )
    }

    private fun sortDesc(m: Map<String, Int>): List<Pair<String, Int>> =
        m.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    fun formatMinutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "$m мин"
            m == 0 -> "$h ч"
            else -> "$h ч $m мин"
        }
    }

    fun daysWithDataCounter(
        withData: Int,
        total: Int = 6,
    ): String? = if (withData in 0 until total) "$withData из $total" else null
}
