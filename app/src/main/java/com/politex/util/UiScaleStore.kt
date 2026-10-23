package com.politex.util

import android.content.Context

enum class UiScale(val factor: Float, val label: String) {
    SMALL(0.75f, "75%"),
    NORMAL(1.0f, "100%"),
    LARGE(1.25f, "125%"),
    ;

    companion object {
        fun from(factor: Float): UiScale = entries.minByOrNull { kotlin.math.abs(it.factor - factor) } ?: NORMAL
    }
}

object UiScaleStore {
    private const val PREFS_NAME = "mpk_ui_prefs"
    private const val KEY_SCALE = "ui_scale"

    fun load(context: Context): UiScale = UiScale.from(
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_SCALE, 1.0f),
    )

    fun save(
        context: Context,
        scale: UiScale,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SCALE, scale.factor)
            .apply()
    }
}
