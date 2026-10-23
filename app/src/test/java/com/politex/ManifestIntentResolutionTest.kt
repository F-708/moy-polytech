package com.politex

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManifestIntentResolutionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun resolveNames(intent: Intent): List<String> {
        @Suppress("DEPRECATION")
        val infos = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return infos.map { it.activityInfo.name }.filter { it.startsWith(context.packageName) }
    }

    @Test
    fun `VIEW content star-star резолвится в FileViewAlias`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.example.provider/file"), "*/*")
            }
        val names = resolveNames(intent)
        assertTrue(names.any { it.endsWith(".FileViewAlias") })
    }

    @Test
    fun `SEND pdf резолвится в MainActivity а не в alias`() {
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
            }
        val names = resolveNames(intent)
        assertTrue(names.any { it.endsWith(".MainActivity") })
        assertFalse(names.any { it.endsWith(".FileViewAlias") })
    }
}
