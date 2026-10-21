package com.politex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTopBar
import java.util.Calendar
import java.util.Locale

@Composable
fun MonthGrid(
    year: Int,
    month: Int,
    selectedDateIso: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = remember(year, month) { calculateMonthDays(year, month) }

    Column(modifier = modifier.fillMaxWidth()) {

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС").forEach { day ->
                Text(
                    text = day,
                    textAlign = TextAlign.Center,
                    style =
                    TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextMuted,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(1.1f)

                            .let { m ->
                                if (day != null) {
                                    val dateIso = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                                    m.clickable { onSelect(dateIso) }
                                } else {
                                    m
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            val dateIso = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day)
                            val isSelected = selectedDateIso == dateIso
                            Box(
                                modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) ColorTopBar else androidx.compose.ui.graphics.Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$day",
                                    style =
                                    TextStyle(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (isSelected) androidx.compose.ui.graphics.Color.White else ColorTextBody,
                                    ),
                                )
                            }
                        }
                    }
                }

                repeat(7 - week.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1.1f))
                }
            }
        }
    }
}

fun calculateMonthDays(
    year: Int,
    month: Int,
): List<Int?> {
    val cal =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }

    val offset =
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

    return buildList {
        repeat(offset) { add(null) }
        for (d in 1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)) add(d)
    }
}

val MONTH_NAMES_NOMINATIVE =
    listOf(
        "Январь",
        "Февраль",
        "Март",
        "Апрель",
        "Май",
        "Июнь",
        "Июль",
        "Август",
        "Сентябрь",
        "Октябрь",
        "Ноябрь",
        "Декабрь",
    )
