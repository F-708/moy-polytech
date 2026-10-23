package com.politex.data.network

import com.politex.data.local.entity.LessonEntity
import com.politex.data.model.CollegeBellSchedule
import com.politex.util.BinaryTextExtract
import com.politex.util.CuratorStore
import com.politex.util.DateFormats
import com.politex.util.Docx
import com.politex.util.Dow
import com.politex.util.GroupParser
import com.politex.util.SubjectFormatter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Locale

data class SchedulePostLink(
    val url: String,
    val title: String,
    val dateString: String = "",
)

data class ParseStats(
    val format: String = "",
    val strategy: String = "",
    val runsExtracted: Int = 0,
    val groupFound: Boolean = false,
    val wasHtml: Boolean = false,

    val parseError: String = "",

    val textPreview: String = "",
)

object MpkScheduleParser {

    @Volatile
    var lastParseStats: ParseStats = ParseStats()

    private val statsLock = Any()

    private fun mutateStats(block: (ParseStats) -> ParseStats) {
        synchronized(statsLock) { lastParseStats = block(lastParseStats) }
    }

    private fun resetStats(format: String) {
        mutateStats { ParseStats(format = format) }
    }

    internal fun statsSnapshot(): ParseStats = synchronized(statsLock) { lastParseStats }

    private val CP1251 = Charset.forName("windows-1251")

    val DAY_KEYWORDS = mapOf(
        "понедельник" to 1,
        "вторник" to 2,
        "среда" to 3,
        "четверг" to 4,
        "пятница" to 5,
        "суббота" to 6,
    )

    private val POST_LINK_REGEX = Regex(
        "<a[^>]+href=[\"']([^\"']*(?:raspisanie|uchashhihsya|[0-9]{2}[.-][0-9]{2}[.-][0-9]{4})[^\"']*)[\"'][^>]*>(.*?)</a>",
        RegexOption.IGNORE_CASE,
    )

    private val IFRAME_SRC_REGEX = Regex(
        "(?i)<iframe[^>]+src=[\"']([^\"']+)[\"']",
    )

    private val OFFICE_VIEWER_SRC_REGEX = Regex(
        "(?i)src=(https?%3A%2F%2F[^&\"']+|https?://[^&\"']+)",
    )

    private val DATE_IN_TEXT_REGEX = Regex(
        "([0-3]?[0-9])[.\\-/]([0-1]?[0-9])[.\\-/](20[0-9]{2})",
    )

    private val WS_REGEX = Regex("\\s+")
    private val TAG_STRIP_REGEX = Regex("<[^>]+>")
    private val DOC_LINK_ATTR_REGEX =
        Regex("(?i)(?:href|src|data-src|data-href)=[\"']([^\"']+\\.(?:docx|doc)(?:\\?[^\"']*)?)[\"']")
    private val DOC_JSON_LINK_REGEX =
        Regex("(?i)\"(?:source_url|guid|url)\"\\s*:\\s*\"([^\"]+\\.(?:docx|doc)[^\"]*)\"")
    private val WP_UPLOADS_REGEX =
        Regex("(?i)https?://[a-zA-Z0-9.-]+/wp-content/uploads/[a-zA-Z0-9/_.-]+\\.(?:doc|docx)")
    private val HEADER_ZIP_REGEX = Regex("РАСПИСАНИЕ[^<]*", RegexOption.IGNORE_CASE)
    private val HEADER_LINE_REGEX = Regex("РАСПИСАНИЕ[^\\n]*", RegexOption.IGNORE_CASE)
    private val LEADING_LESSON_REGEX = Regex("^(10|[1-9])(\\s|$)")
    private val CELL_LESSON_HEAD_REGEX = Regex("^(10|[1-9])\\s*(.*)$")
    private val DOUBLE_ROOMS_TAIL_REGEX =
        Regex("(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД|-{1,7})\\s*/\\s*(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД|-{1,7})?\\s*$")
    private val ROOM_TAIL_REGEX = Regex("(?:каб\\.?|ауд\\.?)?\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?|СТД)$")
    private val KAB_PAIR_REGEX =
        Regex("(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?)\\s*[/,-]\\s*(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?)", RegexOption.IGNORE_CASE)
    private val SLASH_ROOMS_REGEX = Regex("([0-9]{2,3}[Ѐ-ӿa-zA-Z]?)\\s*/\\s*([0-9]{2,3}[Ѐ-ӿa-zA-Z]?)")
    private val ROOM_ANY_REGEX =
        Regex("(?:каб\\.?|ауд\\.?)\\s*([0-9]{1,3}[Ѐ-ӿa-zA-Z]?)|([0-9]{2,3}[Ѐ-ӿa-zA-Z]?)\\s*(?:каб|ауд)", RegexOption.IGNORE_CASE)
    private val ROOM_END_DIGITS_REGEX = Regex("([1-9][0-9]{1,2}[Ѐ-ӿa-zA-Z]?)$")
    private val TEACHER_IN_SUBJECT_REGEX =
        Regex("([\\u0410-\\u042F\\u0401][\\u0430-\\u044F\\u0451]+)\\s+([\\u0410-\\u042F\\u0401]\\.\\s*[\\u0410-\\u042F\\u0401]\\.)")
    private val LESSON_PREFIX_STRIP_REGEX =
        Regex("(?:^(?:10|[1-9])\\s*урок|^(?:10|[1-9])\\s*пара|^(?:10|[1-9])\\s*\\.|^№\\s*(?:10|[1-9]))", RegexOption.IGNORE_CASE)
    private val SUBGROUP_STRIP_REGEX = Regex("(?:1|2)\\s*п/?г|(?:1|2)\\s*подгруппа", RegexOption.IGNORE_CASE)
    private val TEACHER_FIO_REGEX =
        Regex("([\\u0410-\\u042F\\u0401][\\u0430-\\u044F\\u0451]+)\\s+([\\u0410-\\u042F\\u0401])\\.?\\s*([\\u0410-\\u042F\\u0401])?\\.?")
    private val DIGITS_REGEX = Regex("[0-9]")
    private val GROUP_LINE_REGEX =
        Regex("(?:группа\\s+)([1-4][1-9]\\s*[\\u0410-\\u042FA-Z])|(?<![0-9A-Za-z_\\u0400-\\u04FF])([1-4][1-9]\\s*[\\u0410-\\u042FA-Z])(?![0-9A-Za-z_\\u0400-\\u04FF])", RegexOption.IGNORE_CASE)
    private val PURE_HEADER_REGEX =
        Regex("^(?:группа\\s*)?[1-4][1-9]\\s*[\\u0410-\\u042FA-Z](?:\\s+(?:понедельник|вторник|среда|четверг|пятница|суббота))?$", RegexOption.IGNORE_CASE)
    private val P2_SPLIT_REGEX = Regex("(?:/\\s*|\\s+)(?:2\\s*п/?г|2\\s*подгруппа)", RegexOption.IGNORE_CASE)

