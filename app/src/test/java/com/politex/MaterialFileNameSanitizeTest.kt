package com.politex

import com.politex.util.MaterialImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialFileNameSanitizeTest {

    @Test
    fun `bidi и управляющие символы удаляются`() {
        val name = MaterialImporter.sanitizeFileName("лек\u202Eция\u0007\n2024.pdf")

        assertFalse(name.contains('\u202E'))
        assertFalse(name.contains('\u0007'))
        assertFalse(name.contains('\n'))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `пустое имя получает fallback`() {
        assertEquals("Файл", MaterialImporter.sanitizeFileName("   "))
        assertEquals("Файл", MaterialImporter.sanitizeFileName(null))
        assertEquals("Файл", MaterialImporter.sanitizeFileName("\u200B\u200F"))
    }

    @Test
    fun `длинное имя обрезается с сохранением расширения`() {
        val name = MaterialImporter.sanitizeFileName("a".repeat(300) + ".pdf")

        assertTrue(name.length <= 120)
        assertTrue(name.endsWith(".pdf"))
    }
}