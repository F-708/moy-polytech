package com.politex.util

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object ExactAlarmGate {
    fun hintNeeded(
        sdkInt: Int,
        canScheduleExact: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.S && !canScheduleExact

    fun shouldRequest(
        sdkInt: Int,
        canScheduleExact: Boolean,
        askedThisSession: Boolean,
    ): Boolean = hintNeeded(sdkInt, canScheduleExact) && !askedThisSession

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
