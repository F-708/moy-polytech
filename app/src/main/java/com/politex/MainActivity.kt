package com.politex

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.politex.ui.screens.MainScreen
import com.politex.ui.screens.buildMapRoute
import com.politex.ui.screens.buildScrollRoute
import com.politex.ui.screens.materials.ExternalMaterialIntake
import com.politex.ui.screens.navigateToTab
import com.politex.ui.theme.MyApplicationTheme
import com.politex.ui.viewmodel.ARG_SCROLL
import com.politex.ui.viewmodel.AppTab
import com.politex.ui.viewmodel.AppViewModel
import com.politex.ui.viewmodel.ROUTE_LESSONS
import com.politex.util.NotificationHelper
import com.politex.util.PendingMaterial
import com.politex.util.TeacherModeStore
import com.politex.worker.MpkWorkManagerHelper

internal fun shouldNavigateToLessons(currentRoute: String?): Boolean =
    currentRoute?.substringBefore("?") != ROUTE_LESSONS

internal fun acceptIntake(
    flavor: String,
    teacherMode: Boolean,
): Boolean = flavor != "teacher" && !teacherMode

internal fun intakeFingerprint(material: PendingMaterial): String =
    when (material) {
        is PendingMaterial.SingleFile -> "f:" + material.uri
        is PendingMaterial.Images -> "i:" + material.uris.joinToString("|")
        is PendingMaterial.Text -> "t:" + material.text
    }

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.politex.ui.theme
            .applyAppTheme(
                com.politex.util.AppThemeStore
                    .load(this),
            )

        NotificationHelper.createNotificationChannel(this)
        com.politex.util.BellCountdownNotifier
            .createChannel(this)

        com.politex.util.BellTimerService
            .sync(this)
        if (BuildConfig.FLAVOR == "teacher") {
            com.politex.ui.teacher.initUiScale(this)
            MpkWorkManagerHelper.setupTeacherSync(this)

            MpkWorkManagerHelper.setupBellServiceWatchdog(this)

            com.politex.widget.TeacherWidgetUpdater.forceCollegeBellDefaults(this)
        } else {
            MpkWorkManagerHelper.setupPeriodicScheduleCheck(this)
            MpkWorkManagerHelper.setupBellServiceWatchdog(this)
            MpkWorkManagerHelper.setupPeriodicReminderCheck(this)
            if (TeacherModeStore.isTeacher(this)) {
                MpkWorkManagerHelper.setupTeacherSync(this)
            }
            com.politex.update.AppUpdateWorker.schedule(this)
        }
        com.politex.content.ContentPackWorker.schedule(this)

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val navController = rememberNavController()
            this@MainActivity.navController = navController

            if (BuildConfig.FLAVOR != "teacher") {
                LaunchedEffect(Unit) {
                    handleIntent(intent, navController)
                }
            }

            if (BuildConfig.FLAVOR == "teacher") {
                com.politex.ui.teacher.TeacherRoot(modifier = Modifier.fillMaxSize())
            } else {
                MyApplicationTheme {
                    MainScreen(
                        viewModel = appViewModel,
                        navController = navController,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handledIntakeKey = null

        navController?.let { handleIntent(intent, it) }
    }

    override fun onDestroy() {
        if (isFinishing) handledIntakeKey = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

    }

    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController,
    ) {
        val room = intent?.getStringExtra("EXTRA_ROOM")
        if (!room.isNullOrBlank()) {
            navController.navigate(
                buildMapRoute(
                    room,
                    from = intent.getStringExtra("EXTRA_FROM_ROOM"),
                    showRoute = intent.getBooleanExtra("EXTRA_SHOW_ROUTE", false),
                ),
            )
            return
        }
        if (BuildConfig.FLAVOR != "teacher" && intent != null) {
            val external = ExternalMaterialIntake.fromIntent(contentResolver, intent)
            if (external != null) {
                val key = intakeFingerprint(external)
                if (key != handledIntakeKey) {
                    handledIntakeKey = key
                    if (acceptIntake(BuildConfig.FLAVOR, TeacherModeStore.isTeacher(this))) {
                        ExternalMaterialIntake.offer(external)
                        if (shouldNavigateToLessons(navController.currentDestination?.route)) {
                            navController.navigateToTab(ROUTE_LESSONS)
                        }
                    } else {
                        Toast.makeText(this, "Материалы недоступны в режиме преподавателя", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
        }
        val scrollTarget = intent?.getStringExtra("EXTRA_SCROLL_TARGET")
        val tab =
            when (intent?.getStringExtra("EXTRA_OPEN_TAB")) {
                "SCHEDULE" -> AppTab.SCHEDULE
                "BELLS" -> AppTab.BELLS

                "TASKS" -> AppTab.TASKS
                else -> null
            } ?: return
        navController.navigateToTab(buildScrollRoute(tab.route, scrollTarget))

        if (!scrollTarget.isNullOrBlank()) {
            navController.currentBackStackEntry?.savedStateHandle?.set(ARG_SCROLL, scrollTarget)
        }
    }

    private companion object {
        var handledIntakeKey: String? = null
    }
}
