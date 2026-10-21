package com.politex.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateFormats {
    fun dotDate(cal: Calendar): String = dotDate(cal.time)

    fun dotDate(date: Date): String = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).format(date)
}
