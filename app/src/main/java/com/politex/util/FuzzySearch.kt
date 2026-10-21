package com.politex.util

import kotlin.math.abs

object FuzzySearch {

    const val MIN_FUZZY_WORD_LENGTH = 4

    const val MAX_DISTANCE = 2

    fun normalize(
        raw: String,
        caseSensitive: Boolean = false,
    ): String {
        val lowered = if (caseSensitive) raw else raw.lowercase()
        return lowered.replace('ё', 'е').replace('Ё', 'Е')
    }

    fun wordsOf(
        text: String,
        caseSensitive: Boolean = false,
    ): Set<String> {
        val normalized = normalize(text, caseSensitive)
        val out = HashSet<String>(normalized.length / 6 + 8)
        var start = -1
        for (i in normalized.indices) {
            val c = normalized[i]
            if (c.isLetterOrDigit()) {
                if (start < 0) start = i
            } else if (start >= 0) {
                if (i - start >= 2) out.add(normalized.substring(start, i))
                start = -1
            }
        }
        if (start >= 0 && normalized.length - start >= 2) out.add(normalized.substring(start))
        return out
    }

    fun wordMatches(
        textWord: String,
        queryWord: String,
    ): Boolean {
        if (textWord == queryWord) return true
        if (commonPrefixLength(textWord, queryWord) >= MIN_FUZZY_WORD_LENGTH) return true
        if (queryWord.length < MIN_FUZZY_WORD_LENGTH) return false
        return damerauLevenshtein(textWord, queryWord, MAX_DISTANCE) <= MAX_DISTANCE
    }

    fun score(
        text: String,
        query: String,
        caseSensitive: Boolean = false,
    ): Int {
        val queryWords = wordsOf(query, caseSensitive)
        if (queryWords.isEmpty()) return 0
        val textWords = wordsOf(text, caseSensitive)
        if (textWords.isEmpty()) return 0
        var matched = 0
        for (q in queryWords) {
            if (textWords.any { wordMatches(it, q) }) matched++
        }
        return matched
    }

    fun queryWordCount(
        query: String,
        caseSensitive: Boolean = false,
    ): Int = wordsOf(query, caseSensitive).size

    fun commonPrefixLength(
        a: String,
        b: String,
    ): Int {
        val n = minOf(a.length, b.length)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        return i
    }

    fun damerauLevenshtein(
        a: String,
        b: String,
        limit: Int = Int.MAX_VALUE,
    ): Int {
        if (a == b) return 0
        if (abs(a.length - b.length) > limit) return limit + 1
        val n = a.length
        val m = b.length
        if (n == 0) return m
        if (m == 0) return n

        var twoBack = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)

        for (i in 1..n) {
            cur[0] = i
            var rowMin = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    v = minOf(v, twoBack[j - 2] + 1)
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit) return limit + 1
            val recycled = twoBack
            twoBack = prev
            prev = cur
            cur = recycled
        }
        return prev[m]
    }
}
