package com.politex.util

import com.politex.data.model.GroupInfo

object GroupParser {
    private val LATIN_TO_CYRILLIC: Map<Char, Char> =
        mapOf(
            'O' to 'О',
            'o' to 'О',
            'H' to 'Н',
            'h' to 'Н',
            'N' to 'Н',
            'n' to 'Н',
            'E' to 'Э',
            'e' to 'Э',
            'P' to 'П',
            'p' to 'П',
            'T' to 'Т',
            't' to 'Т',
            'G' to 'Г',
            'g' to 'Г',
            'D' to 'Д',
            'd' to 'Д',
            'A' to 'А',
            'a' to 'А',
            'C' to 'С',
            'c' to 'С',
            'S' to 'С',
            's' to 'С',
            'M' to 'М',
            'm' to 'М',
        )

    fun createGroupSearchRegex(targetGroup: String): Regex {
        val parsed = parse(targetGroup)
        val course = parsed?.course?.toString() ?: targetGroup.filter { it.isDigit() }.take(1)
        val number = parsed?.groupNumber?.toString() ?: targetGroup.filter { it.isDigit() }.drop(1).take(1)
        val letter = parsed?.specialtyCode ?: targetGroup.filter { it.isLetter() }.firstOrNull()?.let { normalizeSpecialtyChar(it) } ?: 'О'

        val latinVariants =
            when (letter) {
                'О' -> "[ОоOo]"
                'Н' -> "[НнHhNn]"
                'Э' -> "[ЭэEe]"
                'П' -> "[ПпPp]"
                'Т' -> "[ТтTt]"
                'Г' -> "[ГгGg]"
                'Д' -> "[ДдDd]"
                'А' -> "[АаAa]"
                'С' -> "[СсCcSs]"
                'М' -> "[МмMm]"
                else -> "[$letter]"
            }

        return Regex("(?i)(?:^|[^0-9\\u0400-\\u04FFa-z])$course\\s*$number\\s*[-_\\s]*$latinVariants(?:$|[^0-9\\u0400-\\u04FFa-z])")
    }

    fun matchesGroup(
        text: String,
        targetGroup: String,
    ): Boolean {

        val regex = createGroupSearchRegex(targetGroup)
        return regex.containsMatchIn(text)
    }

    fun parse(rawGroupName: String): GroupInfo? {
        val s = rawGroupName.trim()
        if (s.length < 3) return null

        val course = s[0] - '0'
        if (course !in 1..4) return null

        var i = 1
        while (i < s.length && s[i].isWhitespace()) i++

        if (i >= s.length) return null
        val groupNumber = s[i] - '0'
        if (groupNumber !in 1..9) return null

        i++
        while (i < s.length && (s[i] == '-' || s[i] == '_' || s[i].isWhitespace())) i++

        if (i != s.length - 1) return null
        val rawChar = s[i]
        if (!Character.isLetter(rawChar)) return null

        val normalizedChar = normalizeSpecialtyChar(rawChar)

        if (!MpkCurriculum.isValidSpecialtyCode(normalizedChar)) {
            return null
        }

        val specialty = MpkCurriculum.getSpecialty(normalizedChar)

        return GroupInfo(
            rawName = s,
            course = course,
            groupNumber = groupNumber,
            specialtyCode = normalizedChar,
            specialtyCipher = specialty?.cipher ?: "",
            specialtyFullName = specialty?.fullName ?: "",
        )
    }

    fun isValid(rawGroupName: String): Boolean = parse(rawGroupName) != null

    fun cleanRawGroupName(rawGroupName: String): String = parse(rawGroupName)?.canonicalName ?: rawGroupName.trim().replace("\\s+".toRegex(), "").uppercase()

    fun normalizeSpecialtyChar(char: Char): Char {
        val upper = char.uppercaseChar()
        return LATIN_TO_CYRILLIC[upper] ?: LATIN_TO_CYRILLIC[char] ?: upper
    }

    fun generateCommonGroups(): List<GroupInfo> {
        val groups = mutableListOf<GroupInfo>()
        val specialtyCodes = listOf('О', 'Н', 'Э', 'П', 'Т', 'Г', 'Д', 'А', 'С', 'М')

        for (course in 1..4) {
            for (code in specialtyCodes) {
                val specialty = MpkCurriculum.getSpecialty(code)

                val maxCourse = specialty?.subjectsByCourse?.keys?.maxOrNull() ?: 4
                if (course > maxCourse) continue
                groups.add(
                    GroupInfo(
                        rawName = "${course}1$code",
                        course = course,
                        groupNumber = 1,
                        specialtyCode = code,
                        specialtyCipher = specialty?.cipher ?: "",
                        specialtyFullName = specialty?.fullName ?: "",
                    ),
                )

                if (code in listOf('О', 'Э', 'Т', 'Д', 'С')) {
                    groups.add(
                        GroupInfo(
                            rawName = "${course}2$code",
                            course = course,
                            groupNumber = 2,
                            specialtyCode = code,
                            specialtyCipher = specialty?.cipher ?: "",
                            specialtyFullName = specialty?.fullName ?: "",
                        ),
                    )
                }
            }
        }
        return groups
    }
}
