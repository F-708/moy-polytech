package com.politex.util

import android.content.Context
import com.politex.ui.theme.AppTheme
import com.politex.ui.theme.applyAppTheme
import com.politex.ui.theme.applyDarkMode
import kotlinx.coroutines.launch

object AppThemeStore {
    private const val PREFS_NAME = "mpk_ui_prefs"
    private const val KEY_THEME = "app_theme"
    private const val KEY_DARK = "app_dark"

    fun load(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        applyDarkMode(prefs.getBoolean(KEY_DARK, false))
        return AppTheme.from(prefs.getString(KEY_THEME, null))
    }

    fun save(
        context: Context,
        theme: AppTheme,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.id)
            .apply()
        applyAppTheme(theme)

        com.politex.widget.WidgetUpdateHelper.widgetScope.launch {
            com.politex.widget.WidgetUpdateHelper.updateAllWidgets(context.applicationContext)
        }
    }

    fun isDark(context: Context): Boolean = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_DARK, false)

    fun setDark(
        context: Context,
        dark: Boolean,
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .apply()
        applyDarkMode(dark)
    }
}
