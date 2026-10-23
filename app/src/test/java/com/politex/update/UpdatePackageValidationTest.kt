package com.politex.update

import com.politex.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdatePackageValidationTest {
    private val ownSignature = "a3f1".repeat(16)
    private val alienSignature = "b7c9".repeat(16)

    @Test
    fun `applicationId сборки — com politex`() {
        assertEquals("com.politex", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `apk с собственным пакетом принимается`() {
        assertTrue(UpdateDownloader.apkPackageAccepted("com.politex", "com.politex"))
    }

    @Test
    fun `apk со старым aistudio id отклоняется`() {
        assertFalse(
            UpdateDownloader.apkPackageAccepted(
                "com.politex",
                "com.aistudio.mpkschedule.vbyqzt",
            ),
        )
    }

    @Test
    fun `чужие и пустые пакеты отклоняются`() {
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", null))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", ""))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", "com.example.fake"))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", "com.politex.evil"))
    }

    @Test
    fun `flavor-суффиксы валидируются строго по своему пакету`() {
        assertTrue(UpdateDownloader.apkPackageAccepted("com.politex.admin", "com.politex.admin"))
        assertTrue(UpdateDownloader.apkPackageAccepted("com.politex.teacher", "com.politex.teacher"))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex", "com.politex.admin"))
        assertFalse(UpdateDownloader.apkPackageAccepted("com.politex.admin", "com.politex"))
    }

    @Test
    fun `совпадающая подпись принимается`() {
        assertTrue(UpdateDownloader.signatureAccepted(ownSignature, ownSignature))
        assertTrue(UpdateDownloader.signatureAccepted(ownSignature.uppercase(), ownSignature.lowercase()))
    }

    @Test
    fun `несовпадающая подпись отклоняется`() {
        assertFalse(UpdateDownloader.signatureAccepted(ownSignature, alienSignature))
        assertFalse(UpdateDownloader.signatureAccepted(null, alienSignature))
        assertFalse(UpdateDownloader.signatureAccepted(ownSignature, null))
        assertFalse(UpdateDownloader.signatureAccepted("", ""))
    }

    @Test
    fun `versionCode в apk должен быть больше текущего`() {
        assertTrue(UpdateDownloader.versionAccepted(22003, 22002))
        assertFalse(UpdateDownloader.versionAccepted(22002, 22002))
        assertFalse(UpdateDownloader.versionAccepted(22001, 22002))
        assertFalse(UpdateDownloader.versionAccepted(null, 22002))
    }

    @Test
    fun `совпадающие пакет подпись и версия принимаются`() {
        val apk = ApkMetadata("com.politex", 22003, ownSignature)
        assertEquals(
            ApkRejection.NONE,
            UpdateDownloader.evaluate("com.politex", ownSignature, 22002, apk),
        )
    }

    @Test
    fun `чужой пакет отклоняется раньше подписи`() {
        val apk = ApkMetadata("com.example.fake", 999999, alienSignature)
        assertEquals(
            ApkRejection.PACKAGE,
            UpdateDownloader.evaluate("com.politex", ownSignature, 22002, apk),
        )
    }

    @Test
    fun `чужая подпись отклоняется`() {
        val apk = ApkMetadata("com.politex", 999999, alienSignature)
        assertEquals(
            ApkRejection.SIGNATURE,
            UpdateDownloader.evaluate("com.politex", ownSignature, 22002, apk),
        )
    }

    @Test
    fun `apk без более нового versionCode отклоняется`() {
        val apk = ApkMetadata("com.politex", 22002, ownSignature)
        assertEquals(
            ApkRejection.VERSION,
            UpdateDownloader.evaluate("com.politex", ownSignature, 22002, apk),
        )
    }

    @Test
    fun `нечитаемый apk отклоняется`() {
        assertEquals(
            ApkRejection.UNREADABLE,
            UpdateDownloader.evaluate("com.politex", ownSignature, 22002, null),
        )
    }

    @Test
    fun `сообщение об отказе по подписи`() {
        assertEquals(
            "Обновление отклонено: подпись не совпадает",
            UpdateDownloader.rejectionMessage(ApkRejection.SIGNATURE),
        )
    }

    @Test
    fun `целостность загрузки проверяется по размеру`() {
        assertTrue(UpdateDownloader.downloadComplete(1000, 1000))
        assertTrue(UpdateDownloader.downloadComplete(1000, 0))
        assertFalse(UpdateDownloader.downloadComplete(0, 0))
        assertFalse(UpdateDownloader.downloadComplete(999, 1000))
    }

    @Test
    fun `sha256 сертификата считается по байтам`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            UpdateDownloader.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `имя временного файла не содержит путь`() {
        assertEquals("2.0.1", UpdateDownloader.safeFileName("2.0.1"))
        assertEquals("2.0.1_1", UpdateDownloader.safeFileName("2.0.1/1"))
        assertEquals("2.0.1_beta", UpdateDownloader.safeFileName("2.0.1 beta"))
    }
}
