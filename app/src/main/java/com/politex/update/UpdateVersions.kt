package com.politex.update

internal object UpdateVersions {
    fun normalize(tag: String?): String? {
        val raw = tag?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val stripped = raw.removePrefix("v").removePrefix("V")
        val core = stripped.substringBefore('+').substringBefore('-')
        val parts = core.split('.')
        if (parts.isEmpty() || parts.size > 3) return null
        if (parts.any { part -> part.isEmpty() || part.length > 9 || part.any { !it.isDigit() } }) return null
        return stripped
    }

    fun isNewer(
        candidate: String?,
        current: String?,
    ): Boolean {
        val left = parse(candidate) ?: return false
        val right = parse(current) ?: return false
        val core = compareCore(left.numbers, right.numbers)
        if (core != 0) return core > 0
        if (left.preRelease.isEmpty() && right.preRelease.isEmpty()) return false
        if (left.preRelease.isEmpty()) return true
        if (right.preRelease.isEmpty()) return false
        return comparePreRelease(left.preRelease, right.preRelease) > 0
    }

    private data class Parsed(
        val numbers: List<Int>,
        val preRelease: List<String>,
    )

    private fun parse(value: String?): Parsed? {
        val normalized = normalize(value) ?: return null
        val withoutBuild = normalized.substringBefore('+')
        val core = withoutBuild.substringBefore('-')
        val preRelease =
            withoutBuild
                .substringAfter('-', missingDelimiterValue = "")
                .split('.')
                .filter { it.isNotEmpty() }
        val numbers = core.split('.').map { it.toIntOrNull() ?: return null }
        return Parsed(numbers = numbers, preRelease = preRelease)
    }

    private fun compareCore(
        left: List<Int>,
        right: List<Int>,
    ): Int {
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun comparePreRelease(
        left: List<String>,
        right: List<String>,
    ): Int {
        val size = minOf(left.size, right.size)
        for (index in 0 until size) {
            val a = left[index]
            val b = right[index]
            val aNumber = a.toIntOrNull()
            val bNumber = b.toIntOrNull()
            val result =
                when {
                    aNumber != null && bNumber != null -> aNumber.compareTo(bNumber)
                    aNumber != null -> -1
                    bNumber != null -> 1
                    else -> a.compareTo(b)
                }
            if (result != 0) return result
        }
        return left.size.compareTo(right.size)
    }
}
