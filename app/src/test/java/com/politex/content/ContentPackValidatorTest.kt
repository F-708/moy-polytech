package com.politex.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ContentPackValidatorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildPackDir(
        root: File,
        teachers: Int = 55,
        groups: Int = 25,
        missingPhoto: String? = null,
        mapJson: String = MAP_JSON,
        includeMap: Boolean = true,
    ): File {
        val dir = File(root, "pack").apply { mkdirs() }
        File(dir, "teachers.csv").writeText(
            (1..teachers).joinToString("\n") { i ->
                val photo = if (i == 1 && missingPhoto != null) missingPhoto else "p$i.jpg"
                "Фамилия$i Имя Отчество;Преподаватель;Предмет;;;;$photo;;;"
            },
            Charsets.UTF_8,
        )
        File(dir, "group_codes.csv").writeText((1..groups).joinToString("\n") { "$it" + "1А" }, Charsets.UTF_8)
        if (includeMap) File(dir, "college_map.json").writeText(mapJson, Charsets.UTF_8)
        val photos = File(dir, "teachers_photos").apply { mkdirs() }
        for (i in 1..teachers) {
            val name = "p$i.jpg"
            if (name != missingPhoto) File(photos, name).writeBytes(byteArrayOf(1, 2, 3))
        }
        return dir
    }

    private fun zipDir(
        source: File,
        zip: File,
    ) {
        ZipOutputStream(zip.outputStream()).use { out ->
            source
                .walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    out.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
        }
    }

    private fun zipWithEntries(
        zip: File,
        entries: Map<String, ByteArray>,
    ) {
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
    }

    @Test
    fun `валидный пакет проходит валидацию и распаковку`() {
        val dir = buildPackDir(tempFolder.root)
        assertEquals(ContentPackRejection.NONE, ContentPackValidator.validateContent(dir))

        val zip = File(tempFolder.root, "pack.zip")
        zipDir(dir, zip)
        val target = File(tempFolder.root, "unpacked")
        assertEquals(ContentPackRejection.NONE, ContentPackValidator.extract(zip, target))
        assertEquals(ContentPackRejection.NONE, ContentPackValidator.validateContent(target))
        assertTrue(File(target, "teachers_photos/p1.jpg").isFile)
    }

    @Test
    fun `битая контрольная сумма отклоняется`() {
        val zip = File(tempFolder.root, "pack.zip")
        zipDir(buildPackDir(tempFolder.root), zip)
        val actual = ContentPackValidator.sha256Hex(zip.readBytes())
        assertTrue(ContentPackValidator.matchesChecksum(zip, actual))
        assertTrue(ContentPackValidator.matchesChecksum(zip, actual.uppercase()))
        assertFalse(ContentPackValidator.matchesChecksum(zip, "0".repeat(64)))
        assertFalse(ContentPackValidator.matchesChecksum(zip, ""))
        assertFalse(ContentPackValidator.matchesChecksum(File(tempFolder.root, "missing.zip"), actual))
    }

    @Test
    fun `zip-slip запись отклоняется при распаковке`() {
        val zip = File(tempFolder.root, "evil.zip")
        zipWithEntries(zip, mapOf("../evil.txt" to "x".toByteArray()))
        val target = File(tempFolder.root, "unpacked")
        assertEquals(ContentPackRejection.ZIP_SLIP, ContentPackValidator.extract(zip, target))
        assertFalse(File(tempFolder.root, "evil.txt").exists())
    }

    @Test
    fun `предикат zip-slip ловит выход за корень`() {
        assertTrue(ContentPackValidator.isZipSlip("../evil.txt"))
        assertTrue(ContentPackValidator.isZipSlip("a/../../evil.txt"))
        assertTrue(ContentPackValidator.isZipSlip("/abs/evil.txt"))
        assertTrue(ContentPackValidator.isZipSlip("..\\evil.txt"))
        assertFalse(ContentPackValidator.isZipSlip("a/../b.txt"))
        assertFalse(ContentPackValidator.isZipSlip("teachers_photos/p1.jpg"))
    }

    @Test
    fun `неполный состав отклоняется`() {
        val dir = buildPackDir(tempFolder.root, includeMap = false)
        assertEquals(ContentPackRejection.COMPOSITION, ContentPackValidator.validateContent(dir))
    }

    @Test
    fun `менее 50 преподавателей отклоняется`() {
        val dir = buildPackDir(tempFolder.root, teachers = 49)
        assertEquals(ContentPackRejection.TEACHERS, ContentPackValidator.validateContent(dir))
        val exact = buildPackDir(File(tempFolder.root, "exact").apply { mkdirs() }, teachers = 50)
        assertEquals(ContentPackRejection.NONE, ContentPackValidator.validateContent(exact))
    }

    @Test
    fun `отсутствующее фото преподавателя отклоняется`() {
        val dir = buildPackDir(tempFolder.root, missingPhoto = "ghost.jpg")
        assertEquals(ContentPackRejection.PHOTOS, ContentPackValidator.validateContent(dir))
    }

    @Test
    fun `менее 20 групп отклоняется`() {
        val dir = buildPackDir(tempFolder.root, groups = 19)
        assertEquals(ContentPackRejection.GROUPS, ContentPackValidator.validateContent(dir))
    }

    @Test
    fun `непарсящаяся карта отклоняется`() {
        val dir = buildPackDir(tempFolder.root)
        assertEquals(ContentPackRejection.MAP, ContentPackValidator.validateContent(dir) { false })
    }

    @Test
    fun `битый json карты отклоняется реальным парсером`() {
        val dir = buildPackDir(tempFolder.root, mapJson = "{ не json")
        assertEquals(ContentPackRejection.MAP, ContentPackValidator.validateContent(dir))
        val empty = buildPackDir(File(tempFolder.root, "empty").apply { mkdirs() }, mapJson = "{\"floors\":{}}")
        assertEquals(ContentPackRejection.MAP, ContentPackValidator.validateContent(empty))
    }

    @Test
    fun `min_app_version выше текущего отклоняет пакет`() {
        assertFalse(ContentPackValidator.minAppVersionAccepted(201, 200))
        assertTrue(ContentPackValidator.minAppVersionAccepted(200, 200))
        assertTrue(ContentPackValidator.minAppVersionAccepted(0, 200))
    }

    @Test
    fun `даунгрейд и равная версия пакета не применяются`() {
        assertTrue(ContentPackValidator.shouldApply(3, 2))
        assertFalse(ContentPackValidator.shouldApply(2, 2))
        assertFalse(ContentPackValidator.shouldApply(1, 2))
        assertTrue(ContentPackValidator.shouldApply(1, 0))
    }

    @Test
    fun `превышение размера и расхождение declared size отклоняются`() {
        val file = File(tempFolder.root, "big.bin").apply { writeBytes(ByteArray(10)) }
        assertFalse(ContentPackValidator.sizeAccepted(file, 10L, maxBytes = 4L))
        assertTrue(ContentPackValidator.sizeAccepted(file, 10L, maxBytes = 25L))
        assertFalse(ContentPackValidator.sizeAccepted(file, 9L))
        assertTrue(ContentPackValidator.sizeAccepted(file, 0L))
        assertFalse(ContentPackValidator.sizeAccepted(File(tempFolder.root, "missing.bin"), 0L))
    }

    @Test
    fun `манифест парсится и требует sha256 url и положительную версию`() {
        val manifest =
            ContentPackValidator.parseManifest(
                """{"pack_version":4,"generated_at":"2026-10-20T10:00:00Z","url":"content-pack.zip","size_bytes":123,"sha256":"ab12","min_app_version":190}""",
            )
        assertEquals(4, manifest?.packVersion)
        assertEquals("content-pack.zip", manifest?.url)
        assertEquals(123L, manifest?.sizeBytes)
        assertEquals(190, manifest?.minAppVersion)

        assertNull(ContentPackValidator.parseManifest(null))
        assertNull(ContentPackValidator.parseManifest("не json"))
        assertNull(ContentPackValidator.parseManifest("""{"pack_version":0,"url":"a.zip","sha256":"ab"}"""))
        assertNull(ContentPackValidator.parseManifest("""{"pack_version":2,"url":"","sha256":"ab"}"""))
        assertNull(ContentPackValidator.parseManifest("""{"pack_version":2,"url":"a.zip","sha256":""}"""))
    }

    @Test
    fun `строки преподавателей извлекают имя и фото`() {
        val rows = ContentPackValidator.teacherRows("# комментарий\nИванов И.И.;Д;П;;;;photo1.jpg;;;\n\nПетров П.П.;Д;П;;;;;;;")
        assertEquals(listOf("Иванов И.И." to "photo1.jpg", "Петров П.П." to ""), rows)
    }

    private companion object {
        const val MAP_JSON = """{"floors":{"1":{"rooms":[{"id":"r1","number":"101","x":0.1,"y":0.2,"w":0.3,"h":0.4}]}}}"""
    }
}