    fun parseDotDate(date: String): java.util.Calendar? {
        val iso = Regex("\\s*(20[0-9]{2})-([0-1]?[0-9])-([0-3]?[0-9])\\s*").find(date)
        if (iso != null) {
            val cal = java.util.Calendar.getInstance()
            cal.clear()
            cal.set(iso.groupValues[1].toInt(), iso.groupValues[2].toInt() - 1, iso.groupValues[3].toInt())
            return cal
        }
        val m = Regex("\\s*([0-3]?[0-9])[.\\-/]([0-1]?[0-9])[.\\-/]([0-9]{4})\\s*").find(date) ?: return null
        val cal = java.util.Calendar.getInstance()
        cal.clear()
        cal.set(m.groupValues[3].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[1].toInt())
        return cal
    }

    fun dowOfCalendar(cal: java.util.Calendar): Int = Dow.of(cal)

    internal fun restampByDow(
        lessons: List<LessonEntity>,
        anchorCal: java.util.Calendar,
        anchorDow: Int,
    ): List<LessonEntity> = lessons.map { lesson ->
        val cal = (anchorCal.clone() as java.util.Calendar)
        cal.add(java.util.Calendar.DAY_OF_YEAR, (lesson.dayOfWeek - anchorDow).coerceIn(-6, 6))
        lesson.copy(dateString = DateFormats.dotDate(cal))
    }

    fun findSchedulePostLinks(html: String, baseUrl: String = "https://guo-mpk.by/"): List<SchedulePostLink> {
        val result = mutableListOf<SchedulePostLink>()
        val seenUrls = mutableSetOf<String>()

        POST_LINK_REGEX.findAll(html).forEach { match ->
            val rawUrl = match.groupValues[1].trim()
            val rawTitle = match.groupValues[2].replace(TAG_STRIP_REGEX, " ").trim()
            val absoluteUrl = resolveAbsoluteUrl(baseUrl, rawUrl)

            if (!seenUrls.contains(absoluteUrl) && !absoluteUrl.endsWith("/raspisanie/") && !absoluteUrl.endsWith("/raspisanie")) {
                seenUrls.add(absoluteUrl)

                val dateMatch = DATE_IN_TEXT_REGEX.find(rawTitle) ?: DATE_IN_TEXT_REGEX.find(rawUrl)
                val dateString = if (dateMatch != null) {
                    val d = dateMatch.groupValues[1].padStart(2, '0')
                    val m = dateMatch.groupValues[2].padStart(2, '0')
                    val y = dateMatch.groupValues[3]
                    "$d.$m.$y"
                } else {
                    ""
                }

                result.add(
                    SchedulePostLink(
                        url = absoluteUrl,
                        title = rawTitle.ifBlank { "Расписание учащихся" },
                        dateString = dateString,
                    ),
                )
            }
        }

        Regex("\"link\"\\s*:\\s*\"([^\"]+)\"").findAll(html).forEach { match ->
            val rawUrl = match.groupValues[1].replace("\\/", "/")
            val absoluteUrl = resolveAbsoluteUrl(baseUrl, rawUrl)
            if (!seenUrls.contains(absoluteUrl) && !absoluteUrl.endsWith("/raspisanie/")) {
                seenUrls.add(absoluteUrl)
                val dateMatch = DATE_IN_TEXT_REGEX.find(absoluteUrl)
                val dateString = if (dateMatch != null) {
                    val d = dateMatch.groupValues[1].padStart(2, '0')
                    val m = dateMatch.groupValues[2].padStart(2, '0')
                    val y = dateMatch.groupValues[3]
                    "$d.$m.$y"
                } else {
                    ""
                }

                result.add(
                    SchedulePostLink(
                        url = absoluteUrl,
                        title = "Расписание",
                        dateString = dateString,
                    ),
                )
            }
        }

        return result
    }

    fun extractDocumentUrls(html: String, baseUrl: String = "https://guo-mpk.by/"): List<String> {
        val urls = mutableSetOf<String>()

        IFRAME_SRC_REGEX.findAll(html).forEach { match ->
            val iframeSrc = match.groupValues[1]
            OFFICE_VIEWER_SRC_REGEX.find(iframeSrc)?.let { officeMatch ->
                var rawUrl = officeMatch.groupValues[1]
                if (rawUrl.contains("%")) {
                    try {
                        rawUrl = URLDecoder.decode(rawUrl, "UTF-8")
                    } catch (_: Exception) {}
                }
                urls.add(resolveAbsoluteUrl(baseUrl, rawUrl))
            }
        }

        DOC_LINK_ATTR_REGEX.findAll(html).forEach { match ->
            val link = match.groupValues[1]
            if (!link.contains("officeapps.live.com") && !link.contains("docs.google.com")) {
                urls.add(resolveAbsoluteUrl(baseUrl, link))
            }
        }

        DOC_JSON_LINK_REGEX.findAll(html).forEach { match ->
            val link = match.groupValues[1].replace("\\/", "/")
            urls.add(resolveAbsoluteUrl(baseUrl, link))
        }

        WP_UPLOADS_REGEX.findAll(html).forEach { match ->
            urls.add(match.value)
        }

        return urls.toList()
    }

    fun parseFile(
        bytes: ByteArray,
        targetGroup: String,
        targetDate: String = "",
    ): List<LessonEntity> {
        val parsed = parseFileRaw(bytes, targetGroup, targetDate)
        return applyHeaderDate(parsed, bytes, targetDate)
    }

    fun parseFileAllGroups(
        bytes: ByteArray,
        targetDate: String = "",
        candidateGroups: List<String> = emptyList(),
    ): List<LessonEntity> {
        if (bytes.isEmpty() || candidateGroups.isEmpty()) return emptyList()

        val texts = runCatching { extractDocTexts(bytes) }.getOrNull()
        val groups = (candidateGroups + discoverDocumentGroups(texts)).distinct()
        val out = LinkedHashMap<String, LessonEntity>()
        for (group in groups) {
            val lessons = runCatching {
                if (texts != null) parseFileRawTexts(texts, group, targetDate) else parseFileRaw(bytes, group, targetDate)
            }.getOrDefault(emptyList())
            for (lesson in lessons) {
                if (lesson.groupName.isBlank()) continue

                val key = "${lesson.groupName}_${lesson.dayOfWeek}_${lesson.lessonNumber}_${lesson.dateString}"
                out.putIfAbsent(key, lesson)
            }
        }
        val allLessons = out.values.toList()
        runCatching { CuratorStore.record(collectCuratorPairs(allLessons)) }
        return applyHeaderDate(allLessons, bytes, targetDate)
    }

    internal const val CURATOR_HOUR = "Кураторский час"

    private val CURATOR_HOUR_RAW_VARIANTS = setOf(
        "КУРЧ",
        "КУРЧАС",
        "КУР.ЧАС",
        "КУР. ЧАС",
        "КУР ЧАС",
        "КУРАТОРСКИЙ ЧАС",
    )

