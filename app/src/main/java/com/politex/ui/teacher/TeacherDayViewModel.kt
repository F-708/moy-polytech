package com.politex.ui.teacher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.politex.AppGraph
import com.politex.data.network.MpkScheduleParser
import com.politex.data.repository.NextLesson
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLoader
import com.politex.data.repository.defaultTeacherDayOffset
import com.politex.data.repository.teacherTargetCalendar
import com.politex.util.DateFormats
import com.politex.util.Dow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class TeacherHomeState(
    val dayOffset: Int = 0,

    val selectedDate: String = "",
    val dateKey: String = "",
    val dow: Int = 0,

    val isToday: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val lessons: List<TeacherDayLesson> = emptyList(),
    val ambiguous: Boolean = false,
    val error: String = "",
    val isSynced: Boolean = false,
    val lastSyncEpoch: Long = 0L,
    val nextLesson: NextLesson? = null,
)

class TeacherDayViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(TeacherHomeState(dayOffset = defaultTeacherDayOffset(Dow.of(Calendar.getInstance()))))
    val state: StateFlow<TeacherHomeState> = _state

    private var profile: TeacherProfile? = null

    private var loadToken = 0

    fun setProfile(p: TeacherProfile?) {
        if (p == profile) return
        profile = p
        if (p == null) {
            _state.update { it.copy(loading = false, lessons = emptyList(), error = "") }
        } else {
            load()
        }
    }

    fun selectDay(offset: Int) {
        if (offset == _state.value.dayOffset && _state.value.selectedDate.isEmpty()) return
        _state.update { it.copy(dayOffset = offset, selectedDate = "") }
        load()
    }

    fun selectDate(dateKey: String) {
        if (dateKey == _state.value.selectedDate) return
        _state.update { it.copy(selectedDate = dateKey) }
        load()
    }

    fun refresh() {
        if (profile == null) return
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val (cal, _) = resolveDate(_state.value)
            val ctx = getApplication<Application>()
            val result =
                runCatching { AppGraph.scheduleRepository.syncTeacherScheduleForDate(cal, force = true) }
                    .onFailure { if (it is CancellationException) throw it }
                    .getOrElse { Result.failure(it) }
            if (result.isSuccess) TeacherDayLoader.markSyncedNow(ctx)
            load()
        }
    }

    private fun load() {
        val p = profile ?: return
        val token = ++loadToken
        viewModelScope.launch {
            val (cal, dateKey) = resolveDate(_state.value)
            val dow = Dow.of(cal)
            val isToday = DateFormats.dotDate(Calendar.getInstance()) == dateKey
            val ctx = getApplication<Application>()
            val repo = AppGraph.scheduleRepository
            val day = TeacherDayLoader.loadDay(ctx, repo, p, dateKey)
            val next = if (day.lessons.isEmpty()) TeacherDayLoader.findNextLesson(ctx, repo, p, dateKey) else null
            if (token != loadToken) return@launch
            _state.update {
                it.copy(
                    dateKey = dateKey,
                    dow = dow,
                    isToday = isToday,
                    loading = false,
                    refreshing = false,
                    lessons = day.lessons,
                    ambiguous = day.ambiguous,
                    error = day.error,
                    isSynced = day.isSynced,
                    lastSyncEpoch = day.lastSyncEpoch,
                    nextLesson = next,
                )
            }
        }
    }

    private fun resolveDate(state: TeacherHomeState): Pair<Calendar, String> {
        val picked = state.selectedDate
        if (picked.isNotEmpty()) {
            MpkScheduleParser.parseDotDate(picked)?.let { return it to DateFormats.dotDate(it) }
        }
        val cal = teacherTargetCalendar(state.dayOffset, null)
        return cal to DateFormats.dotDate(cal)
    }
}
