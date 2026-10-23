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

data class PackTeacherOverride(
    val key: String,
    val fio: String? = null,
    val position: String? = null,
    val phone: String? = null,
    val subjects: String? = null,
    val experience: String? = null,
    val category: String? = null,
    val photo: String? = null,
) {
    val isEmpty: Boolean
        get() = fio == null && position == null && phone == null &&
            subjects == null && experience == null && category == null && photo == null
}

object OwnerOverridesStore {
    private const val DIR_NAME = "owner_overrides"
    private const val MANIFEST = "manifest.json"
    private const val MANIFEST_TMP = "manifest.json.tmp"
    private const val PHOTOS_DIR = "photos"
    private const val FORMAT_VERSION = 1
    const val PACK_FILE = "owner_overrides.json"
    private const val PACK_PHOTOS_PREFIX = "teachers_photos/"
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

    @Volatile
    private var packCache: Map<String, PackTeacherOverride>? = null

    @Volatile
    private var packCacheVersion = -1

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
    ): File? {
        val k = keyOf(key)
        photoFileOf(context, load(context)[k])?.let { return it }
        return packPhotoOf(context, packEntries(context)[k])
    }

    fun photoFileFor(
        context: Context,
        nameOrKey: String,
    ): File? {
        photoFileOf(context, findTeacher(context, nameOrKey))?.let { return it }
        return packPhotoOf(context, findPackTeacher(context, nameOrKey))
    }

    fun findPackTeacher(
        context: Context,
        nameOrKey: String,
    ): PackTeacherOverride? {
        val entries = packEntries(context)
        if (entries.isEmpty()) return null
        val k = keyOf(nameOrKey)
        entries[k]?.let { return it }
        return entries.values.firstOrNull { it.fio != null && keyOf(it.fio) == k }
    }

    fun packEntries(context: Context): Map<String, PackTeacherOverride> {
        val version = ContentPackStore.activeVersion
        if (version <= 0) return emptyMap()
        packCache?.let { if (packCacheVersion == version) return it }
        synchronized(lock) {
            packCache?.let { if (packCacheVersion == version) return it }
            val json =
                runCatching {
                    ContentAssets
                        .packFile(context, PACK_FILE)
                        ?.readText(Charsets.UTF_8)
                }.getOrNull()
            val parsed = json?.let { parsePackTeachers(it) } ?: emptyMap()
            packCache = parsed
            packCacheVersion = version
            return parsed
        }
    }

    private fun packPhotoOf(
        context: Context,
        record: PackTeacherOverride?,
    ): File? {
        val name = record?.photo ?: return null
        return ContentAssets.packFile(context, PACK_PHOTOS_PREFIX + name)
    }

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
        val local = load(context)
        val pack = packEntries(context)
        if (local.isEmpty() && pack.isEmpty()) return teachers
        val matched = HashSet<String>()
        val merged =
            teachers.map { t ->
                val k = keyOf(t.name)
                val packRec = pack[k]
                val localRec = local[k]
                if (packRec == null && localRec == null) return@map t
                packRec?.let { matched += it.key }
                localRec?.let { matched += it.key }
                var out = t
                if (packRec != null) out = applyPackRecord(out, packRec)
                if (localRec != null) out = applyLocalRecord(out, localRec)
                out.copy(sourceName = t.name)
            }
        val extras = LinkedHashMap<String, Teacher>()
        pack.values
            .filter { it.key !in matched }
            .forEach { r ->
                extras[r.key] =
                    Teacher(
                        name = r.fio ?: r.key,
                        position = r.position ?: "",
                        subjects = r.subjects ?: "",
                        phone = r.phone ?: "",
                        experience = r.experience ?: "",
                        category = r.category ?: "",
                    )
            }
        local.values
            .filter { it.key !in matched }
            .forEach { r ->
                extras[r.key] =
                    Teacher(
                        name = r.fio,
                        position = r.position,
                        subjects = r.subjects,
                        phone = r.phone,
                        experience = r.experience,
                        category = r.category,
                    )
            }
        return (merged + extras.values).sortedBy { it.name }
    }

    private fun applyPackRecord(
        t: Teacher,
        r: PackTeacherOverride,
    ): Teacher =
        t.copy(
            name = r.fio ?: t.name,
            position = r.position ?: t.position,
            phone = r.phone ?: t.phone,
            subjects = r.subjects ?: t.subjects,
            experience = r.experience ?: t.experience,
            category = r.category ?: t.category,
        )

    private fun applyLocalRecord(
        t: Teacher,
        r: TeacherOverride,
    ): Teacher =
        t.copy(
            name = r.fio,
            position = r.position,
            phone = r.phone,
            subjects = r.subjects,
            experience = r.experience,
            category = r.category,
        )

    fun refreshDependentCaches(context: Context) {
        runCatching { com.politex.AppGraph.teachersRepository.invalidateCache() }
        runCatching { com.politex.ui.util.TeachersCache.invalidate() }
        runCatching { coil.Coil.imageLoader(context).memoryCache?.clear() }
    }

    fun invalidateCache() {
        synchronized(lock) {
            cache = null
            packCache = null
            packCacheVersion = -1
        }
    }

    fun clearAll(context: Context) {
        synchronized(lock) {
            runCatching { dir(context).deleteRecursively() }
            cache = null
            packCache = null
            packCacheVersion = -1
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

    fun parsePackTeachers(json: String): Map<String, PackTeacherOverride>? {
        if (json.isBlank()) return null
        return runCatching {
            val root = JSONObject(json)
            if (root.optInt(KEY_FORMAT, -1) != FORMAT_VERSION) return null
            val section = root.optJSONObject(SECTION_TEACHERS) ?: return null
            val out = LinkedHashMap<String, PackTeacherOverride>()
            val keys = section.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.isBlank()) continue
                val obj = section.optJSONObject(key) ?: continue
                val record =
                    PackTeacherOverride(
                        key = key,
                        fio = packField(obj, F_FIO)?.takeIf { it.isNotBlank() },
                        position = packField(obj, F_POSITION),
                        phone = packField(obj, F_PHONE),
                        subjects = packField(obj, F_SUBJECTS),
                        experience = packField(obj, F_EXPERIENCE),
                        category = packField(obj, F_CATEGORY),
                        photo = packField(obj, F_PHOTO)?.takeIf { it.isNotBlank() },
                    )
                if (record.isEmpty) continue
                out[key] = record
            }
            out
        }.getOrNull()
    }

    private fun packField(
        obj: JSONObject,
        name: String,
    ): String? = if (obj.has(name)) obj.optString(name, "").trim() else null

    fun serializePackJson(
        entries: Map<String, PackTeacherOverride>,
        updatedAt: Long,
    ): String {
        val section = JSONObject()
        for ((key, r) in entries) {
            if (key.isBlank() || r.isEmpty) continue
            val obj = JSONObject()
            r.fio?.let { obj.put(F_FIO, it) }
            r.position?.let { obj.put(F_POSITION, it) }
            r.phone?.let { obj.put(F_PHONE, it) }
            r.subjects?.let { obj.put(F_SUBJECTS, it) }
            r.experience?.let { obj.put(F_EXPERIENCE, it) }
            r.category?.let { obj.put(F_CATEGORY, it) }
            r.photo?.let { obj.put(F_PHOTO, it) }
            section.put(key, obj)
        }
        return JSONObject()
            .put(KEY_FORMAT, FORMAT_VERSION)
            .put(F_UPDATED_AT, updatedAt)
            .put(SECTION_TEACHERS, section)
            .toString()
    }
}
