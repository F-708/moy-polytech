package com.politex.util

import java.util.Calendar
import java.util.Locale

object Dow {
    private val WORD_REGEX = Regex(
        "ПОНЕДЕЛЬНИК|ВТОРНИК|СРЕД[АУЕ]|ЧЕТВЕРГ|ПЯТНИЦ[АУЕ]|СУББОТ",
        RegexOption.IGNORE_CASE,
    )

    fun of(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 7
    }

    fun ofWord(text: String): Int? {
        val word = WORD_REGEX.find(text)?.value?.uppercase(Locale.ROOT) ?: return null
        return when {
            word.startsWith("ПОНЕД") -> 1
            word.startsWith("ВТОР") -> 2
            word.startsWith("СРЕД") -> 3
            word.startsWith("ЧЕТВ") -> 4
            word.startsWith("ПЯТН") -> 5
            word.startsWith("СУББ") -> 6
            else -> null
        }
    }
}
