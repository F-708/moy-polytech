package com.politex.data.repository

import android.content.Context
import com.politex.content.ContentAssets
import com.politex.util.Csv
import java.io.InputStream

data class Teacher(
    val name: String,
    val position: String = "",
    val subjects: String = "",
    val room: String = "",
    val phone: String = "",
    val email: String = "",
    val photo: String = "",
    val department: String = "",
    val experience: String = "",
    val category: String = "",
)

class TeachersRepository(
    private val context: Context,
) {

    fun loadTeachers(): List<Teacher> = cached ?: parseTeachers().takeIf { it.isNotEmpty() }?.also { cached = it } ?: emptyList()

    fun invalidateCache() {
        cached = null
    }

    private fun parseTeachers(): List<Teacher> = try {
        parseCsv(ContentAssets.open(context, "teachers.csv"))
    } catch (_: Exception) {
        emptyList()
    }

    private companion object {
        @Volatile
        private var cached: List<Teacher>? = null
    }

    private fun parseCsv(stream: InputStream): List<Teacher> = stream
        .bufferedReader(Charsets.UTF_8)
        .readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->

            val p = Csv.splitRow(line)
            Teacher(
                name = cell(p, 0),
                position = cell(p, 1),
                subjects = cell(p, 2),
                room = cell(p, 3),
                phone = cell(p, 4),
                email = cell(p, 5),
                photo = cell(p, 6),
                department = cell(p, 7),
                experience = cell(p, 8),
                category = cell(p, 9),
            )
        }.filter { it.name.isNotBlank() }
        .sortedBy { it.name }

    private fun cell(
        p: List<String>,
        i: Int,
    ): String {
        val v = p.getOrElse(i) { "" }.trim()
        return if (v.isEmpty() || v == "—" || v == "–" || v == "-") "" else v
    }

    private val prefs get() = context.getSharedPreferences("teachers_favorites", Context.MODE_PRIVATE)

    fun isFavorite(name: String): Boolean = prefs.getBoolean(key(name), false)

    fun toggleFavorite(name: String): Boolean {
        val newValue = !isFavorite(name)
        prefs.edit().putBoolean(key(name), newValue).apply()
        return newValue
    }

    fun favorites(): Set<String> = prefs.all.keys
        .filter { prefs.getBoolean(it, false) }
        .map { it.removePrefix("fav_") }
        .toSet()

    private fun key(name: String) = "fav_" + name.lowercase()
}
