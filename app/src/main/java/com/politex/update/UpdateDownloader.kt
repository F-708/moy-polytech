package com.politex.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.politex.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

enum class ApkRejection {
    NONE,
    UNREADABLE,
    PACKAGE,
    SIGNATURE,
    VERSION,
}

internal data class ApkMetadata(
    val packageName: String?,
    val versionCode: Int?,
    val signatureSha256: String?,
)

object UpdateDownloader {
    private val client by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    fun download(
        context: Context,
        asset: UpdateAsset,
        versionName: String,
    ): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "update-${safeFileName(versionName)}.apk")
        val downloaded =
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url(asset.url)
                        .header("User-Agent", AppUpdateChecker.USER_AGENT)
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                    if (!downloadComplete(target.length(), asset.size)) {
                        return@use null
                    }
                    target
                }
            }.getOrNull()
        if (downloaded == null) target.delete()
        return downloaded
    }

    fun validate(
        context: Context,
        file: File,
    ): ApkRejection {
        if (!file.exists() || file.length() <= 0L) return ApkRejection.UNREADABLE
        val manager = context.packageManager
        val archived = readApkMetadata(manager, file.absolutePath)
        val installedSignature = installedSignatureSha256(manager, context.packageName)
        return evaluate(
            ownPackage = BuildConfig.APPLICATION_ID,
            ownSignatureSha256 = installedSignature,
            currentVersionCode = BuildConfig.VERSION_CODE,
            apk = archived,
        )
    }

    internal fun evaluate(
        ownPackage: String,
        ownSignatureSha256: String?,
        currentVersionCode: Int,
        apk: ApkMetadata?,
    ): ApkRejection =
        when {
            apk == null -> ApkRejection.UNREADABLE
            !apkPackageAccepted(ownPackage, apk.packageName) -> ApkRejection.PACKAGE
            !signatureAccepted(ownSignatureSha256, apk.signatureSha256) -> ApkRejection.SIGNATURE
            !versionAccepted(apk.versionCode, currentVersionCode) -> ApkRejection.VERSION
            else -> ApkRejection.NONE
        }

    internal fun apkPackageAccepted(
        ownPackage: String,
        apkPackage: String?,
    ): Boolean = !apkPackage.isNullOrBlank() && apkPackage == ownPackage

    internal fun signatureAccepted(
        installedSignatureSha256: String?,
        apkSignatureSha256: String?,
    ): Boolean =
        !installedSignatureSha256.isNullOrBlank() &&
            !apkSignatureSha256.isNullOrBlank() &&
            installedSignatureSha256.equals(apkSignatureSha256, ignoreCase = true)

    internal fun versionAccepted(
        apkVersionCode: Int?,
        currentVersionCode: Int,
    ): Boolean = apkVersionCode != null && apkVersionCode > currentVersionCode

    internal fun matchesChecksum(
        file: File,
        expectedSha256: String?,
    ): Boolean {
        val expected = expectedSha256?.trim().orEmpty()
        if (expected.isEmpty()) return true
        if (!file.exists() || file.length() <= 0L) return false
        val actual = runCatching { sha256Hex(file.readBytes()) }.getOrNull() ?: return false
        return actual.equals(expected, ignoreCase = true)
    }

    internal fun downloadComplete(
        actualBytes: Long,
        expectedBytes: Long,
    ): Boolean = actualBytes > 0L && (expectedBytes <= 0L || actualBytes == expectedBytes)

    internal fun rejectionMessage(rejection: ApkRejection): String =
        when (rejection) {
            ApkRejection.PACKAGE -> "Обновление отклонено: пакет не совпадает"
            ApkRejection.SIGNATURE -> "Обновление отклонено: подпись не совпадает"
            ApkRejection.VERSION -> "Обновление отклонено: версия не новее установленной"
            ApkRejection.UNREADABLE, ApkRejection.NONE -> "Файл обновления не найден или повреждён"
        }

    internal fun safeFileName(raw: String): String {
        val sanitized = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return sanitized.ifBlank { "latest" }
    }

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }

    private fun readApkMetadata(
        manager: PackageManager,
        path: String,
    ): ApkMetadata? =
        runCatching {
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            val info =
                manager.getPackageArchiveInfo(path, flags)
                    ?: return@runCatching null
            ApkMetadata(
                packageName = info.packageName,
                versionCode = info.versionCodeCompat(),
                signatureSha256 = info.signatureSha256(),
            )
        }.getOrNull()

    private fun installedSignatureSha256(
        manager: PackageManager,
        packageName: String,
    ): String? =
        runCatching {
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                }
            info.signatureSha256()
        }.getOrNull()

    private fun PackageInfo.versionCodeCompat(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            versionCode
        }

    private fun PackageInfo.signatureSha256(): String? {
        val signers =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val modern = signingInfo?.apkContentsSigners
                if (modern.isNullOrEmpty()) {
                    @Suppress("DEPRECATION")
                    signatures
                } else {
                    modern
                }
            } else {
                @Suppress("DEPRECATION")
                signatures
            }
        return signers?.firstOrNull()?.toByteArray()?.let(::sha256Hex)
    }
}