package com.politex.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test
    fun `версии сравниваются по числовым сегментам`() {
        assertTrue(UpdateVersions.isNewer("2.0.1", "2.0"))
        assertTrue(UpdateVersions.isNewer("2.1", "2.0.1"))
        assertTrue(UpdateVersions.isNewer("2.0.10", "2.0.9"))
        assertTrue(UpdateVersions.isNewer("3", "2.9.9"))
        assertTrue(UpdateVersions.isNewer("2.0.1", "2"))
    }

    @Test
    fun `префикс v не мешает сравнению`() {
        assertTrue(UpdateVersions.isNewer("v2.0.1", "2.0"))
        assertTrue(UpdateVersions.isNewer("V2.1", "v2.0.1"))
        assertEquals("2.0.1", UpdateVersions.normalize("v2.0.1"))
        assertEquals("2.0.1", UpdateVersions.normalize("V2.0.1"))
    }

    @Test
    fun `равные версии не считаются новее`() {
        assertFalse(UpdateVersions.isNewer("2.0.1", "2.0.1"))
        assertFalse(UpdateVersions.isNewer("v2.0.1", "2.0.1"))
        assertFalse(UpdateVersions.isNewer("2.0", "2.0.0"))
        assertFalse(UpdateVersions.isNewer("2.0.0", "2.0"))
        assertFalse(UpdateVersions.isNewer("2.0.1+build.5", "2.0.1+build.4"))
    }

    @Test
    fun `старая версия не считается новее`() {
        assertFalse(UpdateVersions.isNewer("2.0", "2.0.1"))
        assertFalse(UpdateVersions.isNewer("1.9.9", "2.0"))
        assertFalse(UpdateVersions.isNewer("2.0.01", "2.0.1"))
    }

    @Test
    fun `pre-release ниже релиза`() {
        assertFalse(UpdateVersions.isNewer("2.0.1-beta", "2.0.1"))
        assertTrue(UpdateVersions.isNewer("2.0.1", "2.0.1-rc.1"))
        assertTrue(UpdateVersions.isNewer("2.0.1-rc.2", "2.0.1-rc.1"))
        assertFalse(UpdateVersions.isNewer("2.0.1-alpha", "2.0.1-beta"))
        assertFalse(UpdateVersions.isNewer("2.0.1-1", "2.0.1-alpha"))
        assertTrue(UpdateVersions.isNewer("2.0.1-alpha", "2.0.1-1"))
        assertTrue(UpdateVersions.isNewer("2.0.1-alpha.1", "2.0.1-alpha"))
        assertFalse(UpdateVersions.isNewer("2.0.1-alpha", "2.0.1-alpha.1"))
    }

    @Test
    fun `flavor-суффикс версии разбирается как pre-release`() {
        assertTrue(UpdateVersions.isNewer("2.2.1", "2.2-admin"))
        assertTrue(UpdateVersions.isNewer("2.2", "2.2-admin"))
        assertTrue(UpdateVersions.isNewer("2.2", "2.2-beta"))
    }

    @Test
    fun `мусор не считается новой версией`() {
        assertFalse(UpdateVersions.isNewer(null, "2.0"))
        assertFalse(UpdateVersions.isNewer("", "2.0"))
        assertFalse(UpdateVersions.isNewer("latest", "2.0"))
        assertFalse(UpdateVersions.isNewer("2.0.x", "2.0"))
        assertFalse(UpdateVersions.isNewer("2.0.1.2", "2.0"))
        assertFalse(UpdateVersions.isNewer("2..1", "2.0"))
        assertFalse(UpdateVersions.isNewer("2.0.1", "garbage"))
        assertFalse(UpdateVersions.isNewer("2.0.1", null))
    }

    @Test
    fun `normalize отбрасывает неполные и мусорные теги`() {
        assertNull(UpdateVersions.normalize(null))
        assertNull(UpdateVersions.normalize(""))
        assertNull(UpdateVersions.normalize("v"))
        assertNull(UpdateVersions.normalize("release-2.0"))
        assertNull(UpdateVersions.normalize("2.0.1.1"))
        assertEquals("2.0.1-beta", UpdateVersions.normalize("v2.0.1-beta"))
        assertEquals("2.0.1+build.7", UpdateVersions.normalize("2.0.1+build.7"))
    }
}