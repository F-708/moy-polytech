package com.politex.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateCheckerTest {
    private val latestRelease =
        """
        {
          "tag_name": "v2.0.1",
          "name": "Мой Политех 2.0.1",
          "body": "Исправлены виджеты",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "source.zip",
              "browser_download_url": "https://github.com/o/r/archive/refs/tags/v2.0.1.zip",
              "size": 1000
            },
            {
              "name": "SHA256SUMS.txt",
              "browser_download_url": "https://github.com/o/r/releases/download/v2.0.1/SHA256SUMS.txt",
              "size": 64
            },
            {
              "name": "moy-polytech-v2.0.1.apk",
              "browser_download_url": "https://github.com/o/r/releases/download/v2.0.1/moy-polytech-v2.0.1.apk",
              "size": 24576000
            },
            {
              "name": "moy-polytech-v2.0.1.apk.sha256",
              "browser_download_url": "https://github.com/o/r/releases/download/v2.0.1/moy-polytech-v2.0.1.apk.sha256",
              "size": 64
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `новая версия и apk asset разбираются`() {
        val info = AppUpdateChecker.parseLatestRelease(latestRelease, "2.0", "standard")!!
        assertEquals("v2.0.1", info.tagName)
        assertEquals("2.0.1", info.versionName)
        assertEquals("Исправлены виджеты", info.notes)
        assertEquals("moy-polytech-v2.0.1.apk", info.asset.name)
        assertEquals("https://github.com/o/r/releases/download/v2.0.1/moy-polytech-v2.0.1.apk", info.asset.url)
        assertEquals(24576000L, info.asset.size)
    }

    @Test
    fun `тег без префикса v принимается`() {
        val withoutPrefix = latestRelease.replace("\"tag_name\": \"v2.0.1\"", "\"tag_name\": \"2.0.2\"")
        val info = AppUpdateChecker.parseLatestRelease(withoutPrefix, "2.0.1", "standard")!!
        assertEquals("2.0.2", info.versionName)
    }

    @Test
    fun `та же и старая версии не предлагаются`() {
        assertNull(AppUpdateChecker.parseLatestRelease(latestRelease, "2.0.1", "standard"))
        assertNull(AppUpdateChecker.parseLatestRelease(latestRelease, "2.1", "standard"))
        assertNull(AppUpdateChecker.parseLatestRelease(latestRelease, "2.2-admin", "standard"))
    }

    @Test
    fun `мусорный ответ и ошибки api не роняют проверку`() {
        assertNull(AppUpdateChecker.parseLatestRelease(null, "2.0", "standard"))
        assertNull(AppUpdateChecker.parseLatestRelease("", "2.0", "standard"))
        assertNull(AppUpdateChecker.parseLatestRelease("не json", "2.0", "standard"))
        assertNull(AppUpdateChecker.parseLatestRelease("{}", "2.0", "standard"))
        assertNull(AppUpdateChecker.parseLatestRelease("""{"message":"Not Found"}""", "2.0", "standard"))
        assertNull(
            AppUpdateChecker.parseLatestRelease(
                """{"message":"API rate limit exceeded for 1.2.3.4"}""",
                "2.0",
                "standard",
            ),
        )
    }

    @Test
    fun `черновик и prerelease игнорируются`() {
        val draft = latestRelease.replace("\"draft\": false", "\"draft\": true")
        val prerelease = latestRelease.replace("\"prerelease\": false", "\"prerelease\": true")
        assertNull(AppUpdateChecker.parseLatestRelease(draft, "2.0", "standard"))
        assertNull(AppUpdateChecker.parseLatestRelease(prerelease, "2.0", "standard"))
    }

    @Test
    fun `среди flavor-сборок выбирается standard`() {
        val multiFlavor =
            """
            {
              "tag_name": "v2.0.1",
              "assets": [
                {"name": "moy-polytech-admin-v2.0.1.apk", "browser_download_url": "https://example.com/admin.apk", "size": 10},
                {"name": "moy-polytech-standard-v2.0.1.apk", "browser_download_url": "https://example.com/standard.apk", "size": 20},
                {"name": "moy-polytech-teacher-v2.0.1.apk", "browser_download_url": "https://example.com/teacher.apk", "size": 30}
              ]
            }
            """.trimIndent()
        val info = AppUpdateChecker.parseLatestRelease(multiFlavor, "2.0", "standard")!!
        assertEquals("https://example.com/standard.apk", info.asset.url)
        assertEquals(20L, info.asset.size)
    }

    @Test
    fun `без apk в ассетах обновление не предлагается`() {
        val noApk =
            """
            {
              "tag_name": "v9.0.0",
              "assets": [
                {"name": "source.zip", "browser_download_url": "https://example.com/source.zip", "size": 1000},
                {"name": "SHA256SUMS.txt", "browser_download_url": "https://example.com/sums", "size": 64}
              ]
            }
            """.trimIndent()
        assertNull(AppUpdateChecker.parseLatestRelease(noApk, "2.0", "standard"))
    }

    @Test
    fun `404 и rate limit дают тихий выход`() {
        assertNull(AppUpdateChecker.parseResponse(404, null, "2.0", "standard"))
        assertNull(
            AppUpdateChecker.parseResponse(
                403,
                """{"message":"API rate limit exceeded for 1.2.3.4"}""",
                "2.0",
                "standard",
            ),
        )
        assertNull(AppUpdateChecker.parseResponse(429, "", "2.0", "standard"))
        assertEquals(
            "2.0.1",
            AppUpdateChecker.parseResponse(200, latestRelease, "2.0", "standard")?.versionName,
        )
    }

    @Test
    fun `api url собирается из owner и repo`() {
        assertEquals(
            "https://api.github.com/repos/F-708/MPK-app/releases/latest",
            AppUpdateChecker.latestReleaseUrl("F-708", "MPK-app"),
        )
    }

    @Test
    fun `плейсхолдеры не считаются настроенным репозиторием`() {
        assertFalse(AppUpdateChecker.isConfigured("OWNER_PLACEHOLDER", "MPK-app"))
        assertFalse(AppUpdateChecker.isConfigured("F-708", "REPO_PLACEHOLDER"))
        assertFalse(AppUpdateChecker.isConfigured("", "MPK-app"))
        assertFalse(AppUpdateChecker.isConfigured("F-708", " "))
        assertTrue(AppUpdateChecker.isConfigured("F-708", "MPK-app"))
    }

    @Test
    fun `автообновление включено только для standard`() {
        assertEquals(setOf("standard"), AppUpdateChecker.UPDATE_ENABLED_FLAVORS)
    }
}
