package com.politex.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.repository.TeacherInsights
import com.politex.data.repository.nextDayCandidates
import com.politex.ui.teacher.TeacherProfile
import com.politex.ui.teacher.TeacherProfileStore
import com.politex.util.TeacherModeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class SyncDecision { MORNING_FORCE, PERIODIC, SKIP }

fun shouldTeacherSync(
    hourMinute: Int,
    dayOfWeek: Int,
): SyncDecision {
    if (dayOfWeek == Calendar.SUNDAY) return SyncDecision.SKIP
    return when {
        hourMinute in 6 * 60 + 30..7 * 60 + 15 -> SyncDecision.MORNING_FORCE
        hourMinute in 10 * 60..21 * 60 -> SyncDecision.PERIODIC
        else -> SyncDecision.SKIP
    }
}

internal fun resolveWorkerProfile(context: Context): TeacherProfile? = TeacherProfileStore.get(context)
    ?: TeacherModeStore.teacherName(context)?.let {
        TeacherProfile(name = it, surnameKey = TeacherInsights.surnameOf(it))
    }

class TeacherScheduleWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val profile = resolveWorkerProfile(applicationContext) ?: return@withContext Result.success()

        val now = Calendar.getInstance()
        val decision =
            shouldTeacherSync(
                now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
                now.get(Calendar.DAY_OF_WEEK),
            )
        if (decision == SyncDecision.SKIP) return@withContext Result.success()

        val repository = com.politex.AppGraph.scheduleRepository

        val targets = listOf(now) + nextDayCandidates(now, saturdayIsSchoolDay = true)

        var anyOk = false
        var anyFail = false
        targets.forEachIndexed { index, cal ->

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val force = decision == SyncDecision.MORNING_FORCE || (index == 0 && hour in 14..17)
            repository
                .syncTeacherScheduleForDate(cal, force)
                .onSuccess { anyOk = true }
                .onFailure { anyFail = true }
        }

        if (anyOk) {
            applicationContext
                .getSharedPreferences("mpk_teacher_profile", Context.MODE_PRIVATE)
                .edit()
                .putLong("lastSyncEpoch", System.currentTimeMillis())
                .apply()

            com.politex.widget.TeacherWidgetUpdater.updateAll(applicationContext)

            if (TeacherModeStore.isTeacher(applicationContext)) {
                com.politex.widget.TeacherWidgetData.invalidate()
                com.politex.widget.BellCountdownWidgetProvider.invalidateLastLessonCache()
                com.politex.widget.WidgetUpdateHelper.refreshScheduleWidgets(applicationContext)
            }
        }

        if (anyFail) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "teacher_schedule_sync"
    }
}
