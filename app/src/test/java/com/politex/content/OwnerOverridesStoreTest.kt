package com.politex.content

import androidx.test.core.app.ApplicationProvider
import com.politex.data.repository.Teacher
import com.politex.data.repository.TeachersRepository
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class OwnerOverridesStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        ContentPackStore.attach(context)
        OwnerOverridesStore.clearAll(context)
    }

    @After
    fun tearDown() {
        OwnerOverridesStore.clearAll(context)
        ContentPackStore.reset(context)
        TeachersRepository(context).invalidateCache()
    }

    private fun activatePack(
        version: Int,
        files: Map<String, String>,
    ) {
        val dir = ContentPackStore.packDir(context, version).apply { mkdirs() }
        files.forEach { (name, content) ->
            val file = File(dir, name)
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
        }
        ContentPackStore.activate(context, version, "2026-10-01T00:00:00Z", 0)
    }

    private fun photoSource(
        name: String,
        bytes: String,
    ): File = File(context.cacheDir, name).apply { writeText(bytes) }

    @Test
    fun `keyOf нормализует регистр ё и пробелы`() {
        assertEquals("елкина елена", OwnerOverridesStore.keyOf("  Ёлкина   ЕЛЕНА "))
    }

    @Test
    fun `upsert сохраняет запись и читается обратно`() {
        val record =
            OwnerOverridesStore.upsertTeacher(
                context = context,
                key = OwnerOverridesStore.keyOf("Иванов Иван Иванович"),
                fio = "Иванов Иван Иванович",
                position = "Преподаватель",
                phone = "123",
                subjects = "Математика",
                experience = "10 лет",
                category = "Высшая",
            )
        assertNotNull(record)
        assertEquals(1, record?.version)
        val all = OwnerOverridesStore.listTeachers(context)
        assertEquals(1, all.size)
        assertEquals("Иванов Иван Иванович", all[0].fio)
        assertEquals("Преподаватель", all[0].position)
        assertEquals("123", all[0].phone)
        assertEquals("Математика", all[0].subjects)
        assertEquals("10 лет", all[0].experience)
        assertEquals("Высшая", all[0].category)
        assertNull(all[0].photoName)
        assertEquals(all[0], OwnerOverridesStore.teacher(context, all[0].key))
    }

    @Test
    fun `повторный upsert поднимает версию записи`() {
        val key = OwnerOverridesStore.keyOf("Петрова Анна")
        OwnerOverridesStore.upsertTeacher(context, key, "Петрова Анна")
        val second = OwnerOverridesStore.upsertTeacher(context, key, "Петрова Анна Павловна")
        assertEquals(2, second?.version)
        assertEquals(1, OwnerOverridesStore.listTeachers(context).size)
        assertEquals("Петрова Анна Павловна", OwnerOverridesStore.teacher(context, key)?.fio)
    }

    @Test
    fun `upsert с пустым ключом или фио отклоняется`() {
        assertNull(OwnerOverridesStore.upsertTeacher(context, "", "Иванов"))
        assertNull(OwnerOverridesStore.upsertTeacher(context, "иванов", "   "))
        assertTrue(OwnerOverridesStore.listTeachers(context).isEmpty())
    }

    @Test
    fun `delete удаляет запись и её фото`() {
        val key = OwnerOverridesStore.keyOf("Сидоров Сидор")
        val src = photoSource("src1.jpg", "PHOTO")
        OwnerOverridesStore.upsertTeacher(context, key, "Сидоров Сидор", photoSource = src)
        val photo = OwnerOverridesStore.photoFile(context, key)
        assertNotNull(photo)
        assertTrue(OwnerOverridesStore.deleteTeacher(context, key))
        assertTrue(OwnerOverridesStore.listTeachers(context).isEmpty())
        assertNull(OwnerOverridesStore.photoFile(context, key))
        assertFalse(photo!!.exists())
        assertFalse(OwnerOverridesStore.deleteTeacher(context, key))
    }

    @Test
    fun `запись атомарна и не оставляет временных файлов`() {
        OwnerOverridesStore.upsertTeacher(context, "сидоров сидор", "Сидоров Сидор")
        val dir = OwnerOverridesStore.dir(context)
        val manifest = OwnerOverridesStore.manifestFile(context)
        assertTrue(manifest.isFile)
        assertFalse(File(dir, "manifest.json.tmp").exists())
        val root = JSONObject(manifest.readText(Charsets.UTF_8))
        assertEquals(1, root.optInt("format"))
        assertTrue(root.optJSONObject("teachers")?.has("сидоров сидор") == true)
    }

    @Test
    fun `битый манифест даёт пустой список и восстанавливается записью`() {
        val manifest = OwnerOverridesStore.manifestFile(context)
        manifest.parentFile?.mkdirs()
        manifest.writeText("{сломанный json", Charsets.UTF_8)
        OwnerOverridesStore.invalidateCache()
        assertTrue(OwnerOverridesStore.listTeachers(context).isEmpty())
        assertNull(OwnerOverridesStore.teacher(context, "иванов"))
        assertNotNull(OwnerOverridesStore.upsertTeacher(context, "иванов иван", "Иванов Иван"))
        assertEquals(1, OwnerOverridesStore.listTeachers(context).size)
        val root = JSONObject(manifest.readText(Charsets.UTF_8))
        assertEquals(1, root.optInt("format"))
    }

    @Test
    fun `фото копируется в хранилище и старая версия удаляется`() {
        val key = OwnerOverridesStore.keyOf("Кузнецова Мария")
        val first = photoSource("p_first.jpg", "FIRST")
        val second = photoSource("p_second.jpg", "SECOND")
        OwnerOverridesStore.upsertTeacher(context, key, "Кузнецова Мария", photoSource = first)
        val photo1 = OwnerOverridesStore.photoFile(context, key)
        assertNotNull(photo1)
        assertEquals("FIRST", photo1!!.readText())
        assertTrue(photo1.absolutePath.startsWith(OwnerOverridesStore.photosDir(context).absolutePath))

        val v2 = OwnerOverridesStore.upsertTeacher(context, key, "Кузнецова Мария", photoSource = second)
        val photo2 = OwnerOverridesStore.photoFile(context, key)
        assertNotNull(photo2)
        assertEquals("SECOND", photo2!!.readText())
        assertNotEquals(photo1.absolutePath, photo2.absolutePath)
        assertEquals(v2?.photoName, photo2.name)
        assertFalse(photo1.exists())
        assertEquals(1, OwnerOverridesStore.photosDir(context).listFiles()?.size)
    }

    @Test
    fun `upsert без нового фото сохраняет прежнее`() {
        val key = OwnerOverridesStore.keyOf("Кузнецова Мария")
        val first = photoSource("p_keep.jpg", "KEEP")
        OwnerOverridesStore.upsertTeacher(context, key, "Кузнецова Мария", photoSource = first)
        val v2 = OwnerOverridesStore.upsertTeacher(context, key, "Кузнецова Мария П", position = "Зав")
        assertNotNull(v2?.photoName)
        assertEquals("KEEP", OwnerOverridesStore.photoFile(context, key)?.readText())
    }

    @Test
    fun `applyTeacherOverrides сливает поля и добавляет отсутствующих`() {
        val key = OwnerOverridesStore.keyOf("Иванов Иван")
        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = key,
            fio = "Иванов Иван Иванович",
            position = "Старший преподаватель",
            phone = "999",
        )
        OwnerOverridesStore.upsertTeacher(context, "новый н н", "Новый Новый Нович")
        val base =
            listOf(
                Teacher(name = "Иванов Иван", position = "Преподаватель", phone = "000", room = "12"),
                Teacher(name = "Яшина Яна"),
            )
        val merged = OwnerOverridesStore.applyTeacherOverrides(context, base)
        assertEquals(3, merged.size)
        val ivanov = merged.first { it.name.startsWith("Иванов") }
        assertEquals("Иванов Иван Иванович", ivanov.name)
        assertEquals("Старший преподаватель", ivanov.position)
        assertEquals("999", ivanov.phone)
        assertEquals("12", ivanov.room)
        assertTrue(merged.any { it.name == "Новый Новый Нович" })
        assertTrue(merged.any { it.name == "Яшина Яна" })
        assertEquals(merged.map { it.name }.sortedBy { it }, merged.map { it.name })
    }

    @Test
    fun `applyTeacherOverrides без правок возвращает исходный список`() {
        val base = listOf(Teacher(name = "Иванов Иван"))
        assertEquals(base, OwnerOverridesStore.applyTeacherOverrides(context, base))
    }

    @Test
    fun `приоритет фото override выше пакета и assets`() {
        activatePack(7, mapOf("teachers_photos/p1.jpg" to "PACK_PHOTO"))
        assertEquals(
            "file://" + File(ContentPackStore.packDir(context, 7), "teachers_photos/p1.jpg").absolutePath,
            ContentAssets.teacherPhotoModel(context, "Петрова Анна", "p1.jpg"),
        )
        val src = photoSource("p_own.jpg", "OWN")
        OwnerOverridesStore.upsertTeacher(context, "петрова анна", "Петрова Анна", photoSource = src)
        val model = ContentAssets.teacherPhotoModel(context, "Петрова Анна", "p1.jpg")
        assertNotNull(model)
        assertTrue(model!!.startsWith("file://" + OwnerOverridesStore.photosDir(context).absolutePath))

        OwnerOverridesStore.deleteTeacher(context, "петрова анна")
        assertTrue(
            ContentAssets.teacherPhotoModel(context, "Петрова Анна", "p1.jpg")!!
                .contains("/content/7/".replace('/', File.separatorChar)),
        )
        assertNull(ContentAssets.teacherPhotoModel(context, "Петрова Анна", ""))
    }

    @Test
    fun `override фото находится и после смены фио`() {
        val src = photoSource("p_renamed.jpg", "REN")
        OwnerOverridesStore.upsertTeacher(context, "петрова анна", "Смирнова Анна", photoSource = src)
        val model = ContentAssets.teacherPhotoModel(context, "Смирнова Анна", "")
        assertNotNull(model)
        assertTrue(model!!.contains(OwnerOverridesStore.photosDir(context).absolutePath))
    }

    @Test
    fun `приоритет данных override выше пакета`() {
        activatePack(
            7,
            mapOf(
                "teachers.csv" to "Пакетов Пакет;Должность пакета;Предмет;;; ;;;;",
                "group_codes.csv" to "x",
                "college_map.json" to "{}",
            ),
        )
        val repo = TeachersRepository(context)
        repo.invalidateCache()
        assertTrue(repo.loadTeachers().any { it.name == "Пакетов Пакет" })

        OwnerOverridesStore.upsertTeacher(
            context = context,
            key = OwnerOverridesStore.keyOf("Пакетов Пакет"),
            fio = "Пакетов Пакет Отредактирован",
            position = "Моя должность",
        )
        repo.invalidateCache()
        val merged = repo.loadTeachers()
        val t = merged.first { it.position == "Моя должность" }
        assertEquals("Пакетов Пакет Отредактирован", t.name)
        assertFalse(merged.any { it.name == "Пакетов Пакет" })

        OwnerOverridesStore.deleteTeacher(context, OwnerOverridesStore.keyOf("Пакетов Пакет"))
        repo.invalidateCache()
        assertTrue(repo.loadTeachers().any { it.name == "Пакетов Пакет" })
    }

    @Test
    fun `revision растёт при записи и удалении`() {
        val before = OwnerOverridesStore.revision.intValue
        OwnerOverridesStore.upsertTeacher(context, "иванов иван", "Иванов Иван")
        val afterUpsert = OwnerOverridesStore.revision.intValue
        assertTrue(afterUpsert > before)
        OwnerOverridesStore.deleteTeacher(context, "иванов иван")
        assertTrue(OwnerOverridesStore.revision.intValue > afterUpsert)
    }

    @Test
    fun `refreshDependentCaches не падает без инициализации графа`() {
        OwnerOverridesStore.refreshDependentCaches(context)
    }
}
