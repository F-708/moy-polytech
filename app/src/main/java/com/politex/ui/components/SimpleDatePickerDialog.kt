package com.politex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorDividerLight
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.Tokens
import com.politex.ui.util.bouncyClickable
import java.util.Calendar
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SimpleDatePickerDialog(
    initialDateIso: String?,
    minYear: Int,
    maxYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {

    val today =
        com.politex.ui.util
            .rememberToday()

    val startYear =
        remember(initialDateIso, today) {
            initialDateIso?.split("-")?.getOrNull(0)?.toIntOrNull()
                ?: today.get(Calendar.YEAR)
        }
    val startMonth =
        remember(initialDateIso, today) {
            (
                initialDateIso
                    ?.split("-")
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?.minus(1)
                )
                ?: today.get(Calendar.MONTH)
        }

    var year by remember { mutableIntStateOf(startYear.coerceIn(minYear, maxYear)) }
    var month by remember { mutableIntStateOf(startMonth.coerceIn(0, 11)) }
    var selected by remember { mutableStateOf(initialDateIso) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AppCard(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArrowButton(
                        forward = false,
                        enabled = !(year <= minYear && month == 0),
                        onClick = {
                            if (month == 0) {
                                month = 11
                                year--
                            } else {
                                month--
                            }
                        },
                    )

                    Text(
                        text = MONTH_NAMES_NOMINATIVE[month] + " " + year,
                        style =
                        TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ColorTextTitle,
                        ),
                    )

                    ArrowButton(
                        forward = true,
                        enabled = !(year >= maxYear && month == 11),
                        onClick = {
                            if (month == 11) {
                                month = 0
                                year++
                            } else {
                                month++
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                MonthGrid(
                    year = year,
                    month = month,
                    selectedDateIso = selected,
                    onSelect = { selected = it },
                )

                Spacer(modifier = Modifier.height(12.dp))

                AppDivider()

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionButton(text = "ОТМЕНА", filled = false, onClick = onDismiss)
                    Spacer(modifier = Modifier.width(8.dp))

                    ActionButton(
                        text = "ГОТОВО",
                        filled = true,
                        enabled = selected != null,
                        onClick = { selected?.let(onConfirm) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrowButton(
    forward: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
        Modifier

            .size(Tokens.HitTarget)
            .bouncyClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (forward) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
            contentDescription = null,
            tint = if (enabled) ColorBrandBlue else ColorTextMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ActionButton(
    text: String,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = if (filled) ColorBrandFill else ColorBgMain,
        border = BorderStroke(1.dp, if (filled) ColorBrandFill else ColorBorderLight),
        modifier =
            Modifier
                .alpha(if (enabled) 1f else 0.4f)
                .bouncyClickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = text,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (filled) Color.White else ColorTextTitle,
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
