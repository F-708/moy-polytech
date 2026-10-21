package com.politex.content

import com.politex.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPackWorkerPredicateTest {
    @Test
    fun `относительный url резолвится к корню content`() {
        val base = "https://raw.githubusercontent.com/o/r/main/content/"
        assertEquals(base + "content-pack.zip", resolvePackUrl(base, "content-pack.zip"))
        assertEquals(base + "content-pack.zip", resolvePackUrl(base, "./content-pack.zip"))
        assertEquals(base + "content-pack.zip", resolvePackUrl(base, "/content-pack.zip"))
        assertEquals(base + "content-pack.zip", resolvePackUrl(base, "  content-pack.zip  "))
        assertEquals(
            "https://raw.githubusercontent.com/o/r/main/content/pack.zip",
            resolvePackUrl("https://raw.githubusercontent.com/o/r/main/content", "pack.zip"),
        )
    }

    @Test
    fun `абсолютный url используется как есть`() {
        val absolute = "https://cdn.example.com/packs/content-pack.zip"
        assertEquals(absolute, resolvePackUrl("https://raw.githubusercontent.com/o/r/main/content/", absolute))
        val http = "http://cdn.example.com/p.zip"
        assertEquals(http, resolvePackUrl("https://base/", http))
    }

    @Test
    fun `базовый url указывает на content ветки main`() {
        val base = ContentPackWorker.contentBaseUrl()
        assertTrue(base.startsWith("https://raw.githubusercontent.com/"))
        assertTrue(base.contains(BuildConfig.GITHUB_OWNER))
        assertTrue(base.contains(BuildConfig.GITHUB_REPO))
        assertTrue(base.endsWith("/main/content/"))
    }

    @Test
    fun `version json url строится из базового`() {
        assertEquals(ContentPackWorker.contentBaseUrl() + "version.json", ContentPackWorker.versionJsonUrl())
    }
}
