package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.politex.data.model.TaskType
import com.politex.util.SubjectFormatter

@Entity(tableName = "student_tasks")
data class StudentTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupName: String,
    val course: Int,
    val subjectName: String,
    val taskType: String,
    val title: String,
    val description: String = "",
    val isCompleted: Boolean = false,
    val deadlineDate: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    @delegate:Ignore
    val taskTypeEnum: TaskType by lazy {
        TaskType.fromDisplayName(taskType)
    }

    @delegate:Ignore
    val shortSubjectName: String by lazy {
        SubjectFormatter.getShortName(subjectName)
    }

    @delegate:Ignore
    val subjectAcronym: String by lazy {
        SubjectFormatter.getAcronym(subjectName)
    }
}
