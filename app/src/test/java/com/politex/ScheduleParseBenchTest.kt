package com.politex

import com.politex.data.network.MpkScheduleParser
import com.politex.data.network.TeacherScheduleParser
import org.junit.Test
import java.io.File

class ScheduleParseBenchTest {

    private fun fixture(name: String): ByteArray = javaClass.classLoader!!.getResourceAsStream(name)!!.readBytes()

    private fun catalog(): List<String> {
        val f = File("src/main/assets/group_codes.csv")
        if (f.exists()) {
            return f.readLines(Charsets.UTF_8).map { it.trim() }.filter { it.isNotBlank() }
        }
        return listOf(
            "11А", "11Г", "11Д", "11М", "11Н", "11О", "11П", "11С", "11Т", "11Э", "12Э",
            "21Г", "21Д", "21М", "21Н", "21О", "21П", "21Т", "21Э", "22Д", "22М", "22Н",
            "22О", "22Т", "22Э", "23Д", "23М", "23О", "23Э", "31Г", "31Д", "31М", "31Н",
            "31О", "31П", "31Т", "31Э", "32Д", "32М", "32Н", "32О", "32Т", "32Э", "33О",
            "33Э", "41А", "41Г", "41Н", "41О", "41П", "41С", "41Т", "41Э", "42Н", "42О",
            "42Т", "42Э", "43О", "43Э",
        )
    }

    private val report = StringBuilder()

    private fun bench(label: String, iterations: Int = 5, block: () -> Any?): Double {
        block()
        var best = Long.MAX_VALUE
        var total = 0L
        repeat(iterations) {
            val t0 = System.nanoTime()
            val r = block()
            val dt = System.nanoTime() - t0
            total += dt
            if (dt < best) best = dt
            if (it == iterations - 1) report.append("  last-result: ${describe(r)}\n")
        }
        val avg = total / iterations / 1_000_000.0
        report.append(String.format("  %-52s avg %8.1f ms   best %8.1f ms%n", label, avg, best / 1_000_000.0))
        return avg
    }

    private fun describe(r: Any?): String = when (r) {
        is Collection<*> -> "size=${r.size}"
        is String -> "len=${r.length}"
        is Boolean -> r.toString()
        null -> "null"
        else -> r.toString().take(60)
    }

    @Test
    fun `бенчмарк этапов парсинга`() {
        val student = fixture("fixtures/05-10-2026-raspisanie-uchashhihsya.doc")
        val teacher = fixture("fixtures/05.10.2026-raspisanie-prepodavatelej.doc")
        val groups = catalog()

        report.append("STUDENT doc bytes=${student.size}, catalog=${groups.size}\n")
        bench("extractDocTexts(student)") { MpkScheduleParser.extractDocTexts(student) }
        val texts = MpkScheduleParser.extractDocTexts(student)
        report.append("  utf16Text len=${texts.utf16Text.length} lines=${texts.utf16Text.lines().size}\n")
        bench("documentMatchesDate(student, 05.10.2026)") {
            MpkScheduleParser.documentMatchesDate(student, "05.10.2026")
        }
        bench("discoverDocumentGroups(texts)") { MpkScheduleParser.discoverDocumentGroups(texts) }
        bench("parseFile single group 41О") {
            MpkScheduleParser.parseFile(student, "41О", "05.10.2026")
        }
        bench("parseFileAllGroups (catalog=${groups.size})", iterations = 3) {
            MpkScheduleParser.parseFileAllGroups(student, "05.10.2026", groups)
        }
        bench("parseFileRawTexts per group (grid path)", iterations = 3) {
            groups.map { MpkScheduleParser.parseFileRawTexts(texts, it, "05.10.2026") }
        }
        bench("applyHeaderDate via parseFile", iterations = 3) {
            MpkScheduleParser.parseFile(student, "41О", "05.10.2026")
        }

        report.append("TEACHER doc bytes=${teacher.size}\n")
        bench("TeacherScheduleParser.extractText") { TeacherScheduleParser.extractText(teacher) }
        bench("TeacherScheduleParser.parse", iterations = 3) { TeacherScheduleParser.parse(teacher) }

        report.append("XML regex cost on docx-like input:\n")
        val bigText = texts.utf16Text
        bench("text.lines() split only") { bigText.lines() }

        val out = File("build/bench-parse.txt")
        out.parentFile.mkdirs()
        out.writeText(report.toString(), Charsets.UTF_8)
        println(report)
    }
}
