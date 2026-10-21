package com.politex.ui.teacher

import com.politex.data.repository.Teacher
import com.politex.data.repository.TeacherInsights
import com.politex.util.FuzzySearch

object TeacherOnboardingLogic {

    private const val MIN_FUZZY_LEN = 5

    private const val MAX_DIST = 3

    private const val NAME_WEIGHT = 3

    private const val EXACT = 3
    private const val SUBSTRING = 2
    private const val FUZZY = 1

    fun teacherSearch(
        teachers: List<Teacher>,
        query: String,
    ): List<Teacher> {
        if (query.isBlank()) return teachers.sortedBy { FuzzySearch.normalize(it.name) }
        val words = FuzzySearch.wordsOf(query)
        if (words.isEmpty()) return emptyList()

        val entries = teachers.map { Entry(it) }
        val scored = entries.map { e ->
            var total = 0
            var matched = 0
            var nameScore = 0
            for (w in words) {
                val name = e.nameScore(w)
                val other = e.otherScore(w)
                val best = maxOf(name * NAME_WEIGHT, other)
                if (best > 0) matched++
                total += best
                nameScore += name
            }
            Score(e.teacher, total, matched, nameScore)
        }

        val strict = scored.filter { it.matched == words.size && it.total > 0 }

        val pool = if (strict.isNotEmpty()) strict else scored.filter { it.nameScore > 0 }
        return pool
            .sortedWith(compareByDescending<Score> { it.total }.thenBy { FuzzySearch.normalize(it.teacher.name) })
            .map { it.teacher }
    }

    fun profileOf(teacher: Teacher): TeacherProfile =
        TeacherProfile(name = teacher.name, surnameKey = TeacherInsights.surnameOf(teacher.name))

    fun showOnboarding(
        profile: TeacherProfile?,
        skipped: Boolean,
    ): Boolean = profile == null && !skipped

    private class Entry(teacher: Teacher) {
        val teacher = teacher
        private val name = FuzzySearch.normalize(teacher.name)
        private val nameWords = FuzzySearch.wordsOf(teacher.name)
        private val other = FuzzySearch.normalize(listOf(teacher.position, teacher.subjects).joinToString(" "))
        private val otherWords = FuzzySearch.wordsOf(listOf(teacher.position, teacher.subjects).joinToString(" "))

        fun nameScore(word: String) = score(name, nameWords, word)

        fun otherScore(word: String) = score(other, otherWords, word)
    }

    private fun score(
        text: String,
        textWords: Set<String>,
        word: String,
    ): Int {
        if (word in textWords) return EXACT
        if (text.contains(word)) return SUBSTRING
        if (word.length >= MIN_FUZZY_LEN &&
            textWords.any {
                it.length >= MIN_FUZZY_LEN && FuzzySearch.damerauLevenshtein(it, word, MAX_DIST) <= MAX_DIST
            }
        ) {
            return FUZZY
        }
        return 0
    }

    private class Score(
        val teacher: Teacher,
        val total: Int,
        val matched: Int,
        val nameScore: Int,
    )
}
