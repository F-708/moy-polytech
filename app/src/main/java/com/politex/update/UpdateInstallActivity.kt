package com.politex.update

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = intent.getStringExtra(EXTRA_APK_PATH)?.let(::File)
        if (file == null) {
            reject(ApkRejection.UNREADABLE)
            return
        }
        val rejection = UpdateDownloader.validate(this, file)
        if (rejection != ApkRejection.NONE) {
            reject(rejection)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingFile = file
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        launchInstaller(file)
    }

    override fun onResume() {
        super.onResume()
        val file = pendingFile
        pendingFile = null
        if (file != null) launchInstaller(file) else finish()
    }

    private fun reject(rejection: ApkRejection) {
        Toast.makeText(this, UpdateDownloader.rejectionMessage(rejection), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun launchInstaller(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.updateprovider", file)
            val installIntent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val target = resolveInstaller(installIntent)
            if (target == null) {
                Toast.makeText(this, "Установщик не найден", Toast.LENGTH_LONG).show()
                return@runCatching
            }
            installIntent.component = ComponentName(target.activityInfo.packageName, target.activityInfo.name)
            startActivity(installIntent)
        }.onFailure {
            Toast.makeText(this, "Не удалось открыть установщик", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    private fun resolveInstaller(intent: Intent): ResolveInfo? {
        @Suppress("DEPRECATION")
        val candidates =
            packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName != packageName }
        return candidates.firstOrNull { it.isSystemApp() }
    }

    private fun ResolveInfo.isSystemApp(): Boolean = ((activityInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) != 0

    companion object {
        const val EXTRA_APK_PATH = "extra_apk_path"
        private var pendingFile: File? = null
    }
}
