package com.politex.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MaterialKind { FILE, IMAGES, TEXT }

@Entity(
    tableName = "material_items",
    indices = [Index("folderId")],
)
data class MaterialItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @Suppress("DEPRECATION")
    @Deprecated("W11: папки удалены из UI; колонка остаётся в таблице для совместимости")
    val folderId: Long = 0,
    val kind: String,
    val title: String,

    val fileName: String = "",

    val ext: String = "",

    val uriPath: String = "",
    val pageCount: Int = 0,
    val lastPage: Int = 0,
    val rotationDeg: Int = 0,
    val sortOrder: Int = 0,
    val textContent: String = "",

    @ColumnInfo(defaultValue = "''")
    val subjectName: String = "",

    @ColumnInfo(defaultValue = "0")
    val archived: Boolean = false,

    @ColumnInfo(defaultValue = "0")
    val textEdited: Boolean = false,

    @ColumnInfo(defaultValue = "")
    val contentHash: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val kindEnum: MaterialKind
        get() = runCatching { MaterialKind.valueOf(kind) }.getOrDefault(MaterialKind.FILE)

    val imagePaths: List<String>
        get() = if (kindEnum == MaterialKind.IMAGES) uriPath.split('\n').filter { it.isNotBlank() } else emptyList()
}
