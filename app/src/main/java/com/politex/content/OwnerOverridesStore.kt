package com.politex.content

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import com.politex.data.repository.Teacher
import com.politex.util.FuzzySearch
import org.json.JSONObject
import java.io.File

data class TeacherOverride(
    val key: String,
    val fio: String,
    val position: String = "",
    val phone: String = "",
    val subjects: String = "",
    val experience: String = "",
    val category: String = "",
    val photoName: String? = null,
    val version: Int = 1,
    val updatedAt: Long = 0L,
)

object OwnerOverridesStore {
    private const val DIR_NAME = "owner_overrides"
    private const val MANIFEST = "manifest.json"
    private const val MANIFEST_TMP = "manifest.json.tmp"
    private const val PHOTOS_DIR = "photos"
    private const val FORMAT_VERSION = 1
    private const val KEY_FORMAT = "format"
    private const val SECTION_TEACHERS = "teachers"
    private const val F_FIO = "fio"
    private const val F_POSITION = "position"
    private const val F_PHONE = "phone"
    private const val F_SUBJECTS = "subjects"
    private const val F_EXPERIENCE = "experience"
    private const val F_CATEGORY = "category"
    private const val F_PHOTO = "photo"
    private const val F_VERSION = "version"
    private const val F_UPDATED_AT = "updatedAt"

    val revision = mutableIntStateOf(0)

    private val lock = Any()

    @Volatile
    private var cache: Map<String, TeacherOverride>? = null

    fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    fun manifestFile(context: Context): File = File(dir(context), MANIFEST)

    fun photosDir(context: Context): File = File(dir(context), PHOTOS_DIR)

    fun keyOf(name: String): String = FuzzySearch
        .normalize(name)
        .split(' ', '\t', '\n', '\r')
        .filter { it.isNotBlank() }
        .joinToString(" ")

    fun listTeachers(context: Context): List<TeacherOverride> = load(context).values.sortedBy { it.fio }

    fun teacher(
        context: Context,
        key: String,
    ): TeacherOverride? = load(context)[keyOf(key)]

    fun findTeacher(
        context: Context,
        nameOrKey: String,
    ): TeacherOverride? {
        val entries = load(context)
        if (entries.isEmpty()) return null
        val k = keyOf(nameOrKey)
        entries[k]?.let { return it }
        return entries.values.firstOrNull { keyOf(it.fio) == k }
    }

    fun photoFile(
        context: Context,
        key: String,
    ): File? = photoFileOf(context, load(context)[keyOf(key)])

    fun photoFileFor(
        context: Context,
        nameOrKey: String,
    ): File? = photoFileOf(context, findTeacher(context, nameOrKey))

    private fun photoFileOf(
        context: Context,
        record: TeacherOverride?,
    ): File? {
        val name = record?.photoName ?: return null
        val file = File(photosDir(context), name)
        return if (file.isFile) file else null
    }

    fun upsertTeacher(
        context: Context,
        key: String,
        fio: String,
        position: String = "",
        phone: String = "",
        subjects: String = "",
        experience: String = "",
        category: String = "",
        photoSource: File? = null,
    ): TeacherOverride? {
        val recordKey = keyOf(key)
        val trimmedFio = fio.trim()
        if (recordKey.isBlank() || trimmedFio.isBlank()) return null
        synchronized(lock) {
            val entries = load(context).toMutableMap()
            val previous = entries[recordKey]
            val version = (previous?.version ?: 0) + 1
            var photoName = previous?.photoName
            if (photoSource != null && photoSource.isFile) {
                val newName = photoFileName(recordKey, version)
                val target = File(photosDir(context), newName)
                val copied =
                    runCatching {
                        photosDir(context).mkdirs()
                        if (photoSource.absolutePath != target.absolutePath) {
                            photoSource.copyTo(target, overwrite = true)
                        }
                    }.isSuccess
                if (copied) {
                    previous?.photoName
                        ?.takeIf { it != newName }
                        ?.let { runCatching { File(photosDir(context), it).delete() } }
                    photoName = newName
                }
            }
            val record =
                TeacherOverride(
                    key = recordKey,
                    fio = trimmedFio,
                    position = position.trim(),
                    phone = phone.trim(),
                    subjects = subjects.trim(),
                    experience = experience.trim(),
                    category = category.trim(),
                    photoName = photoName,
                    version = version,
                    updatedAt = System.currentTimeMillis(),
                )
            entries[recordKey] = record
            if (!persistLocked(context, entries)) return null
            cache = entries
            revision.intValue++
            return record
        }
    }

