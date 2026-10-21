package com.politex.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.politex.AppGraph
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TeachersCache {
    @Volatile
    private var cached: List<Teacher>? = null

    fun invalidate() {
        cached = null
    }

    suspend fun load(): List<Teacher> =
        cached ?: withContext(Dispatchers.IO) {
            AppGraph.teachersRepository
                .loadTeachers()

                .also { if (it.isNotEmpty()) cached = it }
        }
}

@Composable
fun rememberTeachers(): List<Teacher> {
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        var firstResume = true
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {

                    if (firstResume) {
                        firstResume = false
                    } else {
                        TeachersCache.invalidate()
                        resumeTick++
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val teachers by produceState(emptyList(), resumeTick) { value = TeachersCache.load() }
    return teachers
}

@Composable
fun rememberIsTeacherKnown(): (String) -> Boolean {
    val teachers = rememberTeachers()
    return remember(teachers) {
        val known = teachers.map { TeacherInsights.surnameOf(it.name) }.toHashSet()
        val check: (String) -> Boolean = { name -> TeacherInsights.surnameOf(name) in known }
        check
    }
}
