package com.politex.ui.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.AppGraph
import com.politex.data.repository.TeacherDayLesson
import com.politex.data.repository.TeacherDayLoader
import com.politex.data.repository.teacherTargetCalendar
import com.politex.ui.components.AppCard
import com.politex.ui.components.BackButton
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.util.DateFormats
import java.util.Calendar
import androidx.compose.ui.text.style.TextOverflow

object TeacherGroupLogic {
    data class GroupDay(
        val label: String,
        val dateString: String,
        val lessons: List<TeacherDayLesson>,
    )

    fun matchesGroup(
        groups: List<String>,
        target: String,
    ): Boolean {
        val t = target.trim().lowercase()
        if (t.isEmpty()) return false
        return groups.any { it.trim().lowercase() == t }
    }

    fun filterForGroup(
        lessons: List<TeacherDayLesson>,
        group: String,
    ): List<TeacherDayLesson> = lessons.filter { matchesGroup(it.groups, group) }

    fun dayLabel(
        offset: Int,
        cal: Calendar,
    ): String {
        val whenWord = if (offset == 0) "Сегодня" else "Завтра"
        return "$whenWord, ${DateFormats.dotDate(cal).dropLast(5)}"
    }
}

@Composable
fun TeacherGroupLessonsScreen(
    group: String,
    profile: TeacherProfile?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalTeacherMetrics.current
    val context = LocalContext.current
    var days by remember { mutableStateOf<List<TeacherGroupLogic.GroupDay>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(group, profile) {
        val p = profile
        if (p == null) {
            loading = false
            return@LaunchedEffect
        }
        val repo = AppGraph.scheduleRepository
        days =
            (0..1).map { offset ->
                val cal = teacherTargetCalendar(offset, null)
                val dateKey = DateFormats.dotDate(cal)
                val day = TeacherDayLoader.loadDay(context, repo, p, dateKey)
                TeacherGroupLogic.GroupDay(
                    label = TeacherGroupLogic.dayLabel(offset, cal),
                    dateString = dateKey,
                    lessons = TeacherGroupLogic.filterForGroup(day.lessons, group),
                )
            }
        loading = false
    }

    Column(modifier = modifier.fillMaxSize().background(ColorBgMain)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack = onBack, testTag = "group_back")
            Spacer(Modifier.width(10.dp))
            Text("Мои пары у «$group»", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ColorTextTitle), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        when {
            loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorBrandBlue, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }

            days.all { it.lessons.isEmpty() } ->
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "У этой группы ваших пар в ближайшие дни нет.",
                        style = TextStyle(fontSize = metrics.body, color = ColorTextMuted),
                    )
                }

            else ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    days.forEach { day ->
                        if (day.lessons.isEmpty()) return@forEach
                        Text(day.label, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = metrics.chipText, color = ColorTextMuted), modifier = Modifier.padding(top = 6.dp))
                        day.lessons.forEach { lesson ->
                            AppCard(modifier = Modifier.fillMaxWidth().testTag("group_lesson_${lesson.lessonNumber}")) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (lesson.start.isNotBlank()) {
                                            Text("${lesson.start}–${lesson.end}", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ColorTextTitle))
                                        }
                                        Text(lesson.subject.ifBlank { "Пара" }, style = TextStyle(fontSize = metrics.body, color = ColorTextBody))
                                        Text("${lesson.lessonNumber}-я пара · $group", style = TextStyle(fontSize = metrics.chipText, color = ColorTextMuted))
                                    }
                                    if (lesson.room.isNotBlank()) {
                                        Text(lesson.room, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, color = ColorBrandBlue))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
        }
    }
}
