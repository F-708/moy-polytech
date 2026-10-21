package com.politex.data.model

data class GroupInfo(
    val rawName: String,
    val course: Int,
    val groupNumber: Int,
    val specialtyCode: Char,
    val specialtyCipher: String = "",
    val specialtyFullName: String = "",
) {
    val hasSaturdayClasses: Boolean
        get() = course == 1

    val canHaveCourseWork: Boolean
        get() = course in 2..4

    val canHaveDiploma: Boolean
        get() = course == 4

    val canonicalName: String
        get() = "${course}${groupNumber}${specialtyCode.uppercaseChar()}"
}
