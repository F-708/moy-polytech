package com.politex.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.politex.ui.theme.ColorBgMain
import com.politex.ui.theme.ColorBorderLight
import com.politex.ui.theme.ColorBrandBlue
import com.politex.ui.theme.ColorBrandFill
import com.politex.ui.theme.ColorTextBody
import com.politex.ui.theme.ColorTextMuted
import com.politex.ui.theme.ColorTextTitle
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.util.bouncyClickable
import com.politex.util.ExactAlarmGate
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId =
            intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val kind = WidgetKind.of(this, appWidgetId)

        val isReconfigure = WidgetOptions.isConfigured(this, appWidgetId)

        setContent {
            MyApplicationTheme {
                Surface(color = ColorBgMain, modifier = Modifier.fillMaxSize()) {
                    var style by remember { mutableStateOf(WidgetStyle.load(this@WidgetConfigActivity, appWidgetId)) }
                    var countdownMode by remember { mutableStateOf(CountdownMode.load(this@WidgetConfigActivity, appWidgetId)) }
                    var showRoom by remember { mutableStateOf(WidgetOptions.showRoom(this@WidgetConfigActivity, appWidgetId)) }
                    var showSeconds by remember { mutableStateOf(WidgetOptions.seconds(this@WidgetConfigActivity, appWidgetId)) }
                    var radius by remember { mutableStateOf(WidgetOptions.radius(this@WidgetConfigActivity, appWidgetId)) }

                    var canExactAlarms by remember { mutableStateOf(ExactAlarmGate.canScheduleExactAlarms(this@WidgetConfigActivity)) }
                    var alarmAsked by remember { mutableStateOf(false) }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                canExactAlarms = ExactAlarmGate.canScheduleExactAlarms(this@WidgetConfigActivity)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    fun requestAlarmAccessOnce() {
                        if (ExactAlarmGate.shouldRequest(
                                Build.VERSION.SDK_INT,
                                ExactAlarmGate.canScheduleExactAlarms(this@WidgetConfigActivity),
                                alarmAsked,
                            )
                        ) {
                            alarmAsked = true
                            ExactAlarmGate.openSettings(this@WidgetConfigActivity)
                        }
                    }

                    Column(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        Text(
                            text = kind.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextTitle,
                        )
                        Text(
                            text = kind.hint,
                            fontSize = 13.sp,
                            color = ColorTextMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        if (ExactAlarmGate.hintNeeded(Build.VERSION.SDK_INT, canExactAlarms)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ExactAlarmHintCard(
                                text = "Чтобы обновление работало корректно, нужен доступ к будильникам",
                                onAllow = { ExactAlarmGate.openSettings(this@WidgetConfigActivity) },
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        SectionTitle("Тема оформления")

                        WidgetStyle.entries.forEach { option ->
                            val isSelected = style == option

                            val colors = option.colors(this@WidgetConfigActivity)
                            OptionRow(
                                selected = isSelected,
                                title = option.title,
                                description = null,
                                onClick = { style = option },
                                leading = {
                                    Box(
                                        modifier =
                                        Modifier
                                            .size(32.dp)
                                            .background(Color(colors.backgroundColor), RoundedCornerShape(2.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "12",
                                            color = Color(colors.mainColor),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        SectionTitle("Скругление углов")
                        val previewColors = style.colors(this@WidgetConfigActivity)
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .height(64.dp)
                                .background(Color(previewColors.backgroundColor), RoundedCornerShape(radius.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "12 мин",
                                color = Color(previewColors.mainColor),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WidgetOptions.RADII_DP.forEach { r ->
                                val selected = radius == r
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier =
                                    Modifier
                                        .weight(1f)
                                        .bouncyClickable { radius = r },
                                ) {
                                    Box(
                                        modifier =
                                        Modifier
                                            .size(40.dp)
                                            .background(
                                                if (selected) ColorBrandBlue else ColorBorderLight,
                                                RoundedCornerShape(r.dp),
                                            ),
                                    )
                                    Text(
                                        text = if (r == 0) "0" else "$r",
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) ColorTextTitle else ColorTextMuted,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }

                        if (kind == WidgetKind.COUNTDOWN) {
                            Spacer(modifier = Modifier.height(18.dp))
                            SectionTitle("До чего считать")
                            val teacherModeOn = com.politex.util.TeacherModeStore.isTeacher(this@WidgetConfigActivity)
                            CountdownMode.entries.forEach { option ->
                                OptionRow(
                                    selected = countdownMode == option,
                                    title = option.title,
                                    description =
                                    if (teacherModeOn && option == CountdownMode.GROUP_LESSONS) {
                                        "Отсчёт заканчивается на последнем уроке в вашем расписании"
                                    } else {
                                        option.description
                                    },
                                    onClick = {
                                        countdownMode = option
                                        requestAlarmAccessOnce()
                                    },
                                )
                            }
                        }

                        if (kind.showRoomOption || kind == WidgetKind.COUNTDOWN) {
                            Spacer(modifier = Modifier.height(18.dp))
                            SectionTitle("Дополнительно")
                            if (kind.showRoomOption) {
                                Row(
                                    modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Показывать кабинет",
                                            fontSize = 14.sp,
                                            color = ColorTextBody,
                                        )
                                        Text(
                                            text = "Если не нужен — строки станут короче",
                                            fontSize = 12.sp,
                                            color = ColorTextMuted,
                                        )
                                    }
                                    Switch(checked = showRoom, onCheckedChange = { showRoom = it })
                                }
                            }
                            if (kind == WidgetKind.COUNTDOWN) {
                                SecondsOptionSection(
                                    showSeconds = showSeconds,
                                    onToggle = { showSeconds = it },
                                    alarmHintVisible =
                                    ExactAlarmGate.secondsHintNeeded(
                                        Build.VERSION.SDK_INT,
                                        canExactAlarms,
                                        showSeconds,
                                    ),
                                    onAllowAlarms = { ExactAlarmGate.openSettings(this@WidgetConfigActivity) },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = ColorBrandFill,
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .bouncyClickable {
                                    WidgetStyle.save(this@WidgetConfigActivity, appWidgetId, style)
                                    WidgetOptions.setShowRoom(this@WidgetConfigActivity, appWidgetId, showRoom)
                                    WidgetOptions.setRadius(this@WidgetConfigActivity, appWidgetId, radius)
                                    if (kind == WidgetKind.COUNTDOWN) {
                                        CountdownMode.save(this@WidgetConfigActivity, appWidgetId, countdownMode)
                                        WidgetOptions.setSeconds(this@WidgetConfigActivity, appWidgetId, showSeconds)
                                    }

                                    WidgetUpdateHelper.widgetScope.launch {
                                        WidgetUpdateHelper.updateOne(this@WidgetConfigActivity, appWidgetId)
                                    }
                                    WidgetAlarm.scheduleNext(this@WidgetConfigActivity)
                                    WidgetAlarm.scheduleNextSecond(this@WidgetConfigActivity)
                                    com.politex.worker.WidgetSyncWorker.enqueue(this@WidgetConfigActivity)
                                    runCatching { com.politex.util.BellTimerService.sync(this@WidgetConfigActivity) }

                                    requestAlarmAccessOnce()

                                    setResult(
                                        RESULT_OK,
                                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                                    )
                                    finish()
                                },
                        ) {
                            Box(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(

                                    text = if (isReconfigure) "СОХРАНИТЬ" else "ДОБАВИТЬ ВИДЖЕТ",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class WidgetKind(
    val title: String,
    val hint: String,
    val providerClass: Class<*>,
    val showRoomOption: Boolean,
) {
    COUNTDOWN(
        "Виджет «До звонка»",
        "Сколько осталось до звонка. Тема и до чего считать отсчёт",
        BellCountdownWidgetProvider::class.java,
        false,
    ),
    NOW_NEXT(
        "Виджет «Сейчас и дальше»",
        "Что идёт сейчас и что будет следующим",
        NowNextWidgetProvider::class.java,
        true,
    ),
    TODAY(
        "Виджет «Расписание на сегодня»",
        "Весь день списком: время, предмет, кабинет",
        TodayScheduleWidgetProvider::class.java,
        true,
    ),
    BELLS(
        "Виджет «Звонки»",
        "Расписание звонков с подсветкой текущего урока",
        BellsWidgetProvider::class.java,
        false,
    ),
    ;

    companion object {
        fun of(
            context: android.content.Context,
            appWidgetId: Int,
        ): WidgetKind {
            val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)
            val providerName = info?.provider?.className
            return entries.firstOrNull { it.providerClass.name == providerName } ?: COUNTDOWN
        }
    }
}

object WidgetOptions {
    private const val PREF_SHOW_ROOM = "widget_show_room_"
    private const val PREF_SECONDS = "widget_seconds_"

    private const val PREF_RADIUS = "widget_radius_"

    val RADII_DP = listOf(0, 8, 16, 24, 28)

    fun radius(
        context: android.content.Context,
        appWidgetId: Int,
    ): Int = WidgetUpdateHelper.getPrefs(context).getInt(PREF_RADIUS + appWidgetId, 0)

    fun setRadius(
        context: android.content.Context,
        appWidgetId: Int,
        value: Int,
    ) {
        WidgetUpdateHelper
            .getPrefs(context)
            .edit()
            .putInt(PREF_RADIUS + appWidgetId, value)
            .apply()
    }

    fun showRoom(
        context: android.content.Context,
        appWidgetId: Int,
    ): Boolean = WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_SHOW_ROOM + appWidgetId, true)

    fun isConfigured(
        context: android.content.Context,
        appWidgetId: Int,
    ): Boolean = WidgetUpdateHelper.getPrefs(context).contains(PREF_SHOW_ROOM + appWidgetId)

    fun setShowRoom(
        context: android.content.Context,
        appWidgetId: Int,
        value: Boolean,
    ) {
        WidgetUpdateHelper
            .getPrefs(context)
            .edit()
            .putBoolean(PREF_SHOW_ROOM + appWidgetId, value)
            .apply()
    }

    fun seconds(
        context: android.content.Context,
        appWidgetId: Int,
    ): Boolean = WidgetUpdateHelper.getPrefs(context).getBoolean(PREF_SECONDS + appWidgetId, false)

    fun setSeconds(
        context: android.content.Context,
        appWidgetId: Int,
        value: Boolean,
    ) {
        WidgetUpdateHelper
            .getPrefs(context)
            .edit()
            .putBoolean(PREF_SECONDS + appWidgetId, value)
            .apply()

        WidgetUpdateHelper.invalidateSecondsCache()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = ColorTextTitle,
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    description: String?,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(1.dp, if (selected) ColorBrandBlue else ColorBorderLight),
        color = ColorBgMain,
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bouncyClickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = ColorTextBody,
                )
                if (description != null) {
                    Text(text = description, fontSize = 12.sp, color = ColorTextMuted)
                }
            }
        }
    }
}

@Composable
internal fun ExactAlarmHintCard(
    text: String,
    onAllow: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(2.dp),
        color = ColorBgMain,
        border = BorderStroke(1.dp, ColorBrandBlue),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = ColorTextBody,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = ColorBrandFill,
                modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .bouncyClickable(onClick = onAllow),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = "РАЗРЕШИТЬ",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SecondsOptionSection(
    showSeconds: Boolean,
    onToggle: (Boolean) -> Unit,
    alarmHintVisible: Boolean,
    onAllowAlarms: () -> Unit,
) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Показывать секунды",
                fontSize = 14.sp,
                color = ColorTextBody,
            )
            Text(
                text = "Отсчёт вида «4:32» — обновление каждую секунду",
                fontSize = 12.sp,
                color = ColorTextMuted,
            )
        }
        Switch(checked = showSeconds, onCheckedChange = onToggle)
    }
    if (alarmHintVisible) {
        Spacer(modifier = Modifier.height(8.dp))
        ExactAlarmHintCard(
            text = "Секундный отсчёт требует доступа к будильникам: без него виджет обновляется реже",
            onAllow = onAllowAlarms,
        )
    }
}
