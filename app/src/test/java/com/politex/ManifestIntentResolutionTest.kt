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
        // Имена компонентов резолвятся от namespace (com.politex), а packageName
        // во флейворах несёт суффикс (.admin/.adminplus) — фильтр по packageName
        // выбрасывал бы все свои же активности.
        return infos.map { it.activityInfo.name }.filter { it.startsWith("com.politex") }
    }

    @Test
    fun `VIEW apk не резолвится в FileViewAlias`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.example.provider/update.apk"),
                    "application/vnd.android.package-archive",
                )
            }
        assertFalse(resolveNames(intent).any { it.endsWith(".FileViewAlias") })
    }

    @Test
    fun `VIEW zip не резолвится в FileViewAlias`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.example.provider/archive.zip"), "application/zip")
            }
        assertFalse(resolveNames(intent).any { it.endsWith(".FileViewAlias") })
    }

    @Test
    fun `VIEW audio не резолвится в FileViewAlias`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.example.provider/track.mp3"), "audio/mpeg")
            }
        assertFalse(resolveNames(intent).any { it.endsWith(".FileViewAlias") })
    }

    @Test
    fun `VIEW pdf резолвится в FileViewAlias`() {
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.example.provider/lecture.pdf"), "application/pdf")
            }
        assertTrue(resolveNames(intent).any { it.endsWith(".FileViewAlias") })
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
