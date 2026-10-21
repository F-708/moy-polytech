package com.politex.util

import android.content.Context

object HintFlags {
    private const val PREFS = "mpk_hints"
    const val KEY_SWIPE_TASKS = "swipe_tasks"
    const val KEY_SCHEDULE_TAPS = "schedule_taps"

    const val KEY_GRADES_TUTORIAL = "grades_tutorial"

    fun isDone(
        context: Context,
        key: String,
    ): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, false)

    fun markDone(
        context: Context,
        key: String,
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, true)
            .apply()
    }

    fun resetAll(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
