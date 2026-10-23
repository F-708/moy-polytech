package com.politex.content

import com.politex.data.model.CollegeMapData
import com.politex.util.Csv
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ContentPackManifest(
    val packVersion: Int,
    val generatedAt: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val minAppVersion: Int,
)

enum class ContentPackRejection {
    NONE,
    UNREADABLE,
    CHECKSUM,
    TOO_LARGE,
    ZIP_SLIP,
    COMPOSITION,
    TEACHERS,
    PHOTOS,
    MAP,
    GROUPS,
}

object ContentPackValidator {
    const val MAX_PACK_BYTES = 25L * 1024L * 1024L
    const val MAX_UNPACKED_BYTES = 100L * 1024L * 1024L
    const val MIN_TEACHERS = 50
    const val MIN_GROUPS = 20
    const val PHOTOS_PREFIX = "teachers_photos/"
    val REQUIRED_FILES = listOf("teachers.csv", "group_codes.csv", "college_map.json")

    fun parseManifest(json: String?): ContentPackManifest? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val version = root.optInt("pack_version", -1)
        if (version <= 0) return null
        val url = root.optString("url").trim()
        if (url.isBlank()) return null
        val sha = root.optString("sha256").trim()
        if (sha.isBlank()) return null
        return ContentPackManifest(
            packVersion = version,
            generatedAt = root.optString("generated_at").trim(),
            url = url,
            sizeBytes = root.optLong("size_bytes", 0L),
            sha256 = sha,
            minAppVersion = root.optInt("min_app_version", 0),
        )
    }

    fun shouldApply(
        remoteVersion: Int,
        activeVersion: Int,
    ): Boolean = remoteVersion > activeVersion

    fun minAppVersionAccepted(
        minAppVersion: Int,
        currentVersionCode: Int,
    ): Boolean = minAppVersion <= currentVersionCode

    fun matchesChecksum(
        file: File,
        expectedSha256: String,
    ): Boolean {
        val expected = expectedSha256.trim()
        if (expected.isEmpty()) return false
        if (!file.isFile || file.length() <= 0L) return false
        val actual = runCatching { sha256Hex(file.readBytes()) }.getOrNull() ?: return false
        return actual.equals(expected, ignoreCase = true)
    }

    fun sizeAccepted(
        file: File,
        declaredBytes: Long,
        maxBytes: Long = MAX_PACK_BYTES,
    ): Boolean {
        if (!file.isFile) return false
        val length = file.length()
        if (length <= 0L || length > maxBytes) return false
        return declaredBytes <= 0L || length == declaredBytes
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    fun isZipSlip(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/")) return true
        var depth = 0
        for (part in normalized.split('/')) {
            when {
                part == ".." -> {
                    depth--
                    if (depth < 0) return true
                }
                part.isNotBlank() && part != "." -> depth++
            }
        }
        return false
    }

    fun extract(
        zip: File,
        target: File,
    ): ContentPackRejection {
        if (!zip.isFile || zip.length() <= 0L) return ContentPackRejection.UNREADABLE
        target.deleteRecursively()
        if (!target.mkdirs()) return ContentPackRejection.UNREADABLE
        val canonicalTarget = target.canonicalPath + File.separator
        return try {
            ZipFile(zip).use { archive -> extractEntries(archive, target, canonicalTarget) }
        } catch (_: OversizedPackException) {
            target.deleteRecursively()
            ContentPackRejection.TOO_LARGE
        } catch (_: Exception) {
            target.deleteRecursively()
            ContentPackRejection.UNREADABLE
        }
    }

    fun validateContent(
        dir: File,
        mapParses: (String) -> Boolean = ::defaultMapParses,
    ): ContentPackRejection {
        if (!REQUIRED_FILES.all { File(dir, it).isFile }) return ContentPackRejection.COMPOSITION
        val rows = runCatching { teacherRows(File(dir, "teachers.csv").readText()) }.getOrNull() ?: return ContentPackRejection.TEACHERS
        if (rows.size < MIN_TEACHERS) return ContentPackRejection.TEACHERS
        val photosMissing = rows.any { (_, photo) -> photo.isNotBlank() && !File(dir, PHOTOS_PREFIX + photo).isFile }
        if (photosMissing) return ContentPackRejection.PHOTOS
        val groups = runCatching { File(dir, "group_codes.csv").readLines().count { it.isNotBlank() } }.getOrDefault(0)
        if (groups < MIN_GROUPS) return ContentPackRejection.GROUPS
        val mapJson = runCatching { File(dir, "college_map.json").readText() }.getOrNull() ?: return ContentPackRejection.MAP
        if (!runCatching { mapParses(mapJson) }.getOrDefault(false)) return ContentPackRejection.MAP
        return ContentPackRejection.NONE
    }

    internal fun teacherRows(csv: String): List<Pair<String, String>> = Csv.stripBom(csv)
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { Csv.splitRow(it) }
        .map { cells ->
            val name = cells.getOrElse(0) { "" }.trim()
            val photo = cells.getOrElse(6) { "" }.trim()
            name to photo
        }.filter { (name, _) -> name.isNotBlank() }
        .toList()

    private fun extractEntries(
        archive: ZipFile,
        target: File,
        canonicalTarget: String,
    ): ContentPackRejection {
        val entries = archive.entries().toList()
        if (entries.any { isZipSlip(it.name) }) return ContentPackRejection.ZIP_SLIP
        var totalBytes = 0L
        for (entry in entries) {
            val out = File(target, entry.name.replace('\\', '/'))
            if (!out.canonicalPath.startsWith(canonicalTarget)) return ContentPackRejection.ZIP_SLIP
            if (entry.isDirectory) {
                out.mkdirs()
                continue
            }
            out.parentFile?.mkdirs()
            archive.getInputStream(entry).use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(UNPACK_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_UNPACKED_BYTES) throw OversizedPackException()
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        return ContentPackRejection.NONE
    }

    private fun defaultMapParses(json: String): Boolean = !CollegeMapData.parse(json).isEmpty

    private class OversizedPackException : RuntimeException()

    private const val UNPACK_BUFFER_SIZE = 8192
}
