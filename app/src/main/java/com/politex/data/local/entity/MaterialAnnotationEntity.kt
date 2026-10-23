package com.politex.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "material_annotations",
    indices = [Index(value = ["itemId", "pageIndex"], unique = true)],
)
data class MaterialAnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Long,
    val pageIndex: Int,
    val strokesJson: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
