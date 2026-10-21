package com.politex.util

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

object TeacherModeStore {
    private const val PREFS = "mpk_ui_prefs"
    private const val KEY_NAME = "teacher_mode_name"

    val revision = mutableIntStateOf(0)

    fun isTeacher(context: Context): Boolean = !teacherName(context).isNullOrBlank()

    fun teacherName(context: Context): String? =
        context
            .applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun setTeacher(
        context: Context,
        name: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        context
            .applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, trimmed)
            .apply()
        revision.intValue++
    }

    fun clear(context: Context) {
        context
            .applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_NAME)
            .apply()
        revision.intValue++
    }
}
