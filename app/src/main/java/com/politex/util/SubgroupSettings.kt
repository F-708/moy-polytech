package com.politex.util

import android.content.Context

enum class SubgroupPref {
    NEUTRAL,
    FIRST,
    SECOND,
    ;

    companion object {
        fun from(id: String?): SubgroupPref = entries.firstOrNull { it.name == id } ?: NEUTRAL
    }
}

object SubgroupSettings {
    private const val KEY = "subgroup_pref"

    fun get(context: Context): SubgroupPref =
        SubgroupPref.from(
            com.politex.widget.WidgetUpdateHelper.getPrefs(context).getString(KEY, null),
        )

    fun set(
        context: Context,
        pref: SubgroupPref,
    ) {
        com.politex.widget.WidgetUpdateHelper.getPrefs(context).edit().putString(KEY, pref.name).apply()

        com.politex.widget.WidgetUpdateHelper.refreshScheduleWidgets(context)
    }

    fun splitIndex(pref: SubgroupPref): Int? =
        when (pref) {
            SubgroupPref.FIRST -> 0
            SubgroupPref.SECOND -> 1
            SubgroupPref.NEUTRAL -> null
        }
}
