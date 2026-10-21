package com.politex.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

enum class GradeCategory(
    val title: String,
) {
    REGULAR("Обычные"),
    PRACTICAL("Практические"),
    CONTROL("Контрольные"),
    LAB("Лабораторные"),
    EXAM("Экзамен"),
}

object GradeAggregator {

    const val PHYSICAL_EDUCATION_SUBJECT: String = "Физическая культура и здоровье"

    val DEFAULT_PRIORITY: List<String> =
        listOf(
            GradeCategory.REGULAR.name,
            GradeCategory.PRACTICAL.name,
            GradeCategory.CONTROL.name,
            GradeCategory.EXAM.name,
        )

    val DEFAULT_WEIGHTS: List<Int> = listOf(25, 25, 50, 100)

    fun isPhysicalEducation(subject: String): Boolean {
        val trimmed = subject.trim()
        if (trimmed.equals(PHYSICAL_EDUCATION_SUBJECT, ignoreCase = true)) return true
        return SubjectFormatter.normalize(trimmed).equals(PHYSICAL_EDUCATION_SUBJECT, ignoreCase = true)
    }

    fun effectiveWeightsEnabled(
        subject: String,
        configWeightsEnabled: Boolean,
    ): Boolean = !isPhysicalEducation(subject) && configWeightsEnabled

    fun subjectAverage(
        byCategory: Map<String, List<Int>>,
        weights: Map<String, Int>,
        weightsEnabled: Boolean,
    ): Double? =
        if (weightsEnabled) {
            weightedAverage(byCategory, weights)
        } else {
            plainAverage(byCategory)
        }

    fun average(
        byCategory: Map<String, List<Int>>,
        priority: List<String> = DEFAULT_PRIORITY,
    ): Double? {
        var acc: Double? = null
        for (name in priority) {
            if (name == GradeCategory.LAB.name) continue
            val values = byCategory[name].orEmpty()
            if (values.isEmpty()) continue
            val avg = values.average()
            acc = if (acc == null) avg else (acc + avg) / 2.0
        }
        return acc
    }

    fun weightedAverage(
        byCategory: Map<String, List<Int>>,
        weights: Map<String, Int>,
    ): Double? {
        var acc = 0.0
        var total = 0
        for ((name, values) in byCategory) {
            if (name == GradeCategory.LAB.name || values.isEmpty()) continue
            val w = weights[name] ?: 0
            if (w <= 0) continue
            acc += values.average() * w
            total += w
        }
        return if (total == 0) null else acc / total
    }

    fun plainAverage(byCategory: Map<String, List<Int>>): Double? {
        val all = byCategory.filterKeys { it != GradeCategory.LAB.name }.values.flatten()
        return if (all.isEmpty()) null else all.average()
    }

    fun roundAvg(
        v: Double,
        mode: String,
    ): Double {
        val scale =
            when (mode) {
                ".00" -> 2
                "целое" -> 0
                else -> 1
            }
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).toDouble()
    }

    fun snapWeight(fraction: Float): Int = (fraction * 4).roundToInt().coerceIn(0, 4) * 25

    fun buildTreeLevels(
        priority: List<String>,
        nonEmpty: Set<String> = priority.toSet(),
    ): List<List<String>> {
        val leaves = priority.filter { it != GradeCategory.LAB.name && it in nonEmpty }
        if (leaves.isEmpty()) return emptyList()
        val levels = ArrayList<List<String>>(leaves.size)
        var current: List<String> = leaves
        levels += current
        while (current.size > 1) {
            current = listOf(current[0] + "+" + current[1]) + current.drop(2)
            levels += current
        }
        return levels
    }

    fun round1(x: Double): Double = Math.round(x * 10) / 10.0

    fun format(
        x: Double?,
        mode: String = ".0",
    ): String =
        when {
            x == null -> "—"
            mode == ".00" -> String.format(Locale.ROOT, "%.2f", roundAvg(x, mode))
            mode == "целое" -> roundAvg(x, mode).toInt().toString()
            round1(x) % 1.0 == 0.0 -> round1(x).toInt().toString()
            else -> String.format(Locale.ROOT, "%.1f", round1(x))
        }
}
