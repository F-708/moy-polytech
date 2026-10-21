package com.politex.update

import com.politex.util.UpdateChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateChannelSelectionTest {
    private fun metaJson(
        channel: String,
        version: String,
        code: Int? = null,
        apk: String = "https://github.com/o/r/releases/download/v$version/moy-polytech-standard-v$version.apk",
    ): String {
        val codeField = if (code != null) "\"version_code\": $code," else ""
        return """
            {
              "channel": "$channel",
              "version": "$version",
              $codeField
              "apk": "$apk",
              "sha256": "AB12",
              "published_at": "2026-10-22T12:00:00Z",
              "min_version_code": 0
            }
        """.trimIndent()
    }

    private fun releaseJson(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        apkName: String = "moy-polytech-standard-$tag.apk",
        url: String = "https://example.com/$apkName",
    ): String =
        """
        {
          "tag_name": "$tag",
          "body": "notes $tag",
          "draft": $draft,
          "prerelease": $prerelease,
          "assets": [
            {"name": "$apkName", "browser_download_url": "$url", "size": 42}
          ]
        }
        """.trimIndent()

    @Test
    fun `мета разбирается по контракту`() {
        val meta = UpdateMetaSource.parse(metaJson("stable", "2.0.1", code = 20001999))!!
        assertEquals("stable", meta.channel)
        assertEquals("2.0.1", meta.version)
        assertEquals(20001999, meta.versionCode)
        assertTrue(meta.apk.endsWith("moy-polytech-standard-v2.0.1.apk"))
        assertEquals("ab12", meta.sha256)
        assertEquals("2026-10-22T12:00:00Z", meta.publishedAt)
        assertEquals(0, meta.minVersionCode)
    }

    @Test
    fun `version_code из меты приоритетнее, при отсутствии выводится из версии`() {
        assertEquals(
            12345,
            UpdateMetaSource.parse(metaJson("stable", "2.0.1", code = 12345))!!.versionCode,
        )
        assertEquals(
            versionCodeOf("2.0.1"),
            UpdateMetaSource.parse(metaJson("preview", "2.0.1"))!!.versionCode,
        )
    }

    @Test
    fun `невалидная мета отклоняется`() {
        assertNull(UpdateMetaSource.parse(null))
        assertNull(UpdateMetaSource.parse(""))
        assertNull(UpdateMetaSource.parse("не json"))
        assertNull(UpdateMetaSource.parse("""{"message":"404"}"""))
        assertNull(UpdateMetaSource.parse(metaJson("beta", "2.0.1")))
        assertNull(UpdateMetaSource.parse(metaJson("stable", "")))
        assertNull(UpdateMetaSource.parse(metaJson("stable", "2.0.1", apk = "ftp://bad/apk")))
        assertNull(UpdateMetaSource.parse(metaJson("stable", "2.0.1", apk = " ")))
    }

    @Test
    fun `стабильный канал никогда не ставит превью из меты`() {
        assertTrue(UpdateMetaSource.metaMatchesChannel("stable", UpdateChannel.STABLE))
        assertTrue(UpdateMetaSource.metaMatchesChannel("preview", UpdateChannel.PREVIEW))
        assertTrue(UpdateMetaSource.metaMatchesChannel("stable", UpdateChannel.PREVIEW))
        assertNull(
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("preview", "2.1.0-preview.1", code = 20100001)),
                UpdateChannel.STABLE,
                20000999,
            ),
        )
    }

    @Test
    fun `стабильный канал получает свежий стабильный релиз из меты`() {
        val info =
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("stable", "2.0.1", code = 20001999)),
                UpdateChannel.STABLE,
                20000999,
            )!!
        assertEquals("2.0.1", info.versionName)
        assertEquals(20001999, info.versionCode)
        assertEquals("ab12", info.sha256)
        assertEquals("moy-polytech-standard-v2.0.1.apk", info.asset.name)
        assertEquals(0L, info.asset.size)
    }

    @Test
    fun `превью-канал получает и превью и стабильный из меты`() {
        assertNotNull(
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("preview", "2.1.0-preview.1", code = 20100001)),
                UpdateChannel.PREVIEW,
                20000999,
            ),
        )
        assertNotNull(
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("stable", "2.0.1", code = 20001999)),
                UpdateChannel.PREVIEW,
                20000999,
            ),
        )
    }

    @Test
    fun `даунгрейда нет — равный и меньший коды не предлагаются`() {
        assertNull(
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("stable", "2.0.1", code = 20001999)),
                UpdateChannel.STABLE,
                20001999,
            ),
        )
        assertNull(
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("stable", "2.0.0", code = 20000999)),
                UpdateChannel.STABLE,
                20001999,
            ),
        )
    }

    @Test
    fun `переход превью-стабильные не даунгрейдит`() {
        val previewUser = versionCodeOf("2.1.0-preview.3")!!
        assertNull(
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("stable", "2.0.9", code = versionCodeOf("2.0.9")!!)),
                UpdateChannel.STABLE,
                previewUser,
            ),
        )
        val stableSameLine =
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("stable", "2.1.0", code = versionCodeOf("2.1.0")!!)),
                UpdateChannel.STABLE,
                previewUser,
            )
        assertEquals("2.1.0", stableSameLine?.versionName)
    }

    @Test
    fun `переход стабильные-превью предлагает ближайший превью с большим кодом`() {
        val stableUser = versionCodeOf("2.0.0")!!
        val info =
            UpdateMetaSource.selectFromMeta(
                UpdateMetaSource.parse(metaJson("preview", "2.1.0-preview.1", code = versionCodeOf("2.1.0-preview.1")!!)),
                UpdateChannel.PREVIEW,
                stableUser,
            )
        assertEquals("2.1.0-preview.1", info?.versionName)
    }

    @Test
    fun `api latest для стабильного канала — только не-пререлиз и только новее по коду`() {
        val info =
            UpdateMetaSource.selectFromLatestRelease(releaseJson("v2.0.1"), 20000999, "standard")!!
        assertEquals("v2.0.1", info.tagName)
        assertEquals("2.0.1", info.versionName)
        assertEquals(20001999, info.versionCode)
        assertEquals("notes v2.0.1", info.notes)
        assertNull(UpdateMetaSource.selectFromLatestRelease(releaseJson("v2.0.1", prerelease = true), 20000999, "standard"))
        assertNull(UpdateMetaSource.selectFromLatestRelease(releaseJson("v2.0.1", draft = true), 20000999, "standard"))
        assertNull(UpdateMetaSource.selectFromLatestRelease(releaseJson("v2.0.1"), 20001999, "standard"))
        assertNull(UpdateMetaSource.selectFromLatestRelease(releaseJson("v2.1.0-preview.1"), 20000999, "standard"))
        assertNull(UpdateMetaSource.selectFromLatestRelease("""{"message":"Not Found"}""", 20000999, "standard"))
        assertNull(UpdateMetaSource.selectFromLatestRelease(null, 20000999, "standard"))
    }

    @Test
    fun `список релизов — стабильный канал игнорирует чужие теги`() {
        val releases =
            """
            [
              ${releaseJson("v2.1.0-preview.1", prerelease = true)},
              ${releaseJson("v2.0.2-preview.9", prerelease = false)},
              ${releaseJson("v2.0.1")},
              ${releaseJson("v1.9.9")}
            ]
            """.trimIndent()
        val stable = UpdateMetaSource.selectFromReleases(releases, UpdateChannel.STABLE, 20000999, "standard")!!
        assertEquals("v2.0.1", stable.tagName)
    }

    @Test
    fun `список релизов — превью-канал берёт самый свежий тег включая prerelease`() {
        val releases =
            """
            [
              ${releaseJson("v2.0.1")},
              ${releaseJson("v2.1.0-preview.2", prerelease = true)},
              ${releaseJson("v2.1.0-preview.1", prerelease = true)},
              ${releaseJson("v3.0.0", draft = true)}
            ]
            """.trimIndent()
        val preview = UpdateMetaSource.selectFromReleases(releases, UpdateChannel.PREVIEW, 20000999, "standard")!!
        assertEquals("v2.1.0-preview.2", preview.tagName)
        assertEquals(20100002, preview.versionCode)
    }

    @Test
    fun `список релизов — черновики и старьё пропускаются, даунгрейда нет`() {
        val releases = "[${releaseJson("v2.0.1")}, ${releaseJson("v1.0.0")}]"
        assertNull(UpdateMetaSource.selectFromReleases(releases, UpdateChannel.STABLE, 20001999, "standard"))
        assertNull(UpdateMetaSource.selectFromReleases("[${releaseJson("v2.0.1", draft = true)}]", UpdateChannel.PREVIEW, 1, "standard"))
        assertNull(UpdateMetaSource.selectFromReleases("[]", UpdateChannel.PREVIEW, 1, "standard"))
        assertNull(UpdateMetaSource.selectFromReleases("не json", UpdateChannel.PREVIEW, 1, "standard"))
    }

    @Test
    fun `список релизов — выбирается apk своего flavor`() {
        val releases =
            """
            [
              {
                "tag_name": "v2.0.1",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {"name": "moy-polytech-admin-v2.0.1.apk", "browser_download_url": "https://example.com/admin.apk", "size": 10},
                  {"name": "moy-polytech-standard-v2.0.1.apk", "browser_download_url": "https://example.com/standard.apk", "size": 20}
                ]
              }
            ]
            """.trimIndent()
        val info = UpdateMetaSource.selectFromReleases(releases, UpdateChannel.STABLE, 20000999, "standard")!!
        assertEquals("https://example.com/standard.apk", info.asset.url)
    }

    @Test
    fun `мета без apk не предлагает обновление`() {
        val releases =
            """
            [
              {
                "tag_name": "v2.0.1",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {"name": "source.zip", "browser_download_url": "https://example.com/source.zip", "size": 10}
                ]
              }
            ]
            """.trimIndent()
        assertNull(UpdateMetaSource.selectFromReleases(releases, UpdateChannel.STABLE, 20000999, "standard"))
    }

    @Test
    fun `url источников собираются из owner и repo`() {
        assertEquals(
            "https://raw.githubusercontent.com/F-708/MPK-app/main/update-meta.json",
            AppUpdateChecker.rawMetaUrl("F-708", "MPK-app"),
        )
        assertEquals(
            "https://api.github.com/repos/F-708/MPK-app/releases?per_page=8",
            AppUpdateChecker.releasesUrl("F-708", "MPK-app"),
        )
    }
}
