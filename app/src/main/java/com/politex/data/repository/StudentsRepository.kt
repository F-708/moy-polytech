package com.politex.data.repository

import android.content.Context
import com.politex.util.Csv
import com.politex.util.GroupCatalog
import com.politex.util.MpkCurriculum

data class Student(
    val fullName: String,
    val group: String,
    val course: String,
    val specialtyCode: String,
    val specialty: String,
    val funding: String,
    val dormitory: String,
    val curator: String,
    val room: String,
    val courseMismatch: Boolean = false,
)

class StudentsRepository(
    private val context: Context,
) {

    fun loadStudents(): List<Student> = cached ?: try {
        parse(
            context.assets
                .open("students.csv")
                .bufferedReader(Charsets.UTF_8)
                .readLines(),
            knownGroups = GroupCatalog.load(context).all().toSet(),
        ).also { cached = it }
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        @Volatile
        private var cached: List<Student>? = null
    }

    internal fun parse(
        lines: List<String>,
        knownGroups: Set<String>,
    ): List<Student> {
        val header = lines.firstOrNull()?.let { Csv.splitRow(Csv.stripBom(it)) } ?: return emptyList()

        val idxName = header.indexOfFirst { it.startsWith("ФИО") }
        val idxGroup = header.indexOf("Группа")
        val idxCourse = header.indexOf("Курс")
        val idxCode = header.indexOf("Код специальности")
        val idxSpecialty = header.indexOf("Специальность")
        val idxFunding = header.indexOf("Основа обучения")
        val idxDorm = header.indexOf("Общежитие")
        val idxCurator = header.indexOfFirst { it.startsWith("Куратор") }
        val idxRoom = header.indexOfFirst { it.startsWith("Кабинет") }

        if (idxName < 0 || idxGroup < 0 || idxCourse < 0) return emptyList()

        return lines
            .drop(1)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line ->
                val p = Csv.splitRow(line)

                fun cell(i: Int) = p.getOrElse(i) { "" }.trim()

                val group = cell(idxGroup)
                val course = cell(idxCourse).filter { it.isDigit() }

                val letter = group.lastOrNull()
                val maxCourse =
                    letter
                        ?.let { MpkCurriculum.getSpecialty(it) }
                        ?.subjectsByCourse
                        ?.keys
                        ?.maxOrNull()
                Student(
                    fullName = cell(idxName),
                    group = group,
                    course = course,
                    specialtyCode = cell(idxCode),
                    specialty = cell(idxSpecialty),
                    funding = cell(idxFunding),
                    dormitory = cell(idxDorm),
                    curator = cell(idxCurator),
                    room = cell(idxRoom),

                    courseMismatch =
                    group !in knownGroups &&
                        maxCourse != null &&
                        (course.toIntOrNull() ?: 0) > maxCourse,
                )
            }.filter { it.fullName.isNotBlank() }
    }
}
