package com.politex.content

import android.content.Context
import java.io.File
import java.io.InputStream

object ContentAssets {
    private const val PHOTOS_DIR = "teachers_photos"

    fun open(
        context: Context,
        name: String,
    ): InputStream {
        val file = packFile(context, name)
        return if (file != null) file.inputStream() else context.assets.open(name)
    }

    fun photoModel(
        context: Context,
        fileName: String,
    ): String = photoModelFor(packFile(context, "$PHOTOS_DIR/$fileName"), fileName)

    fun teacherPhotoModel(
        context: Context,
        teacherName: String,
        fileName: String,
    ): String? {
        val override = OwnerOverridesStore.photoFileFor(context, teacherName)
        if (override != null) return "file://" + override.absolutePath
        if (fileName.isBlank()) return null
        return photoModel(context, fileName)
    }

    fun packFile(
        context: Context,
        name: String,
    ): File? = resolvePackFile(packRoot(context), name)

    private fun packRoot(context: Context): File? {
        val version = ContentPackStore.activeVersion
        if (version <= 0) return null
        val dir = ContentPackStore.packDir(context, version)
        return if (dir.isDirectory) dir else null
    }

    internal fun resolvePackFile(
        packRoot: File?,
        name: String,
    ): File? {
        if (packRoot == null || name.isBlank()) return null
        val file = File(packRoot, name)
        return if (file.isFile) file else null
    }

    internal fun photoModelFor(
        packFile: File?,
        fileName: String,
    ): String = if (packFile != null) {
        "file://" + packFile.absolutePath
    } else {
        "file:///android_asset/$PHOTOS_DIR/$fileName"
    }
}
