package com.politex.util

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

enum class UpdateChannel(
    val wire: String,
) {
    STABLE("stable"),
    PREVIEW("preview"),
    ;

    companion object {
        fun fromWire(raw: String?): UpdateChannel =
            entries.firstOrNull { it.wire.equals(raw?.trim(), ignoreCase = true) } ?: STABLE
    }
}

fun updateCheckIntervalHours(channel: UpdateChannel): Long =
    when (channel) {
        UpdateChannel.STABLE -> 12L
        UpdateChannel.PREVIEW -> 1L
    }

fun periodicCheckNeedsReschedule(
    scheduledChannel: String?,
    channel: UpdateChannel,
): Boolean = scheduledChannel != channel.wire

object UpdateChannelStore {
    private const val PREFS = "mpk_ui_prefs"
    private const val KEY_CHANNEL = "update_channel"

    val revision = mutableIntStateOf(0)

    fun channel(context: Context): UpdateChannel =
        UpdateChannel.fromWire(prefs(context).getString(KEY_CHANNEL, null))

    fun set(
        context: Context,
        channel: UpdateChannel,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_CHANNEL, channel.wire)
            .apply()
        revision.intValue++
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
