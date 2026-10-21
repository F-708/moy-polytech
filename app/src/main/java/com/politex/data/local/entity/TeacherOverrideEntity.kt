package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "teacher_overrides",
    indices = [Index(value = ["dateString", "lessonNumber"], unique = true)],
)
data class TeacherOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateString: String,
    val lessonNumber: Int,
    val kind: String,
    val room: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KIND_OTHER_ROOM = "OTHER_ROOM"
        const val KIND_NO_LESSON = "NO_LESSON"
        const val KIND_SOMEONE_ELSE = "SOMEONE_ELSE"
    }
}
