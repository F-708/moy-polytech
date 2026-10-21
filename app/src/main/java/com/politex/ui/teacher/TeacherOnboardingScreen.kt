package com.politex.ui.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.repository.Teacher
import com.politex.ui.components.BrandActionButton
import com.politex.ui.components.CenteredLoader
import com.politex.ui.components.EmptyState
import com.politex.ui.components.SearchBar
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.TextStylePageTitle
import com.politex.ui.util.bouncyClickable
import com.politex.ui.util.rememberTeachers

@Composable
@Suppress("UNUSED_PARAMETER")
fun TeacherOnboardingScreen(
    onProfileSaved: () -> Unit,
    onSkipAll: () -> Unit,
) {
    val context = LocalContext.current
    val teachers = rememberTeachers()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(teachers, query) { TeacherOnboardingLogic.teacherSearch(teachers, query) }

    TeacherTheme {
        Column(modifier = Modifier.fillMaxSize().background(ColorBgMain)) {
            Text(
                text = "Выберите себя",
                style = TextStylePageTitle,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
            )
            SearchBar(
                value = query,
                onValueChange = { query = it },
                placeholder = "Фамилия, имя или предмет",
                testTag = "teacher_onboarding_search",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            when {
                teachers.isEmpty() -> CenteredLoader()

                results.isEmpty() ->
                    EmptyState(
                        title = "Никого не нашли по «${query.trim()}»",
                        hint = "Попробуйте фамилию",
                        modifier = Modifier.fillMaxSize(),
                        action = {
                            BrandActionButton(
                                icon = Icons.Default.Close,
                                label = "Сбросить",
                                testTag = "teacher_onboarding_reset",
                            ) { query = "" }
                        },
                    )

                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(results) { t ->
                            TeacherPickCard(teacher = t) {
                                TeacherProfileStore.set(context, TeacherOnboardingLogic.profileOf(t))
                                onProfileSaved()
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun TeacherPickCard(
    teacher: Teacher,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBorderLight),
        modifier = Modifier.fillMaxWidth().bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = teacher.name,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorTextTitle),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details =
                    listOf(teacher.position, teacher.subjects)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = TextStyle(fontSize = 12.sp, color = ColorTextMuted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = ColorTextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
