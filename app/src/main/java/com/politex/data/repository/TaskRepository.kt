package com.politex.data.repository

import androidx.room.withTransaction
import com.politex.data.local.MpkDatabase
import com.politex.data.local.dao.TaskDao
import com.politex.data.local.dao.TaskReminderDao
import com.politex.data.local.entity.StudentTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class TaskRepository(
    private val taskDao: TaskDao,
    private val reminderDao: TaskReminderDao,
    private val db: MpkDatabase? = null,
) {
    fun getTasksForGroup(groupName: String): Flow<List<StudentTaskEntity>> = taskDao.getTasksForGroup(groupName).flowOn(Dispatchers.IO)

    fun getTasksForGroupAndSubject(
        groupName: String,
        subjectName: String,
    ): Flow<List<StudentTaskEntity>> = taskDao.getTasksForGroupAndSubject(groupName, subjectName).flowOn(Dispatchers.IO)

    fun getPendingTasksForGroup(groupName: String): Flow<List<StudentTaskEntity>> = taskDao.getPendingTasksForGroup(groupName).flowOn(Dispatchers.IO)

    suspend fun getTaskById(id: Long): StudentTaskEntity? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(id)
    }

    suspend fun addTask(task: StudentTaskEntity): Long = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    suspend fun saveTask(task: StudentTaskEntity): Long = withContext(Dispatchers.IO) {
        taskDao.insertTask(task)
    }

    suspend fun updateTask(task: StudentTaskEntity) = withContext(Dispatchers.IO) {
        taskDao.updateTask(task)
    }

    suspend fun toggleTaskCompleted(
        id: Long,
        isCompleted: Boolean,
    ) = withContext(Dispatchers.IO) {
        taskDao.setTaskCompleted(id, isCompleted)
    }

    suspend fun deleteTask(task: StudentTaskEntity) = deleteTaskById(task.id)

    suspend fun deleteTaskById(id: Long) = withContext(Dispatchers.IO) {
        val database = db
        if (database != null) {
            // Одна транзакция: падение между delete не оставит сиротское напоминание.
            database.withTransaction {
                taskDao.deleteTaskById(id)
                reminderDao.deleteForTask(id)
            }
        } else {
            taskDao.deleteTaskById(id)
            reminderDao.deleteForTask(id)
        }
    }
}
