package com.politex.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object MiuiWidgetHint {
    private const val PREF_SHOWN = "miui_autostart_hint_shown"

    private val XIAOMI_MANUFACTURERS = setOf("xiaomi", "redmi", "poco", "blackshark")

    fun isXiaomi(manufacturer: String?): Boolean =
        manufacturer != null && manufacturer.trim().lowercase(java.util.Locale.ROOT) in XIAOMI_MANUFACTURERS

    fun shouldShow(
        manufacturer: String?,
        canExactAlarms: Boolean,
        ignoringBatteryOptimizations: Boolean,
        alreadyShown: Boolean,
    ): Boolean = isXiaomi(manufacturer) && !alreadyShown && (!canExactAlarms || !ignoringBatteryOptimizations)

    fun isShown(context: Context): Boolean = WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_SHOWN, false)

    fun markShown(context: Context) {
        WidgetUpdateHelper.getPrefs(context).edit().putBoolean(PREF_SHOWN, true).apply()
    }

    fun ignoringBatteryOptimizations(context: Context): Boolean = runCatching {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(true)

    fun takeOnce(
        context: Context,
        canExactAlarms: Boolean,
    ): Boolean {
        val show = shouldShow(
            Build.MANUFACTURER,
            canExactAlarms,
            ignoringBatteryOptimizations(context),
            isShown(context),
        )
        if (show) markShown(context)
        return show
    }

    fun openBatterySettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
            }
        }
    }
}
