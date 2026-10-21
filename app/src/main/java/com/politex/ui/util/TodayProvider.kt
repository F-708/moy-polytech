package com.politex.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun rememberToday(): Calendar {
    val lifecycleOwner = LocalLifecycleOwner.current
    var dayKey by remember { mutableLongStateOf(dayKeyOf(Calendar.getInstance())) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) dayKey = dayKeyOf(Calendar.getInstance())
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            val key = dayKeyOf(Calendar.getInstance())
            if (key != dayKey) dayKey = key
        }
    }
    return remember(dayKey) { Calendar.getInstance() }
}

internal fun dayKeyOf(cal: Calendar): Long = cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)

internal fun minutesOfDay(cal: Calendar = Calendar.getInstance()): Int = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

@Composable
fun rememberNowMinutes(periodMillis: Long = 60_000): Int {
    var nowMinutes by remember { mutableIntStateOf(minutesOfDay()) }
    LaunchedEffect(periodMillis) {
        delay(60_000 - System.currentTimeMillis() % 60_000)
        nowMinutes = minutesOfDay()
        while (true) {
            delay(periodMillis)
            nowMinutes = minutesOfDay()
        }
    }
    return nowMinutes
}
