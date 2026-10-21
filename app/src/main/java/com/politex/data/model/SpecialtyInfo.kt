package com.politex.data.model

data class SpecialtyInfo(
    val code: Char,
    val cipher: String,
    val fullName: String,
    val shortName: String,
    val qualification: String = "",
    val workerProfessions: List<String> = emptyList(),
    val subjectsByCourse: Map<Int, List<String>>,
    val incompleteCourses: Set<Int> = emptySet(),
)