    internal fun collectCuratorPairs(lessons: Collection<LessonEntity>): Map<String, String> {
        val pairs = LinkedHashMap<String, String>()
        for (lesson in lessons) {
            val group = lesson.groupName.trim()
            if (group.isBlank()) continue
            if (!isCuratorHour(lesson)) continue
            val teacher = lesson.teacherFirst.trim().ifBlank { lesson.teacherSecond.trim() }
            if (teacher.isBlank()) continue
            pairs.putIfAbsent(group, teacher)
        }
        return pairs
    }

    private fun isCuratorHour(lesson: LessonEntity): Boolean {
        if (lesson.officialSubjectName.equals(CURATOR_HOUR, ignoreCase = true)) return true
        val raw = lesson.subjectRaw
            .trim()
            .replace(WS_REGEX, " ")
            .uppercase(Locale.ROOT)
        return raw in CURATOR_HOUR_RAW_VARIANTS
    }

    internal fun discoverDocumentGroups(texts: DocTexts?): List<String> {
        if (texts == null || texts.wasHtml) return emptyList()
        val text =
            when {
                texts.isZip && !texts.docxXml.isNullOrBlank() ->
                    runCatching { TeacherScheduleParser.xmlToPseudoTable(texts.docxXml) }.getOrDefault("")
                texts.utf16Text.isNotBlank() -> texts.utf16Text
                texts.cp1251Text.isNotBlank() -> texts.cp1251Text
                else -> ""
            }
        if (text.isBlank()) return emptyList()
        val found = LinkedHashSet<String>()
        for (line in text.lineSequence()) {
            for (rawCell in line.split('│', '║', '|', '\t')) {
                val cell = rawCell.trim()
                if (cell.isEmpty()) continue
                val bare =
                    if (cell.startsWith("группа", ignoreCase = true)) {
                        cell.substring("группа".length).trim()
                    } else {
                        cell
                    }
                if (GroupParser.isValid(bare)) found.add(GroupParser.cleanRawGroupName(bare))
            }
        }
        return found.toList()
    }

    internal class DocTexts(
        val wasHtml: Boolean,
        val isZip: Boolean,
        val docxXml: String?,
        val isOle: Boolean,
        val utf16Runs: List<String>,
        val utf16Text: String,
        val cp1251Runs: List<String>,
        val cp1251Text: String,
        val utf8Text: String,
        val cp1251RawText: String,
    )

    internal fun extractDocTexts(bytes: ByteArray): DocTexts {
        val head = String(bytes.copyOfRange(0, minOf(600, bytes.size)), Charsets.UTF_8)
            .lowercase(Locale.ROOT)
        if (head.contains("<!doctype html") || head.contains("<html")) {
            return DocTexts(true, false, null, false, emptyList(), "", emptyList(), "", "", "")
        }
        val isZip = Docx.isZip(bytes)
        val docxXml = if (isZip) Docx.readDocumentXml(bytes) else null
        val utf16Runs = extractUtf16LeRuns(bytes)
        val cp1251Runs = extractCp1251Runs(bytes)
        val isOle = bytes.size >= 4 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() &&
            bytes[3] == 0xE0.toByte()
        return DocTexts(
            wasHtml = false,
            isZip = isZip,
            docxXml = docxXml,
            isOle = isOle,
            utf16Runs = utf16Runs,
            utf16Text = utf16Runs.joinToString("\n"),
            cp1251Runs = cp1251Runs,
            cp1251Text = cp1251Runs.joinToString("\n"),
            utf8Text = String(bytes, Charsets.UTF_8),
            cp1251RawText = String(bytes, CP1251),
        )
    }

