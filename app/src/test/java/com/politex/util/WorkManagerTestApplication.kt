package com.politex.util

import android.app.Application
import androidx.work.Configuration

class WorkManagerTestApplication :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
