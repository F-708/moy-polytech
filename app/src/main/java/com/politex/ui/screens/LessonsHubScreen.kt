package com.politex.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.data.model.GroupInfo
import com.politex.data.repository.TaskRepository
import com.politex.ui.screens.materials.ExternalMaterialIntake
import com.politex.ui.screens.materials.ViewerMode
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTopBar
import com.politex.ui.util.bouncyClickable
import kotlinx.coroutines.flow.collect

enum class LessonsSection(
    val title: String,
) {
    MATERIALS("Материалы"),
    TASKS("Задания"),
    GRADES("Оценки"),
}

internal fun targetHubSection(
    current: LessonsSection,
    hasExternalMaterial: Boolean,
): LessonsSection = if (hasExternalMaterial) LessonsSection.MATERIALS else current

@Composable
fun LessonsHubScreen(
    groupInfo: GroupInfo,
    taskRepository: TaskRepository,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {

    var userSection by rememberSaveable { mutableStateOf(LessonsSection.TASKS) }
    val hasExternalMaterial = ExternalMaterialIntake.hasWork
    val section = targetHubSection(userSection, hasExternalMaterial)

    LaunchedEffect(Unit) {
        snapshotFlow { ExternalMaterialIntake.pending to ExternalMaterialIntake.active }
            .collect { (pending, active) ->
                if (pending != null && active == null) ExternalMaterialIntake.claim()
            }
    }

    LaunchedEffect(hasExternalMaterial) {
        if (hasExternalMaterial && userSection != LessonsSection.MATERIALS) {
            userSection = LessonsSection.MATERIALS
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize()
            .background(ColorBgMain),
    ) {

        if (!ViewerMode.active && !GradeDetailMode.active) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LessonsSection.entries.forEach { s ->
                    val isSelected = section == s
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            border = BorderStroke(1.dp, if (isSelected) ColorTopBar else ColorBorderLight),
                            color = if (isSelected) ColorTopBar else ColorBgMain,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .bouncyClickable { userSection = s }
                                .testTag("hub_tab_${s.name.lowercase()}"),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = s.title,

                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style =
                                    androidx.compose.ui.text.TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color.White else ColorTextBody,
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            }
        }

        when (section) {
            LessonsSection.TASKS ->
                TasksScreen(
                    groupInfo = groupInfo,
                    taskRepository = taskRepository,
                    modifier = Modifier.fillMaxSize(),
                    snackbarHostState = snackbarHostState,
                )
            LessonsSection.MATERIALS ->
                MaterialBaseScreen(
                    groupInfo = groupInfo,
                    modifier = Modifier.fillMaxSize(),
                    onExternalMaterialConsumed = { userSection = LessonsSection.MATERIALS },
                )
            LessonsSection.GRADES ->
                GradesScreen(
                    groupInfo = groupInfo,
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}