    fun deleteTeacher(
        context: Context,
        key: String,
    ): Boolean {
        val recordKey = keyOf(key)
        synchronized(lock) {
            val entries = load(context).toMutableMap()
            val removed = entries.remove(recordKey) ?: return false
            if (persistLocked(context, entries)) {
                removed.photoName?.let { runCatching { File(photosDir(context), it).delete() } }
                cache = entries
                revision.intValue++
                return true
            }
            return false
        }
    }

    fun applyTeacherOverrides(
        context: Context,
        teachers: List<Teacher>,
    ): List<Teacher> {
        val overrides = load(context)
        if (overrides.isEmpty()) return teachers
        val matched = HashSet<String>()
        val merged =
            teachers.map { t ->
                val record = overrides[keyOf(t.name)] ?: return@map t
                matched += record.key
                t.copy(
                    name = record.fio,
                    position = record.position,
                    phone = record.phone,
                    subjects = record.subjects,
                    experience = record.experience,
                    category = record.category,
                )
            }
        val extras =
            overrides.values
                .filter { it.key !in matched }
                .map { r ->
                    Teacher(
                        name = r.fio,
                        position = r.position,
                        subjects = r.subjects,
                        phone = r.phone,
                        experience = r.experience,
                        category = r.category,
                    )
                }
        return (merged + extras).sortedBy { it.name }
    }

    fun refreshDependentCaches(context: Context) {
        runCatching { com.politex.AppGraph.teachersRepository.invalidateCache() }
        runCatching { com.politex.ui.util.TeachersCache.invalidate() }
        runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
    }

    fun invalidateCache() {
        synchronized(lock) {
            cache = null
        }
    }

    fun clearAll(context: Context) {
        synchronized(lock) {
            runCatching { dir(context).deleteRecursively() }
            cache = null
            revision.intValue++
        }
    }

    private fun load(context: Context): Map<String, TeacherOverride> {
        cache?.let { return it }
        synchronized(lock) {
            cache?.let { return it }
            val json =
                runCatching {
                    val file = manifestFile(context)
                    if (file.isFile) file.readText(Charsets.UTF_8) else ""
                }.getOrDefault("")
            val parsed = parseTeachers(json)
            cache = parsed
            return parsed
        }
    }

    private fun persistLocked(
        context: Context,
        entries: Map<String, TeacherOverride>,
    ): Boolean = try {
        val target = manifestFile(context)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, MANIFEST_TMP)
        tmp.writeText(serializeTeachers(entries), Charsets.UTF_8)
        var ok = tmp.renameTo(target)
        if (!ok) {
            target.delete()
            ok = tmp.renameTo(target)
        }
        if (!ok) tmp.delete()
        ok
    } catch (_: Exception) {
        false
    }

    internal fun photoFileName(
        key: String,
        version: Int,
    ): String = "${key.hashCode().toUInt().toString(16)}.v$version.jpg"

    internal fun parseTeachers(json: String): Map<String, TeacherOverride> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val section = JSONObject(json).optJSONObject(SECTION_TEACHERS) ?: return emptyMap()
            val out = LinkedHashMap<String, TeacherOverride>()
            val keys = section.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = section.optJSONObject(key) ?: continue
                val fio = obj.optString(F_FIO, "").trim()
                if (key.isBlank() || fio.isBlank()) continue
                out[key] =
                    TeacherOverride(
                        key = key,
                        fio = fio,
                        position = obj.optString(F_POSITION, ""),
                        phone = obj.optString(F_PHONE, ""),
                        subjects = obj.optString(F_SUBJECTS, ""),
                        experience = obj.optString(F_EXPERIENCE, ""),
                        category = obj.optString(F_CATEGORY, ""),
                        photoName = obj.optString(F_PHOTO, "").takeIf { it.isNotBlank() },
                        version = obj.optInt(F_VERSION, 1).coerceAtLeast(1),
                        updatedAt = obj.optLong(F_UPDATED_AT, 0L),
                    )
            }
            out
        }.getOrElse { emptyMap() }
    }

    internal fun serializeTeachers(entries: Map<String, TeacherOverride>): String {
        val section = JSONObject()
        for ((key, r) in entries) {
            val obj =
                JSONObject()
                    .put(F_FIO, r.fio)
                    .put(F_POSITION, r.position)
                    .put(F_PHONE, r.phone)
                    .put(F_SUBJECTS, r.subjects)
                    .put(F_EXPERIENCE, r.experience)
                    .put(F_CATEGORY, r.category)
                    .put(F_VERSION, r.version)
                    .put(F_UPDATED_AT, r.updatedAt)
            r.photoName?.let { obj.put(F_PHOTO, it) }
            section.put(key, obj)
        }
        return JSONObject()
            .put(KEY_FORMAT, FORMAT_VERSION)
            .put(SECTION_TEACHERS, section)
            .toString()
    }
}
