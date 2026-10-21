package com.politex.data.repository

import com.politex.data.local.entity.LessonEntity

enum class TeacherRowMatch { YES, NO, AMBIGUOUS }

class TeacherInsights(
    private val teachers: List<Teacher>,
    lessons: List<LessonEntity>,

    private val nowMillis: Long = System.currentTimeMillis(),

    private val leadsOffTs: (String) -> Long? = { null },
    private val leadsOn: (String) -> Boolean = { false },
) {
    private val bySurname: Map<String, List<Teacher>> =
        teachers.groupBy { surnameOf(it.name) }.filterKeys { it.isNotBlank() }

    private val staleMillis = STALE_DAYS * 24L * 60L * 60L * 1000L

    private val matched: Map<String, TeacherFacts> = buildFacts(lessons)

    val activeNames: Set<String> =
        matched.filterValues { it.activeNow }.keys

    fun isActive(name: String): Boolean = activeNames.contains(name)

    fun rooms(name: String): List<String> = matched[name]?.rooms.orEmpty()

    fun subjects(name: String): List<String> = matched[name]?.subjects.orEmpty()

    fun lessonCount(name: String): Int = matched[name]?.count ?: 0

    fun lastSeenDate(name: String): String = matched[name]?.lastDate.orEmpty()

    internal fun lastLessonMillis(name: String): Long? = matched[name]?.lastMillis

    fun isLeadsNow(name: String): Boolean {
        if (leadsOn(name)) return true
        if (!isActive(name)) return false
        val off = leadsOffTs(name) ?: return true
        val last = matched[name]?.lastMillis ?: return false
        return last > off
    }

    private data class TeacherFacts(
        val rooms: List<String>,
        val subjects: List<String>,
        val count: Int,
        val lastDate: String,

        val activeNow: Boolean = false,

        val lastMillis: Long? = null,
    )

    private fun buildFacts(lessons: List<LessonEntity>): Map<String, TeacherFacts> {
        val rooms = mutableMapOf<String, MutableList<String>>()
        val subjects = mutableMapOf<String, MutableList<String>>()
        val counts = mutableMapOf<String, Int>()
        val weekly = mutableMapOf<String, Boolean>()
        val dateMillis = mutableMapOf<String, Long>()

        lessons.forEach { lesson ->
            val names = splitTeacherNames(lesson.teacherFirst) + splitTeacherNames(lesson.teacherSecond)
            val lessonRooms = listOf(lesson.roomFirst, lesson.roomSecond).filter { it.isNotBlank() }

            names.forEach { rawName ->
                val teacher = matchTeacher(rawName) ?: return@forEach
                val full = teacher.name

                if (lessonRooms.isNotEmpty()) {
                    rooms.getOrPut(full) { mutableListOf() }.addAll(lessonRooms)
                }
                if (lesson.subjectRaw.isNotBlank()) {
                    subjects.getOrPut(full) { mutableListOf() }.add(lesson.subjectRaw)
                }
                counts[full] = (counts[full] ?: 0) + 1

                val dateKey = lesson.dateString.replace("-", ".")
                if (dateKey.isNotBlank()) {

                    parseDotDateMillis(dateKey)?.let { ms ->
                        val prevMs = dateMillis[full]
                        if (prevMs == null || ms > prevMs) dateMillis[full] = ms
                    }
                } else {

                    weekly[full] = true
                }
            }
        }

        return counts.keys.associateWith { full ->
            TeacherFacts(
                rooms =
                rooms[full]
                    .orEmpty()
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key },
                subjects = subjects[full].orEmpty().distinct().take(6),
                count = counts[full] ?: 0,
                lastDate = dateMillis[full]?.let { com.politex.util.DateFormats.dotDate(java.util.Date(it)) }.orEmpty(),
                lastMillis = dateMillis[full],
                activeNow =
                weekly[full] == true ||
                    (dateMillis[full] != null && dateMillis[full]!! >= nowMillis - staleMillis),
            )
        }
    }

    private fun parseDotDateMillis(date: String): Long? {
        val parts = date.split(".")
        if (parts.size != 3) return null
        val pattern =
            if (parts[0].length == 4) "yyyy.MM.dd" else "dd.MM.yyyy"
        return runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).apply {
                isLenient = false
            }.parse(date)?.time
        }.getOrNull()
    }

    fun matchTeacher(rawName: String): Teacher? {
        val clean = rawName.trim().trim(',', '/', '-', ' ')
        if (clean.length < 3) return null

        val surname = surnameOf(clean)
        if (surname.isBlank()) return null

        bySurname[surname]?.let { candidates ->
            if (candidates.size == 1) return candidates.first()

            val initials = initialsOf(clean)
            if (initials.isEmpty()) return null
            return candidates.firstOrNull { teacher ->
                val candidateInitials = initialsOf(teacher.name)
                candidateInitials.isNotEmpty() && candidateInitials.startsWith(initials)
            }
        }

        if (surname.length >= 5) {
            val prefixMatches = bySurname.filterKeys { it.startsWith(surname) }
            if (prefixMatches.size == 1) {
                val candidates = prefixMatches.values.first()
                if (candidates.size == 1) return candidates.first()
                val initials = initialsOf(clean)
                return candidates.firstOrNull { teacher ->
                    initials.isNotEmpty() && initialsOf(teacher.name).startsWith(initials)
                }
            }
        }

        return null
    }

    companion object {

        const val STALE_DAYS = 30L

        fun matchRowToTeacher(
            rowTeacherName: String,
            selected: Teacher,
            allTeachers: List<Teacher>,
        ): TeacherRowMatch {
            val selectedKey = surnameOf(selected.name)
            if (selectedKey.isBlank()) return TeacherRowMatch.NO
            val candidates = allTeachers.filter { surnameOf(it.name) == selectedKey }
            val parts = splitTeacherNames(rowTeacherName).filter { surnameOf(it) == selectedKey }
            if (parts.isEmpty()) return TeacherRowMatch.NO
            if (candidates.size <= 1) return TeacherRowMatch.YES

            var ambiguous = false
            for (part in parts) {
                val ini = initialsOf(part)
                if (ini.isEmpty()) {
                    ambiguous = true
                    continue
                }
                val matches = candidates.filter { initialsOf(it.name).startsWith(ini) }
                if (matches.size == 1) {
                    if (matches[0].name == selected.name) return TeacherRowMatch.YES
                } else if (matches.size > 1) {
                    ambiguous = true
                }
            }
            return if (ambiguous) TeacherRowMatch.AMBIGUOUS else TeacherRowMatch.NO
        }

        fun splitTeacherNames(raw: String): List<String> = raw
            .split('/')
            .map { it.trim() }
            .filter { it.length >= 3 }

        fun surnameOf(raw: String): String {
            val first =
                raw
                    .trim()
                    .replace(Regex("[0-9]"), "")
                    .split(Regex("\\s+"))
                    .firstOrNull() ?: return ""
            return first.trim(' ', ',', '.', '-', '/', '\\').lowercase().replace('ё', 'е')
        }

        fun initialsOf(raw: String): String {
            val parts = raw.trim().split(Regex("\\s+")).drop(1)
            return parts
                .joinToString("") { part ->
                    part.trim('.', ',', '/', '-').take(1).lowercase().replace('ё', 'е')
                }.filter { it.isLetter() }
        }
    }
}
