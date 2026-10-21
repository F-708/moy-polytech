package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Deprecated("W11: папки удалены из UI; таблица оставлена в БД ради безопасной миграции")
@Entity(tableName = "material_folders")
data class MaterialFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectName: String,
    val name: String,
    val isArchive: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {

        const val ARCHIVE_SUBJECT = "Архив"

        const val ARCHIVE_NAME = "Архив"
    }
}
