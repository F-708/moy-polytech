package com.politex.util

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

object ScheduleDisplayPrefs {
    private const val PREFS = "mpk_ui_prefs"
    private const val KEY_COMPACT = "schedule_compact"

    val revision = mutableIntStateOf(0)

    fun isCompact(context: Context): Boolean =
        context
            .applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPACT, false)

    fun setCompact(
        context: Context,
        value: Boolean,
    ) {
        context
            .applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPACT, value)
            .apply()
        revision.intValue++
    }
}
