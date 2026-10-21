package com.politex.update

private const val STABLE_BUILD_SLOT = 999
private const val MAX_PREVIEW_BUILD = 998
private const val MAX_MAJOR = 213
private const val MAX_MINOR = 99
private const val MAX_PATCH = 99

fun versionCodeOf(tag: String?): Int? {
    val raw = tag?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val stripped = raw.removePrefix("v").removePrefix("V")
    val withoutBuild = stripped.substringBefore('+')
    if (withoutBuild.isEmpty()) return null
    val core = withoutBuild.substringBefore('-')
    val suffix = withoutBuild.substringAfter('-', "")
    val parts = core.split('.')
    if (parts.size > 3) return null
    val numbers = IntArray(3)
    for (index in parts.indices) {
        val part = parts[index]
        if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
        numbers[index] = part.toInt()
    }
    if (numbers[0] > MAX_MAJOR || numbers[1] > MAX_MINOR || numbers[2] > MAX_PATCH) return null
    val build =
        if (suffix.isEmpty()) {
            STABLE_BUILD_SLOT
        } else {
            val numeric =
                suffix
                    .split('.', '-')
                    .lastOrNull { segment -> segment.isNotEmpty() && segment.all { it.isDigit() } }
            (numeric?.toIntOrNull() ?: 0).coerceAtMost(MAX_PREVIEW_BUILD)
        }
    return numbers[0] * 10_000_000 + numbers[1] * 100_000 + numbers[2] * 1_000 + build
}

fun isPreviewTag(tag: String?): Boolean {
    val raw = tag?.trim().orEmpty()
    if (raw.isEmpty()) return false
    return raw.substringBefore('+').contains('-')
}
