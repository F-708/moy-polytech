package com.politex.ui.teacher

import android.content.Context

data class TeacherProfile(
    val name: String,
    val surnameKey: String,
)

object TeacherProfileStore {
    private const val PREFS = "mpk_teacher_profile"
    private const val KEY_NAME = "name"
    private const val KEY_SURNAME = "surnameKey"

    fun get(context: Context): TeacherProfile? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        return TeacherProfile(name, p.getString(KEY_SURNAME, "").orEmpty())
    }

    fun set(
        context: Context,
        profile: TeacherProfile?,
    ) {
        val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (profile == null) {
            e.remove(KEY_NAME).remove(KEY_SURNAME)
        } else {
            e.putString(KEY_NAME, profile.name).putString(KEY_SURNAME, profile.surnameKey)
        }
        e.apply()
    }
}
