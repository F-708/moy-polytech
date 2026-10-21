package com.politex.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.politex.data.local.MpkDatabase
import com.politex.data.local.entity.LessonEntity
import com.politex.data.local.entity.StudentTaskEntity
import com.politex.data.model.GroupInfo
import com.politex.data.network.MpkScheduleParser
import com.politex.data.repository.SyncOutcome
import com.politex.data.repository.TaskRepository
import com.politex.data.repository.TeacherDay
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLoader
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.toastFor
import com.politex.ui.teacher.TeacherProfile
import com.politex.util.DateFormats
import com.politex.util.Dow
import com.politex.util.GroupParser
import com.politex.util.TeacherModeStore
import com.politex.widget.WidgetUpdateHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

const val ROUTE_SCHEDULE = "schedule"
const val ROUTE_LESSONS = "lessons"
const val ROUTE_BELLS = "bells"
const val ROUTE_COLLEGE = "college"
const val ROUTE_SPECIALTY = "college/specialty"
const val ROUTE_TEACHERS = "college/teachers"
const val ROUTE_SETTINGS = "college/settings"
const val ROUTE_STUDENTS = "college/students"
const val ROUTE_TEACHER = "teacher?name={name}"
const val ROUTE_MAP = "map?room={room}&from={from}&route={route}"
const val ROUTE_GROUP_SCHEDULE = "group_schedule?group={group}"

const val ARG_SCROLL = "scroll"

enum class AppTab(
    val title: String,
    val route: String,
) {
    SCHEDULE("Расписание", ROUTE_SCHEDULE),

    TASKS("Уроки", ROUTE_LESSONS),
    BELLS("Звонки", ROUTE_BELLS),
    COLLEGE("Другое", ROUTE_COLLEGE),
}

fun tabsForMode(isTeacher: Boolean): List<AppTab> =
    if (isTeacher) AppTab.entries.filter { it != AppTab.TASKS } else AppTab.entries

fun TeacherDayLesson.toLessonEntity(dateString: String): LessonEntity =
    LessonEntity(
        groupName = groups.joinToString(", "),
        dayOfWeek = MpkScheduleParser.parseDotDate(dateString)?.let { Dow.of(it) } ?: 0,
        lessonNumber = lessonNumber,
        timeStart = start,
        timeEnd = end,
        subjectRaw = subject,
        roomFirst = room,
        teacherFirst = "",
        isSplit = false,
        dateString = dateString,
    )

data class AppUiState(
    val currentGroupName: String,
    val groupInfo: GroupInfo?,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false,
    val showGroupSelection: Boolean = false,
    val isTeacherMode: Boolean = false,
    val teacherName: String? = null,
)

class AppViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val database = MpkDatabase.getInstance(application)

    val scheduleRepository = com.politex.AppGraph.scheduleRepository
    val taskRepository = TaskRepository(database.taskDao(), database.taskReminderDao())

    val diagnosticInfo = scheduleRepository.diagnosticInfo

    private val _uiState =
        MutableStateFlow(
            AppUiState(
                currentGroupName = WidgetUpdateHelper.getSelectedGroup(application),
                groupInfo = GroupParser.parse(WidgetUpdateHelper.getSelectedGroup(application)),
                showGroupSelection = !WidgetUpdateHelper.hasSelectedGroup(application) && !TeacherModeStore.isTeacher(application),
                isTeacherMode = TeacherModeStore.isTeacher(application),
                teacherName = TeacherModeStore.teacherName(application),
            ),
        )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val currentGroup: Flow<String> =
        _uiState.map { it.currentGroupName }.distinctUntilChanged()

    private val teacherScheduleDateFlow = MutableStateFlow(DateFormats.dotDate(Calendar.getInstance()))
    val teacherScheduleDate: StateFlow<String> = teacherScheduleDateFlow.asStateFlow()

    private val teacherRefreshTick = MutableStateFlow(0)

    private val teacherIdentity: Flow<Pair<Boolean, String?>> =
        _uiState.map { it.isTeacherMode to it.teacherName }.distinctUntilChanged()

    val teacherDay: StateFlow<TeacherDay?> =
        combine(teacherScheduleDateFlow, teacherIdentity, teacherRefreshTick) { date, identity, _ ->
            date to identity
        }.map { (date, identity) ->
            val (isTeacher, name) = identity
            if (!isTeacher || name.isNullOrBlank()) {
                null
            } else {
                TeacherDayLoader.loadDay(
                    context = getApplication(),
                    repository = scheduleRepository,
                    profile = TeacherProfile(name = name, surnameKey = TeacherInsights.surnameOf(name)),
                    dateString = date,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val teacherLessons: Flow<List<LessonEntity>> =
        combine(teacherDay, teacherScheduleDateFlow) { day, date -> day to date }
            .map { (day, date) ->
                day?.lessons.orEmpty().map { it.toLessonEntity(date) }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val groupLessons: StateFlow<List<LessonEntity>?> =
        teacherIdentity
            .flatMapLatest { (isTeacher, _) ->
                if (isTeacher) {
                    teacherLessons
                } else {
                    currentGroup.flatMapLatest { name -> scheduleRepository.getAllLessonsForGroup(name) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pendingTasks: StateFlow<List<StudentTaskEntity>?> =
        teacherIdentity
            .flatMapLatest { (isTeacher, _) ->
                if (isTeacher) {
                    flowOf(emptyList<StudentTaskEntity>())
                } else {
                    currentGroup.flatMapLatest { name -> taskRepository.getPendingTasksForGroup(name) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {

        viewModelScope.launch {
            snapshotFlow { TeacherModeStore.revision.intValue }
                .drop(1)
                .collect {
                    val teacher = TeacherModeStore.isTeacher(getApplication())
                    _uiState.update {
                        it.copy(
                            isTeacherMode = teacher,
                            teacherName = if (teacher) TeacherModeStore.teacherName(getApplication()) else null,
                            showGroupSelection =
                            if (teacher) {
                                false
                            } else {
                                !WidgetUpdateHelper.hasSelectedGroup(getApplication())
                            },
                        )
                    }
                    if (teacher) {
                        com.politex.worker.MpkWorkManagerHelper.setupTeacherSync(getApplication())
                    } else {
                        com.politex.worker.MpkWorkManagerHelper.cancelTeacherSync(getApplication())
                    }
                    WidgetUpdateHelper.refreshAfterModeChange(getApplication())
                }
        }

        if (!uiState.value.showGroupSelection) {

            viewModelScope.launch {
                if (!scheduleRepository.hasSchedule(uiState.value.currentGroupName)) {
                    syncSchedule(isAutoSync = true)
                }
            }
        }
    }

    fun setGroup(newGroupName: String) {
        val parsed = GroupParser.parse(newGroupName) ?: return
        WidgetUpdateHelper.setSelectedGroup(getApplication(), parsed.canonicalName)
        _uiState.update {
            it.copy(
                currentGroupName = parsed.canonicalName,
                groupInfo = parsed,
                hasSyncError = false,
                showGroupSelection = false,
            )
        }
        viewModelScope.launch {
            _toastEvent.emit("Выбрана группа: ${parsed.canonicalName}")

            if (!scheduleRepository.hasSchedule(parsed.canonicalName)) {
                syncSchedule(isAutoSync = false)
            }
        }
    }

    fun setTeacherMode(name: String) {
        TeacherModeStore.setTeacher(getApplication(), name)
        viewModelScope.launch {
            _toastEvent.emit("Режим преподавателя: ${name.trim()}")
        }
    }

    fun exitTeacherMode() {
        TeacherModeStore.clear(getApplication())
    }

    fun setTeacherDate(date: String) {
        if (MpkScheduleParser.parseDotDate(date) != null) {
            teacherScheduleDateFlow.value = date
        }
    }

    fun syncTeacherSchedule(
        targetDate: String? = null,
        force: Boolean = true,
    ) {
        if (_uiState.value.isSyncing) return
        val date = targetDate ?: teacherScheduleDateFlow.value
        val calendar = MpkScheduleParser.parseDotDate(date) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, hasSyncError = false) }
            val result = scheduleRepository.syncTeacherScheduleForDate(calendar, force = force)
            result.fold(
                onSuccess = {
                    TeacherDayLoader.markSyncedNow(getApplication())
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = false) }
                    teacherRefreshTick.update { it + 1 }
                },
                onFailure = {
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = true) }
                    if (force) {
                        _toastEvent.emit("Не удалось загрузить расписание преподавателя (проверьте интернет)")
                    }
                },
            )
        }
    }

    fun syncSchedule(
        isAutoSync: Boolean = false,
        targetDate: String? = null,
    ) {
        if (_uiState.value.isTeacherMode) {
            syncTeacherSchedule(targetDate = targetDate, force = !isAutoSync)
            return
        }
        if (_uiState.value.isSyncing) return

        val targetGroup = _uiState.value.currentGroupName

        if (targetGroup.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, hasSyncError = false) }

            val result = scheduleRepository.syncScheduleDetailed(targetGroup, force = !isAutoSync, targetDate = targetDate)

            result.fold(
                onSuccess = { outcome ->
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = false) }

                    val showToast = outcome !is SyncOutcome.NotFound || !isAutoSync
                    if (showToast) _toastEvent.emit(toastFor(outcome, targetGroup))
                },
                onFailure = {
                    _uiState.update { it.copy(isSyncing = false, hasSyncError = true) }
                    if (!isAutoSync) {
                        _toastEvent.emit("Сбой обновления: проверьте интернет (сайт guo-mpk.by недоступен)")
                    }
                },
            )
        }
    }
}
