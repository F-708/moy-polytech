package com.politex.data.network

import com.politex.util.BinaryTextExtract
import com.politex.util.Docx
import com.politex.util.Dow

data class TeacherSlot(
    val lessonNumber: Int,
    val groups: List<String>,
    val subject: String,
    val room: String,

    val dayOfWeek: Int = 0,
)

object TeacherScheduleParser {
    private val GROUP_REGEX = Regex("^([1-4])([1-9])([\\u0410-\\u042F])$")

    private val DATE_DOT_REGEX = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")

    private const val HEADER_SCAN_LINES = 6

    private val ROOM_REGEX = Regex("^([0-9]{1,3}[\\u0430-\\u044Fa-zA-Z]?|СТД)$")

    private val WEEK_HEADER_REGEX = Regex("^\\d+\\s*НЕДЕЛЯ", RegexOption.IGNORE_CASE)

    private const val MAX_LESSON = 12

    private const val OFFSITE_MARKER = "Вые"

    private fun isLessonRuler(cells: List<String>): Boolean {
        val start = cells.indexOfFirst { it.isNotBlank() }
        if (start < 0 || cells[start] != "1") return false
        return cells.withIndex().all { (i, v) -> i < start || v.isBlank() || v == (i - start + 1).toString() }
    }

    fun dowFromHeaderText(text: String): Int? = Dow.ofWord(text)

    fun findHeaderLine(text: String): String? {
        val lines = text.lines()
        lines.firstOrNull { it.contains("НЕДЕЛЯ", ignoreCase = true) }?.let { return it }
        val head = lines.take(HEADER_SCAN_LINES)
        head.firstOrNull { Dow.ofWord(it) != null }?.let { return it }
        return head.firstOrNull { DATE_DOT_REGEX.containsMatchIn(it) }
    }

    fun dateFromHeaderText(text: String): String? = DATE_DOT_REGEX.find(text)?.value

    fun matchesRequestedDate(
        text: String,
        requested: java.util.Calendar,
    ): Boolean {
        val header = findHeaderLine(text) ?: return false
        val inDocDate = dateFromHeaderText(header)

        if (inDocDate != null) {
            val inDocCal = MpkScheduleParser.parseDotDate(inDocDate) ?: return false
            return inDocCal.get(java.util.Calendar.YEAR) == requested.get(java.util.Calendar.YEAR) &&
                inDocCal.get(java.util.Calendar.DAY_OF_YEAR) == requested.get(java.util.Calendar.DAY_OF_YEAR)
        }

        val dow = dowFromHeaderText(header) ?: return false
        return dow == MpkScheduleParser.dowOfCalendar(requested)
    }

    fun parse(bytes: ByteArray): Map<String, List<TeacherSlot>> {
        if (bytes.isEmpty()) return emptyMap()

        if (Docx.isZip(bytes)) {
            parseDocx(bytes)?.let { if (it.isNotEmpty()) return it }
        }

        val utf16 = extractUtf16(bytes)
        if (utf16.isNotBlank()) {
            val parsed = parsePlainText(utf16)
            if (parsed.isNotEmpty()) return parsed
        }
        val cp1251 = extractCp1251(bytes)
        return parsePlainText(cp1251)
    }

