package com.politex.ui.teacher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun TeacherRoot(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(TeacherProfileStore.get(context)) }
    var skipped by remember { mutableStateOf(false) }

    TeacherTheme {
        ScaledDensityProvider {
            if (TeacherOnboardingLogic.showOnboarding(profile, skipped)) {
                TeacherOnboardingScreen(
                    onProfileSaved = { profile = TeacherProfileStore.get(context) },
                    onSkipAll = {
                        skipped = true
                        profile = TeacherProfileStore.get(context)
                    },
                )
            } else {
                TeacherNav(
                    profile = profile,
                    onPickProfile = {
                        TeacherProfileStore.set(context, null)
                        profile = null
                        skipped = false
                    },
                    modifier = modifier.fillMaxSize(),
                )
            }
        }
    }
}
