package com.politex.ui.util

import com.politex.data.local.entity.LessonEntity
import com.politex.util.SubgroupPref
import com.politex.util.SubgroupSettings

object RouteStartResolver {

    sealed interface RouteStart {
        data class From(val room: String) : RouteStart

        data class Ask(val roomFirst: String, val roomSecond: String) : RouteStart
    }

    fun previousLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
    ): LessonEntity? = lessons
        .filter {
            it.lessonNumber in 1 until lesson.lessonNumber &&
                (it.roomFirst.isNotBlank() || it.roomSecond.isNotBlank())
        }.maxByOrNull { it.lessonNumber }

    fun startFromPrevious(
        prev: LessonEntity?,
        pref: SubgroupPref,
    ): RouteStart = when {
        prev == null -> RouteStart.From("")
        prev.isSplit && prev.roomFirst.isNotBlank() && prev.roomSecond.isNotBlank() ->
            SubgroupSettings
                .splitIndex(pref)
                ?.let { RouteStart.From((if (it == 0) prev.roomFirst else prev.roomSecond).trim()) }
                ?: RouteStart.Ask(prev.roomFirst.trim(), prev.roomSecond.trim())
        else -> RouteStart.From(prev.roomFirst.ifBlank { prev.roomSecond }.trim())
    }

    fun startForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
        pref: SubgroupPref,
    ): RouteStart = startFromPrevious(previousLesson(lessons, lesson), pref)

    fun startForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
    ): String = startRoomForLesson(lessons, lesson, SubgroupPref.NEUTRAL)

    fun startRoomForLesson(
        lessons: List<LessonEntity>,
        lesson: LessonEntity,
        pref: SubgroupPref,
    ): String = when (val s = startForLesson(lessons, lesson, pref)) {
        is RouteStart.Ask -> s.roomFirst
        is RouteStart.From -> s.room
    }
}
