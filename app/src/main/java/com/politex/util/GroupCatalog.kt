package com.politex.util

import android.content.Context
import com.politex.content.ContentAssets

class GroupCatalog(
    private val codes: List<String>,
) {

    fun all(): List<String> = codes

    fun find(input: String): String? {
        val normalized = normalize(input) ?: return null
        return codes.firstOrNull { it == normalized }
    }

    companion object {
        private const val ASSET = "group_codes.csv"

        fun load(context: Context): GroupCatalog = try {
            ContentAssets
                .open(context, ASSET)
                .bufferedReader(Charsets.UTF_8)
                .readLines()
                .let(::parse)
        } catch (_: Exception) {
            GroupCatalog(emptyList())
        }

        fun parse(lines: List<String>): GroupCatalog = GroupCatalog(
            lines
                .map { it.trim() }
                .filter { it.isNotBlank() },
        )

        fun normalize(input: String): String? {
            val s =
                input
                    .trim()
                    .replace(" ", "")
                    .replace("-", "")
                    .replace("_", "")
            if (s.length < 3) return null
            val course = s[0] - '0'
            val number = s[1] - '0'
            if (course !in 1..4 || number !in 1..9) return null
            val letter = GroupParser.normalizeSpecialtyChar(s[2])
            if (!letter.isLetter()) return null
            return "$course$number$letter"
        }
    }
}