    internal fun parseFileRawTexts(
        texts: DocTexts,
        targetGroup: String,
        targetDate: String = "",
    ): List<LessonEntity> {
        if (texts.wasHtml) {
            mutateStats { ParseStats(format = "html", wasHtml = true, groupFound = false) }
            return emptyList()
        }

        if (texts.isZip) {
            resetStats("docx")
            val docxLessons = if (texts.docxXml.isNullOrBlank()) {
                emptyList()
            } else {
                parseDocxXml(texts.docxXml, targetGroup, targetDate)
            }
            if (docxLessons.isNotEmpty()) return docxLessons
        }

        resetStats("doc")
        val docLessons = parseDocBinaryTexts(texts, targetGroup, targetDate)
        if (docLessons.isNotEmpty()) return docLessons

        if (texts.isOle) return emptyList()

        resetStats("text")
        val textLessons = runCatching { parsePlainTextSchedule(texts.utf8Text, targetGroup, targetDate) }
            .onFailure {
                mutateStats { s ->
                    s.copy(parseError = (s.parseError + "; text:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                }
            }
            .getOrDefault(emptyList())
        if (textLessons.isNotEmpty()) return textLessons

        resetStats("text-cp1251")
        return runCatching { parsePlainTextSchedule(texts.cp1251RawText, targetGroup, targetDate) }
            .onFailure {
                mutateStats { s ->
                    s.copy(parseError = (s.parseError + "; cp1251-text:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                }
            }
            .getOrDefault(emptyList())
    }

    private fun parseDocBinaryTexts(
        texts: DocTexts,
        targetGroup: String,
        targetDate: String,
    ): List<LessonEntity> {
        mutateStats { it.copy(format = "doc-utf16", runsExtracted = texts.utf16Runs.size) }
        if (texts.utf16Text.isNotBlank()) {
            val utf16Lessons = runCatching { parsePlainTextSchedule(texts.utf16Text, targetGroup, targetDate) }
                .onFailure {
                    mutateStats { s ->
                        s.copy(parseError = (s.parseError + "; utf16:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                    }
                }
                .getOrDefault(emptyList())
            if (utf16Lessons.isNotEmpty()) return utf16Lessons
        }

        val utf16Stats = statsSnapshot()
        mutateStats { it.copy(format = "doc-cp1251", runsExtracted = texts.cp1251Runs.size) }
        if (texts.cp1251Text.isNotBlank()) {
            val cp1251Lessons = runCatching { parsePlainTextSchedule(texts.cp1251Text, targetGroup, targetDate) }
                .onFailure {
                    mutateStats { s ->
                        s.copy(parseError = (s.parseError + "; cp1251:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                    }
                }
                .getOrDefault(emptyList())
            if (cp1251Lessons.isNotEmpty()) return cp1251Lessons
        }

        synchronized(statsLock) {
            lastParseStats = if (lastParseStats.parseError.isBlank() && lastParseStats.textPreview.isBlank()) {
                utf16Stats
            } else if (utf16Stats.textPreview.isNotBlank()) {
                lastParseStats.copy(
                    textPreview = utf16Stats.textPreview,
                    runsExtracted = utf16Stats.runsExtracted,
                )
            } else {
                lastParseStats
            }
        }

        return emptyList()
    }

    fun parseFileRaw(
        bytes: ByteArray,
        targetGroup: String,
        targetDate: String = "",
    ): List<LessonEntity> {
        if (bytes.isEmpty()) return emptyList()

        val head = String(bytes.copyOfRange(0, minOf(600, bytes.size)), Charsets.UTF_8)
            .lowercase(Locale.ROOT)
        if (head.contains("<!doctype html") || head.contains("<html")) {
            mutateStats { ParseStats(format = "html", wasHtml = true, groupFound = false) }
            return emptyList()
        }

        if (Docx.isZip(bytes)) {
            resetStats("docx")
            val docxLessons = parseDocx(ByteArrayInputStream(bytes), targetGroup, targetDate)
            if (docxLessons.isNotEmpty()) return docxLessons
        }

        resetStats("doc")
        val docLessons = parseDocBinary(bytes, targetGroup, targetDate)
        if (docLessons.isNotEmpty()) return docLessons

        val isOleDoc = bytes.size >= 4 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() &&
            bytes[3] == 0xE0.toByte()
        if (isOleDoc) return emptyList()

        resetStats("text")
        val textUtf8 = String(bytes, Charsets.UTF_8)
        val textLessons = runCatching { parsePlainTextSchedule(textUtf8, targetGroup, targetDate) }
            .onFailure {
                mutateStats { s ->
                    s.copy(parseError = (s.parseError + "; text:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                }
            }
            .getOrDefault(emptyList())
        if (textLessons.isNotEmpty()) return textLessons

        resetStats("text-cp1251")
        val textCp1251 = String(bytes, CP1251)
        return runCatching { parsePlainTextSchedule(textCp1251, targetGroup, targetDate) }
            .onFailure {
                mutateStats { s ->
                    s.copy(parseError = (s.parseError + "; cp1251-text:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                }
            }
            .getOrDefault(emptyList())
    }

    private fun applyHeaderDate(
        lessons: List<LessonEntity>,
        bytes: ByteArray,
        targetDate: String,
    ): List<LessonEntity> {
        if (lessons.isEmpty()) return lessons

        val headerText = extractHeaderText(bytes)
        val headerDate = extractHeaderDate(headerText)
        val anchor = parseDotDate(headerDate ?: targetDate) ?: return lessons
        val anchorDow = if (headerDate != null) {
            Dow.ofWord(headerText) ?: dowOfCalendar(anchor)
        } else {
            dowOfCalendar(anchor)
        }
        val stamped = restampByDow(lessons, anchor, anchorDow)

        if (stamped.map { it.dateString }.toSet() == lessons.map { it.dateString }.toSet()) return lessons
        return stamped
    }

    private fun extractHeaderText(bytes: ByteArray): String {
        val fromZip = if (Docx.isZip(bytes)) {
            runCatching {
                Docx.readDocumentXml(bytes)?.let { HEADER_ZIP_REGEX.find(it)?.value }
            }.getOrNull()
        } else {
            null
        }
        if (!fromZip.isNullOrBlank()) return fromZip

        val utf16Text = runCatching { extractUtf16LeRuns(bytes).joinToString("\n") }.getOrDefault("")
        val firstU16 = HEADER_LINE_REGEX.find(utf16Text)?.value
        if (firstU16 != null) return firstU16

        val cp1251Text = runCatching { String(bytes, CP1251) }.getOrDefault("")
        return HEADER_LINE_REGEX.find(cp1251Text)?.value ?: ""
    }

    private fun extractHeaderDate(headerText: String): String? {
        val m = DATE_IN_TEXT_REGEX.find(headerText) ?: return null
        return "${m.groupValues[1].padStart(2, '0')}.${m.groupValues[2].padStart(2, '0')}.${m.groupValues[3]}"
    }

    fun documentMatchesDate(bytes: ByteArray, dateDot: String): Boolean {
        val headerDate = extractHeaderDate(extractHeaderText(bytes)) ?: return true
        val doc = parseDotDate(headerDate) ?: return true
        val req = parseDotDate(dateDot) ?: return true
        return doc.get(java.util.Calendar.YEAR) == req.get(java.util.Calendar.YEAR) &&
            doc.get(java.util.Calendar.MONTH) == req.get(java.util.Calendar.MONTH) &&
            doc.get(java.util.Calendar.DAY_OF_MONTH) == req.get(java.util.Calendar.DAY_OF_MONTH)
    }

    fun parseDocBinary(
        bytes: ByteArray,
        targetGroup: String,
        targetDate: String = "",
    ): List<LessonEntity> {
        val utf16Runs = extractUtf16LeRuns(bytes)
        mutateStats { it.copy(format = "doc-utf16", runsExtracted = utf16Runs.size) }
        val utf16Text = utf16Runs.joinToString("\n")
        if (utf16Text.isNotBlank()) {
            val utf16Lessons = runCatching { parsePlainTextSchedule(utf16Text, targetGroup, targetDate) }
                .onFailure {
                    mutateStats { s ->
                        s.copy(parseError = (s.parseError + "; utf16:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                    }
                }
                .getOrDefault(emptyList())
            if (utf16Lessons.isNotEmpty()) return utf16Lessons
        }

        val utf16Stats = statsSnapshot()
        val cp1251Runs = extractCp1251Runs(bytes)
        mutateStats { it.copy(format = "doc-cp1251", runsExtracted = cp1251Runs.size) }
        val cp1251Text = cp1251Runs.joinToString("\n")
        if (cp1251Text.isNotBlank()) {
            val cp1251Lessons = runCatching { parsePlainTextSchedule(cp1251Text, targetGroup, targetDate) }
                .onFailure {
                    mutateStats { s ->
                        s.copy(parseError = (s.parseError + "; cp1251:${it.javaClass.simpleName}: ${it.message}").trim(';', ' '))
                    }
                }
                .getOrDefault(emptyList())
            if (cp1251Lessons.isNotEmpty()) return cp1251Lessons
        }

        synchronized(statsLock) {
            lastParseStats = if (lastParseStats.parseError.isBlank() && lastParseStats.textPreview.isBlank()) {
                utf16Stats
            } else if (utf16Stats.textPreview.isNotBlank()) {
                lastParseStats.copy(
                    textPreview = utf16Stats.textPreview,
                    runsExtracted = utf16Stats.runsExtracted,
                )
            } else {
                lastParseStats
            }
        }

        return emptyList()
    }

    private fun extractCp1251Runs(bytes: ByteArray): List<String> = BinaryTextExtract.cp1251Runs(bytes, bellAsTab = true, minRunBytes = 4) { BinaryTextExtract.hasText(it) }

    fun extractUtf16LeRuns(bytes: ByteArray): List<String> = BinaryTextExtract.utf16LeRuns(bytes, bellAsTab = true) { decoded ->
        BinaryTextExtract.hasText(decoded) || (decoded.length >= 6 && decoded.all { it.isWhitespace() })
    }

    fun parseDocx(
        inputStream: InputStream,
        targetGroup: String,
        targetDate: String = "",
    ): List<LessonEntity> {
        val documentXml = Docx.readDocumentXml(inputStream)
        if (documentXml.isNullOrBlank()) {
            return emptyList()
        }
        return parseDocxXml(documentXml, targetGroup, targetDate)
    }

    fun parseDocxXml(
        xmlContent: String,
        targetGroup: String,
        targetDate: String = "",
    ): List<LessonEntity> {
        val cleanTargetGroup = GroupParser.cleanRawGroupName(targetGroup).uppercase(Locale.ROOT)
        val lessons = mutableListOf<LessonEntity>()

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(xmlContent.reader())

            var inTable = false
            var inRow = false
            var inCell = false
            var currentCellText = StringBuilder()
            val rowCells = mutableListOf<String>()
            val allRows = mutableListOf<List<String>>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "tbl" -> {
                                inTable = true
                                allRows.clear()
                            }
                            "tr" -> {
                                inRow = true
                                rowCells.clear()
                            }
                            "tc" -> {
                                inCell = true
                                currentCellText = StringBuilder()
                            }
                            "t" -> {
                                if (inCell) {
                                    parser.next()
                                    if (parser.text != null) {
                                        currentCellText.append(parser.text).append(" ")
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tag) {
                            "tc" -> {
                                inCell = false
                                rowCells.add(currentCellText.toString().trim())
                            }
                            "tr" -> {
                                inRow = false
                                if (rowCells.isNotEmpty()) {
                                    allRows.add(rowCells.toList())
                                }
                            }
                            "tbl" -> {
                                inTable = false
                                val parsedTableLessons = parseTableData(allRows, cleanTargetGroup, targetDate)
                                lessons.addAll(parsedTableLessons)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            return parsePlainTextSchedule(xmlContent.replace(TAG_STRIP_REGEX, " "), cleanTargetGroup, targetDate)
        }

        return lessons
    }

    private fun parseTableData(
        rows: List<List<String>>,
        targetGroup: String,
        targetDate: String,
    ): List<LessonEntity> {
        val result = mutableListOf<LessonEntity>()
        if (rows.isEmpty()) return result

        var targetColIndex = -1
        var headerRowIndex = -1

        for ((rIdx, row) in rows.withIndex()) {
            for ((cIdx, cell) in row.withIndex()) {
                if (GroupParser.matchesGroup(cell, targetGroup)) {
                    targetColIndex = cIdx
                    headerRowIndex = rIdx
                    break
                }
            }
            if (targetColIndex != -1) break
        }

        var currentDay = 1
        val startRow = if (headerRowIndex != -1) headerRowIndex + 1 else 0

        for (rIdx in startRow until rows.size) {
            val row = rows[rIdx]
            if (row.isEmpty()) continue

            val firstCell = row.getOrNull(0)?.lowercase(Locale.ROOT) ?: ""
            for ((dayName, dayNum) in DAY_KEYWORDS) {
                if (firstCell.contains(dayName)) {
                    currentDay = dayNum
                    break
                }
            }

            val lessonNumber = detectLessonNumber(row) ?: continue

            val lessonCellContent = if (targetColIndex > 0 && targetColIndex < row.size) {
                row[targetColIndex]
            } else if (row.size > 1) {
                row.drop(1).joinToString(" ")
            } else {
                row.firstOrNull() ?: ""
            }

            if (lessonCellContent.isNotBlank() && !GroupParser.matchesGroup(lessonCellContent, targetGroup)) {
                val lesson = createLessonEntity(
                    groupName = targetGroup,
                    dayOfWeek = currentDay,
                    lessonNumber = lessonNumber,
                    rawContent = lessonCellContent,
                    dateString = targetDate,
                )
                if (lesson != null) {
                    result.add(lesson)
                }
            }
        }

        return result
    }

    private val LESSON_NUMBER_IN_TEXT = Regex(
        "(?:(10|[1-9])\\s*урок|(10|[1-9])\\s*пара|^№?\\s*(10|[1-9])(?![0-9]))",
        RegexOption.IGNORE_CASE,
    )

    internal fun detectLessonNumber(row: List<String>): Int? {
        for (cell in row.take(3)) {
            val trimmed = cell.trim()
            val asInt = trimmed.toIntOrNull()
            if (asInt != null && asInt in 1..12) return asInt
            val match = LESSON_NUMBER_IN_TEXT.find(trimmed)
            if (match != null) {
                val numStr = match.groupValues[1].ifEmpty {
                    match.groupValues[2].ifEmpty { match.groupValues[3] }
                }
                return numStr.toIntOrNull()
            }
        }
        return null
    }

    fun parsePlainTextSchedule(
        text: String,
        targetGroup: String,
        targetDate: String = "",
    ): List<LessonEntity> {
        if (text.isBlank()) return emptyList()

        val cleanTargetGroup = GroupParser.cleanRawGroupName(targetGroup).uppercase(Locale.ROOT)
        val lines = text.lines().map { it.trimEnd() }

        var detectedDay = 1
        var detectedDate = targetDate

        val searchScopeText = lines.take(100).joinToString(" ").lowercase(Locale.ROOT)
        for ((dayName, dayNum) in DAY_KEYWORDS) {
            if (searchScopeText.contains(dayName)) {
                detectedDay = dayNum
                break
            }
        }

        if (detectedDate.isBlank()) {
            val dateMatch = DATE_IN_TEXT_REGEX.find(searchScopeText)
            if (dateMatch != null) {
                val d = dateMatch.groupValues[1].padStart(2, '0')
                val m = dateMatch.groupValues[2].padStart(2, '0')
                val y = dateMatch.groupValues[3]
                detectedDate = "$d.$m.$y"
            }
        }

        mutateStats {
            it.copy(textPreview = text.replace(WS_REGEX, " ").take(120))
        }
        val strategyErrors = mutableListOf<String>()

        val tableLessons = runCatching { parseTableGridSchedule(lines, cleanTargetGroup, detectedDay, detectedDate) }
            .onFailure { strategyErrors.add("grid:${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(emptyList())
        if (tableLessons.isNotEmpty()) {
            mutateStats { it.copy(strategy = "grid", groupFound = true, parseError = strategyErrors.joinToString("; ")) }
            return tableLessons
        }

        val cellLessons = runCatching { parseCellRunsSchedule(lines, cleanTargetGroup, detectedDay, detectedDate) }
            .onFailure { strategyErrors.add("cells:${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(emptyList())
        if (cellLessons.isNotEmpty()) {
            mutateStats { it.copy(strategy = "cells", groupFound = true, parseError = strategyErrors.joinToString("; ")) }
            return cellLessons
        }

        val lineLessons = runCatching { parseLineByLineFallback(lines, cleanTargetGroup, detectedDay, detectedDate) }
            .onFailure { strategyErrors.add("line:${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(emptyList())
        mutateStats {
            it.copy(
                strategy = if (lineLessons.isNotEmpty()) "line" else strategyErrors.joinToString("; ").ifBlank { "none" },
                parseError = strategyErrors.joinToString("; "),
            )
        }
        return lineLessons
    }

    fun parseCellRunsSchedule(
        lines: List<String>,
        targetGroup: String,
        defaultDay: Int,
        dateString: String,
    ): List<LessonEntity> {
        val lessons = mutableListOf<LessonEntity>()

        data class HeaderBlock(val groups: List<String>, val endIdx: Int)

        val blocks = mutableListOf<HeaderBlock>()
        var i = 0
        while (i < lines.size) {
            if (GroupParser.isValid(lines[i].trim())) {
                val groups = mutableListOf<String>()
                var j = i
                while (j < lines.size && GroupParser.isValid(lines[j].trim()) && groups.size < 12) {
                    groups.add(lines[j].trim())
                    j++
                }
                if (groups.size >= 2) {
                    blocks.add(HeaderBlock(groups, j))
                    i = j
                    continue
                }
            }
            i++
        }
        if (blocks.isEmpty()) return emptyList()

        var targetBlockFound = false
        for ((bIdx, block) in blocks.withIndex()) {
            val targetCol = block.groups.indexOfFirst { GroupParser.matchesGroup(it, targetGroup) }
            if (targetCol == -1) continue
            targetBlockFound = true

            val end = if (bIdx + 1 < blocks.size) {
                indexOfNextHeaderStart(lines, from = block.endIdx) ?: lines.size
            } else {
                lines.size
            }

            lessons.addAll(
                parseCellRunsBetween(lines, block.endIdx, end, targetCol, block.groups.size, targetGroup, defaultDay, dateString),
            )
        }

        if (!targetBlockFound) {
            mutateStats { it.copy(groupFound = false) }
        }
        return lessons
    }

    private fun indexOfNextHeaderStart(lines: List<String>, from: Int): Int? {
        var i = from
        while (i < lines.size) {
            if (GroupParser.isValid(lines[i].trim())) {
                var j = i
                var count = 0
                while (j < lines.size && GroupParser.isValid(lines[j].trim()) && count < 12) {
                    count++
                    j++
                }
                if (count >= 2) return i
                i = j
                continue
            }
            i++
        }
        return null
    }

    private fun parseCellRunsBetween(
        lines: List<String>,
        from: Int,
        to: Int,
        targetCol: Int,
        groupCount: Int,
        targetGroup: String,
        dayOfWeek: Int,
        dateString: String,
    ): List<LessonEntity> {
        val result = mutableListOf<LessonEntity>()
        var i = from
        while (i < to) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val lessonNum = leadingLessonNumberOf(line)
            if (lessonNum == null) {
                i++
                continue
            }

            val subjectCells = mutableListOf<String>()
            var k = i
            while (k < to && leadingLessonNumberOf(lines[k]) == lessonNum) {
                subjectCells.add(lines[k])
                k++
            }

            val teacherCells = mutableListOf<String>()
            var m = k
            while (m < to && teacherCells.size < groupCount) {
                val t = lines[m]
                if (leadingLessonNumberOf(t) != null) break
                if (t.isNotBlank() && GroupParser.isValid(t.trim())) break
                if (t.length < 6) {
                    m++
                    continue
                }
                teacherCells.add(t)
                m++
            }

            if (subjectCells.size == groupCount) {
                val subj = subjectCells[targetCol].trim()
                val teach = teacherCells.getOrNull(targetCol)?.trim() ?: ""
                val lesson = parseTableCell(subj, teach, targetGroup, dayOfWeek, dateString)
                if (lesson != null && result.none { it.lessonNumber == lesson.lessonNumber }) {
                    result.add(lesson)
                }
            }

            i = if (m > k) m else k
        }
        return result
    }

    private fun leadingLessonNumberOf(line: String): Int? {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty()) return null
        val match = LEADING_LESSON_REGEX.find(trimmed) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun parseTableGridSchedule(
        lines: List<String>,
        targetGroup: String,
        defaultDay: Int,
        dateString: String,
    ): List<LessonEntity> {
        val lessons = mutableListOf<LessonEntity>()
        var currentDay = defaultDay

        var currentBlockGroups = listOf<String>()
        var targetColIndex = -1

        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            val line = rawLine.trim()

            if (line.isBlank()) {
                i++
                continue
            }

            val lowerLine = line.lowercase(Locale.ROOT)
            for ((dayName, dayNum) in DAY_KEYWORDS) {
                if (lowerLine.contains(dayName)) {
                    currentDay = dayNum
                    break
                }
            }

            val delimiter = when {
                line.contains("│") -> "│"
                line.contains("|") -> "|"
                line.contains("\t") -> "\t"
                else -> null
            }

            if (delimiter != null) {
                val cells = line.split(delimiter)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                val validGroupCount = cells.count { GroupParser.isValid(it) }
                if (validGroupCount >= 2 || (validGroupCount >= 1 && cells.size in 1..8 && cells.any { GroupParser.matchesGroup(it, targetGroup) })) {
                    currentBlockGroups = cells
                    targetColIndex = -1
                    for ((cIdx, grp) in cells.withIndex()) {
                        if (GroupParser.matchesGroup(grp, targetGroup)) {
                            targetColIndex = cIdx
                            break
                        }
                    }
                    i++
                    continue
                }

                if (targetColIndex != -1 && currentBlockGroups.isNotEmpty()) {
                    val isBorder = line.all { it in "┌┬┐├┼┤┴└┘─|-=+\t " }
                    if (isBorder) {
                        i++
                        continue
                    }

                    val rowCells = splitRowPreservingColumns(line, delimiter)

                    val targetCell1 = rowCells.getOrNull(targetColIndex)?.trim() ?: ""
                    val hasLessonNumber = targetCell1.isNotEmpty() && targetCell1[0].isDigit() && targetCell1[0].digitToInt() in 1..9

                    val anyColHasLesson = rowCells.any { c ->
                        val t = c.trim()
                        t.isNotEmpty() && t[0].isDigit() && t[0].digitToInt() in 1..9
                    }

                    if (hasLessonNumber || anyColHasLesson) {
                        val row1Cells = rowCells

                        var row2Cells = listOf<String>()
                        if (i + 1 < lines.size) {
                            val nextLine = lines[i + 1].trim()
                            val nextDelimiter = when {
                                nextLine.contains("│") -> "│"
                                nextLine.contains("|") -> "|"
                                nextLine.contains("\t") -> "\t"
                                else -> null
                            }
                            val isNextBorder = nextLine.all { it in "┌┬┐├┼┤┴└┘─|-=+\t " }
                            val isNextGroupHeader = nextDelimiter != null && nextLine.split(nextDelimiter).map { it.trim() }.count { GroupParser.isValid(it) } >= 2
                            val nextFirstColDigit = nextLine.split(nextDelimiter ?: "│").map { it.trim() }.any { it.isNotEmpty() && it[0].isDigit() && it[0].digitToInt() in 1..9 }

                            if (!isNextBorder && !isNextGroupHeader && !nextFirstColDigit && nextDelimiter != null) {
                                row2Cells = splitRowPreservingColumns(nextLine, nextDelimiter)
                                i++
                            }
                        }

                        val cell1 = row1Cells.getOrNull(targetColIndex)?.trim() ?: ""
                        val cell2 = row2Cells.getOrNull(targetColIndex)?.trim() ?: ""

                        if (cell1.isNotBlank()) {
                            val parsedLesson = parseTableCell(
                                cellSubject = cell1,
                                cellTeacher = cell2,
                                groupName = targetGroup,
                                dayOfWeek = currentDay,
                                dateString = dateString,
                            )
                            if (parsedLesson != null && lessons.none { it.dayOfWeek == parsedLesson.dayOfWeek && it.lessonNumber == parsedLesson.lessonNumber }) {
                                lessons.add(parsedLesson)
                            }
                        }
                    }
                }
            }

            i++
        }

        return lessons
    }

    private fun splitRowPreservingColumns(line: String, delimiter: String): List<String> {
        val trimmed = line.trim()
        val withoutOuter = if (trimmed.startsWith(delimiter) && trimmed.endsWith(delimiter) && trimmed.length > 1) {
            trimmed.substring(1, trimmed.length - 1)
        } else if (trimmed.startsWith(delimiter)) {
            trimmed.substring(1)
        } else if (trimmed.endsWith(delimiter)) {
            trimmed.substring(0, trimmed.length - 1)
        } else {
            trimmed
        }

        return withoutOuter.split(delimiter)
    }

    fun parseTableCell(
        cellSubject: String,
        cellTeacher: String,
        groupName: String,
        dayOfWeek: Int,
        dateString: String,
    ): LessonEntity? {
        val cleanSubj = cellSubject.trim()
        if (cleanSubj.isBlank() || cleanSubj == "-" || cleanSubj == "—") return null

        val lessonNumMatch = CELL_LESSON_HEAD_REGEX.find(cleanSubj) ?: return null
        val lessonNumber = lessonNumMatch.groupValues[1].toInt()
        val rawRest = lessonNumMatch.groupValues[2].trim()

        if (rawRest.isBlank() || rawRest.all { it == '-' || it == '—' || it == ' ' }) {
            return null
        }

        val (timeStart, timeEnd) = CollegeBellSchedule.getTimeForLessonNumber(lessonNumber, dayOfWeek)

        val doubleRoomsMatch = DOUBLE_ROOMS_TAIL_REGEX.find(rawRest)

        val isExplicitSplit = doubleRoomsMatch != null ||
            rawRest.contains("/") ||
            cellTeacher.contains("/") ||
            cleanSubj.contains("п/г", ignoreCase = true) ||
            cleanSubj.contains("подгруппа", ignoreCase = true)

        if (isExplicitSplit) {
            var room1 = ""
            var room2 = ""
            var subjPart1 = ""
            var subjPart2 = ""

            if (doubleRoomsMatch != null) {
                room1 = doubleRoomsMatch.groupValues[1].trim('-')
                room2 = doubleRoomsMatch.groupValues[2].trim('-')
                val subjectSection = rawRest.substring(0, doubleRoomsMatch.range.first).trim()
                if (subjectSection.contains("/")) {
                    val subjs = subjectSection.split("/")
                    subjPart1 = subjs[0].trim()
                    subjPart2 = subjs.getOrElse(1) { "" }.trim()
                } else {
                    subjPart1 = subjectSection
                    subjPart2 = subjectSection
                }
            } else if (rawRest.contains("/")) {
                val parts = rawRest.split("/")
                subjPart1 = parts[0].trim()
                subjPart2 = parts.getOrElse(1) { "" }.trim()

                val r1Match = ROOM_TAIL_REGEX.find(subjPart1)
                if (r1Match != null) {
                    room1 = r1Match.groupValues[1]
                    subjPart1 = subjPart1.substring(0, r1Match.range.first).trim()
                }
                val r2Match = ROOM_TAIL_REGEX.find(subjPart2)
                if (r2Match != null) {
                    room2 = r2Match.groupValues[1]
                    subjPart2 = subjPart2.substring(0, r2Match.range.first).trim()
                }
            } else {
                val (r, _, s) = extractDetails(rawRest)
                room1 = r
                subjPart1 = s
                subjPart2 = s
            }

            var teacher1 = ""
            var teacher2 = ""
            if (cellTeacher.contains("/")) {
                val teachers = cellTeacher.split("/")
                teacher1 = cleanTeacherName(teachers[0])
                teacher2 = cleanTeacherName(teachers.getOrElse(1) { "" })
            } else {
                teacher1 = cleanTeacherName(cellTeacher)
            }

            val normSubj1 = SubjectFormatter.normalize(subjPart1.ifBlank { rawRest })
            val normSubj2 = if (subjPart2.isNotBlank() && subjPart2 != subjPart1 && !subjPart2.contains("---")) {
                SubjectFormatter.normalize(subjPart2)
            } else {
                normSubj1
            }

            val finalSubjectName = if (normSubj1 != normSubj2 && normSubj2.isNotBlank()) {
                "$normSubj1 / $normSubj2"
            } else {
                normSubj1
            }

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = finalSubjectName,
                roomFirst = room1,
                teacherFirst = teacher1,
                roomSecond = room2,
                teacherSecond = teacher2,
                isSplit = true,
                dateString = dateString,
            )
        } else {
            var room = ""
            var subject = rawRest
            val singleRoomMatch = ROOM_TAIL_REGEX.find(rawRest)
            if (singleRoomMatch != null) {
                room = singleRoomMatch.groupValues[1]
                subject = rawRest.substring(0, singleRoomMatch.range.first).trim()
            } else {
                val (r, _, s) = extractDetails(rawRest)
                room = r
                subject = s
            }

            val finalTeacher = cleanTeacherName(cellTeacher)
            val normSubject = SubjectFormatter.normalize(subject.ifBlank { rawRest })

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = normSubject,
                roomFirst = room,
                teacherFirst = finalTeacher,
                roomSecond = "",
                teacherSecond = "",
                isSplit = false,
                dateString = dateString,
            )
        }
    }

    fun cleanTeacherName(raw: String): String {
        var t = raw.replace(DIGITS_REGEX, "").trim()
        t = t.replace(WS_REGEX, " ")
        t = t.trim(',', '-', '/', '\\', ' ')
        if (t.isBlank()) return ""

        val match = TEACHER_FIO_REGEX.find(t)
        if (match != null) {
            val surname = match.groupValues[1]
            val init1 = match.groupValues[2]
            val init2 = match.groupValues[3]
            return if (init2.isNotEmpty()) {
                "$surname $init1.$init2."
            } else {
                "$surname $init1."
            }
        }

        return t
    }

    private fun parseLineByLineFallback(
        lines: List<String>,
        targetGroup: String,
        defaultDay: Int,
        dateString: String,
    ): List<LessonEntity> {
        val lessons = mutableListOf<LessonEntity>()
        var currentDay = defaultDay
        var isCurrentGroupActive = false

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            for ((dayName, dayNum) in DAY_KEYWORDS) {
                if (lower.contains(dayName)) {
                    currentDay = dayNum
                    break
                }
            }

            val groupHeaderMatch = GROUP_LINE_REGEX.find(line)
            if (groupHeaderMatch != null) {
                val matchedGroup = groupHeaderMatch.value
                if (GroupParser.isValid(matchedGroup.replace("группа", "", ignoreCase = true).trim())) {
                    isCurrentGroupActive = GroupParser.matchesGroup(matchedGroup, targetGroup)
                }
            }

            val isPureHeader = line.matches(PURE_HEADER_REGEX)
            val isLineForGroup = !isPureHeader && (GroupParser.matchesGroup(line, targetGroup) || isCurrentGroupActive)

            if (isLineForGroup) {
                val numMatch = LESSON_NUMBER_IN_TEXT.find(line)
                val lessonNum = numMatch?.groupValues?.get(1)?.ifEmpty {
                    numMatch.groupValues.getOrNull(2)?.ifEmpty { numMatch.groupValues.getOrNull(3) }
                }?.toIntOrNull()
                    ?: if (isCurrentGroupActive) (lessons.size + 1).coerceAtMost(10) else 1

                val lesson = createLessonEntity(
                    groupName = targetGroup,
                    dayOfWeek = currentDay,
                    lessonNumber = lessonNum,
                    rawContent = line,
                    dateString = dateString,
                )
                if (lesson != null && lessons.none { it.dayOfWeek == currentDay && it.lessonNumber == lessonNum }) {
                    lessons.add(lesson)
                }
            }
        }

        return lessons
    }

    fun createLessonEntity(
        groupName: String,
        dayOfWeek: Int,
        lessonNumber: Int,
        rawContent: String,
        dateString: String = "",
    ): LessonEntity? {
        val trimmed = rawContent.trim()
        if (trimmed.isBlank() || trimmed == "-" || trimmed == "—") return null

        val (timeStart, timeEnd) = CollegeBellSchedule.getTimeForLessonNumber(lessonNumber, dayOfWeek)

        val doubleRooms = extractDoubleRooms(trimmed)
        val isExplicitSplit = trimmed.contains("1 п/г", ignoreCase = true) ||
            trimmed.contains("1п/г", ignoreCase = true) ||
            trimmed.contains("1 пг", ignoreCase = true) ||
            trimmed.contains("1 подгруппа", ignoreCase = true) ||
            doubleRooms != null ||
            (trimmed.contains("/") && trimmed.length > 15)

        if (isExplicitSplit) {
            val parts = splitSubgroupContent(trimmed)
            val firstPart = parts.first
            val secondPart = parts.second

            val (room1, teacher1, subject1) = extractDetails(firstPart)
            val (room2, teacher2, subject2) = extractDetails(secondPart)

            val finalRoom1 = when {
                doubleRooms != null -> doubleRooms.first
                room1.isNotBlank() -> room1
                else -> ""
            }
            val finalRoom2 = when {
                doubleRooms != null -> doubleRooms.second
                room2.isNotBlank() && room2 != room1 -> room2
                else -> ""
            }

            val normSubj1 = SubjectFormatter.normalize(subject1.ifBlank { subject2.ifBlank { trimmed } })
            val normSubj2 =
                if (subject2.isNotBlank() && subject2 != subject1 && !subject2.contains("---")) {
                    SubjectFormatter.normalize(subject2)
                } else {
                    normSubj1
                }
            val subjectRaw =
                if (normSubj2.isNotBlank() && !normSubj2.equals(normSubj1, ignoreCase = true)) {
                    "$normSubj1 / $normSubj2"
                } else {
                    normSubj1
                }

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = subjectRaw,
                roomFirst = finalRoom1,
                teacherFirst = teacher1,
                roomSecond = finalRoom2,
                teacherSecond = teacher2,
                isSplit = true,
                dateString = dateString,
            )
        } else {
            val (room, teacher, subject) = extractDetails(trimmed)
            val normalizedSubject = SubjectFormatter.normalize(subject.ifBlank { trimmed })

            return LessonEntity(
                groupName = GroupParser.cleanRawGroupName(groupName),
                dayOfWeek = dayOfWeek,
                lessonNumber = lessonNumber,
                timeStart = timeStart,
                timeEnd = timeEnd,
                subjectRaw = normalizedSubject,
                roomFirst = room,
                teacherFirst = teacher,
                roomSecond = "",
                teacherSecond = "",
                isSplit = false,
                dateString = dateString,
            )
        }
    }

    private fun extractDoubleRooms(text: String): Pair<String, String>? {
        val match = KAB_PAIR_REGEX.find(text)
        if (match != null) {
            return Pair(match.groupValues[1], match.groupValues[2])
        }

        val slashMatch = SLASH_ROOMS_REGEX.find(text)
        if (slashMatch != null) {
            return Pair(slashMatch.groupValues[1], slashMatch.groupValues[2])
        }

        return null
    }

    private fun extractDetails(text: String): Triple<String, String, String> {
        var room = ""
        var teacher = ""
        var subject = text

        val roomMatch = ROOM_ANY_REGEX.find(text)
        if (roomMatch != null) {
            room = roomMatch.groupValues[1].ifEmpty { roomMatch.groupValues[2] }
            subject = subject.replace(roomMatch.value, " ")
        } else {
            val endDigits = ROOM_END_DIGITS_REGEX.find(text.trim())
            if (endDigits != null) {
                room = endDigits.groupValues[1]
                subject = subject.substring(0, endDigits.range.first).trim()
            }
        }

        val teacherMatch = TEACHER_IN_SUBJECT_REGEX.find(subject)
        if (teacherMatch != null) {
            teacher = "${teacherMatch.groupValues[1]} ${teacherMatch.groupValues[2].replace(" ", "")}"
            subject = subject.replace(teacherMatch.value, " ")
        }

        subject = subject
            .replace(LESSON_PREFIX_STRIP_REGEX, "")
            .replace(SUBGROUP_STRIP_REGEX, "")
            .replace(WS_REGEX, " ")
            .trim(',', '.', '-', ' ', '/', '\\', ':')

        return Triple(room, teacher, subject)
    }

    private fun splitSubgroupContent(raw: String): Pair<String, String> {
        val p2Match = P2_SPLIT_REGEX.find(raw)
        if (p2Match != null) {
            val part1 = raw.substring(0, p2Match.range.first).trim()
            val part2 = raw.substring(p2Match.range.first).trim()
            if (part1.isNotBlank()) {
                return Pair(part1, part2)
            }
        }

        if (raw.contains("/")) {
            val parts = raw.split("/")
            if (parts.size >= 2 && parts[0].isNotBlank()) {
                return Pair(parts[0].trim(), parts.drop(1).joinToString("/").trim())
            }
        }

        return Pair(raw, raw)
    }

    private fun resolveAbsoluteUrl(baseUrl: String, relativeUrl: String): String = try {
        val base = java.net.URI(baseUrl)
        base.resolve(relativeUrl).toString()
    } catch (_: Exception) {
        if (relativeUrl.startsWith("http")) relativeUrl else "$baseUrl$relativeUrl"
    }
}
