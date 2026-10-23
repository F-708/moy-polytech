package com.politex.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.politex.data.local.MpkDatabase
import com.politex.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderCheckWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = MpkDatabase.getInstance(applicationContext)
        deliverDueReminders(db, applicationContext)
        Result.success()
    }

    companion object {
        const val WORK_NAME = "mpk_reminder_check_worker"
    }
}

internal suspend fun deliverDueReminders(
    db: MpkDatabase,
    context: Context,
) {
    val reminderDao = db.taskReminderDao()
    val taskDao = db.taskDao()

    val due = reminderDao.getDueUntil(System.currentTimeMillis())

    for (reminder in due) {
        val task = taskDao.getTaskById(reminder.taskId)
        when {
            task == null || task.isCompleted -> reminderDao.deleteById(reminder.id)

            canPostNotifications(context) -> {
                NotificationHelper.showReminderNotification(context, task, reminder)
                reminderDao.deleteById(reminder.id)
            }

            else -> {}
        }
    }
}

internal fun canPostNotifications(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