    fun parsePlainText(text: String): Map<String, List<TeacherSlot>> {
        if (text.isBlank()) return emptyMap()

        data class Cell(
            val groups: MutableList<String> = mutableListOf(),
            val rooms: MutableList<String> = mutableListOf(),
            var subject: String = "",
        )

        val headerLine = findHeaderLine(text)
        val headerDow =
            headerLine?.let { line ->
                dowFromHeaderText(line)
                    ?: dateFromHeaderText(line)
                        ?.let { dot -> MpkScheduleParser.parseDotDate(dot) }
                        ?.let { cal -> MpkScheduleParser.dowOfCalendar(cal) }
            } ?: 0

        val result = mutableMapOf<String, MutableMap<Int, Cell>>()
        var currentTeacher: String? = null

        var headerSeen = headerLine == null

        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (!line.contains('║')) return@forEach

            if (line.contains('╫') || line.contains('╥')) return@forEach

            val parts = line.split('║')
            if (parts.size < 2) return@forEach

            if (!headerSeen) {
                if (line.contains("НЕДЕЛЯ", ignoreCase = true) ||
                    Dow.ofWord(line) != null ||
                    DATE_DOT_REGEX.containsMatchIn(line)
                ) {
                    headerSeen = true
                }
                return@forEach
            }

            val nameCell = parts[0].replace("│", "").trim()
            if (WEEK_HEADER_REGEX.containsMatchIn(nameCell)) return@forEach
            if (nameCell.isNotEmpty()) {
                currentTeacher = nameCell
            }
            val teacher = currentTeacher ?: return@forEach

            val cells = parts[1].split('│').map { it.trim() }
            if (isLessonRuler(cells)) return@forEach

            val teacherCells = result.getOrPut(teacher) { mutableMapOf() }

            cells.forEachIndexed { index, value ->
                val lessonNumber = index + 1
                if (lessonNumber > MAX_LESSON) return@forEachIndexed
                if (value.isBlank() || value == "—") return@forEachIndexed

                val cell = teacherCells.getOrPut(lessonNumber) { Cell() }
                when {
                    GROUP_REGEX.matches(value) -> cell.groups.add(value)
                    ROOM_REGEX.matches(value) -> cell.rooms.add(value)
                    value == OFFSITE_MARKER -> cell.rooms.add(value)
                    else -> cell.subject = value
                }
            }
        }

        return result
            .mapValues { (_, byLesson) ->
                byLesson.entries.sortedBy { it.key }.map { (lessonNumber, cell) ->
                    TeacherSlot(
                        lessonNumber = lessonNumber,
                        groups = cell.groups.distinct(),
                        subject = cell.subject,
                        room = cell.rooms.firstOrNull().orEmpty(),
                        dayOfWeek = headerDow,
                    )
                }
            }.filterValues { it.isNotEmpty() }
    }

    fun extractText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (Docx.isZip(bytes)) {
            parseDocxText(bytes)?.let { if (it.isNotBlank()) return it }
        }
        val utf16 = extractUtf16(bytes)
        if (utf16.isNotBlank()) return utf16
        return extractCp1251(bytes)
    }

    private fun parseDocxText(bytes: ByteArray): String? = try {
        Docx.readDocumentXml(bytes)?.let { xmlToPseudoTable(it) }
    } catch (_: Exception) {
        null
    }

    private fun extractUtf16(bytes: ByteArray): String = BinaryTextExtract.utf16LeRuns(bytes, bellAsTab = false).joinToString("\n")

    private fun extractCp1251(bytes: ByteArray): String = BinaryTextExtract
        .cp1251Runs(bytes, bellAsTab = false, minRunBytes = 6) {
            it.contains('║') || it.count { c -> c in 'А'..'я' } > 3
        }.joinToString("\n")

    private fun parseDocx(bytes: ByteArray): Map<String, List<TeacherSlot>>? = parseDocxText(bytes)?.let { parsePlainText(it) }

    internal fun xmlToPseudoTable(xml: String): String {
        val textRun = Regex("<w:t(?: [^>]*)?>([^<]*)</w:t>")
        val cellStart = Regex("<w:tc(?: [^>]*)?>")
        val sb = StringBuilder()
        Regex("<w:tr[ >]").findAll(xml).forEach { rowMatch ->
            val rowEnd = xml.indexOf("</w:tr>", rowMatch.range.first)
            if (rowEnd < 0) return@forEach
            val rowXml = xml.substring(rowMatch.range.first, rowEnd)
            sb.append('│')
            cellStart.split(rowXml).drop(1).forEachIndexed { cellIndex, cellXml ->
                val text = textRun.findAll(cellXml).map { it.groupValues[1] }.joinToString("")
                sb.append(text).append(if (cellIndex == 0) '║' else '│')
            }
            sb.append('\n')
        }
        return sb.toString()
    }
}
