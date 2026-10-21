package com.politex

import com.politex.data.repository.Teacher
import com.politex.data.repository.TeachersRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TeachersCsvParseTest {
    private val teachers: List<Teacher> =
        TeachersRepository(RuntimeEnvironment.getApplication()).loadTeachers()

    private fun parseOne(line: String): Teacher {
        val p = line.split(';')

        fun cell(i: Int): String {
            val v = p.getOrElse(i) { "" }.trim()
            return if (v.isEmpty() || v == "—" || v == "–" || v == "-") "" else v
        }
        return Teacher(
            name = cell(0),
            position = cell(1),
            subjects = cell(2),
            room = cell(3),
            phone = cell(4),
            email = cell(5),
            photo = cell(6),
            department = cell(7),
            experience = cell(8),
            category = cell(9),
        )
    }

    @Test
    fun `ячейка прочерк становится пустой в room phone email`() {
        val t = parseOne("Иванов И.И.;Преподаватель;Дисциплины;—;—;—;;;ЦК;5 лет;высшая")
        assertEquals("", t.room)
        assertEquals("", t.phone)
        assertEquals("", t.email)
    }

    @Test
    fun `телефон с дефисом внутри сохраняется как есть`() {
        val t = parseOne("Шатько А.В.;Директор;Управление;229;338-33-42;a@b.by;;;15 лет;Высшая")
        assertEquals("338-33-42", t.phone)
        assertEquals("229", t.room)
        assertEquals("a@b.by", t.email)
    }

    @Test
    fun `прочерк в реальном CSV не доходит до room phone email`() {
        assertTrue(teachers.isNotEmpty())
        for (t in teachers) {
            assertFalse("room содержит прочерк у ${t.name}", t.room == "—")
            assertFalse("phone содержит прочерк у ${t.name}", t.phone == "—")
            assertFalse("email содержит прочерк у ${t.name}", t.email == "—")
        }
    }

    @Test
    fun `все строки реального CSV имеют 10 полей`() {
        val lines =
            RuntimeEnvironment
                .getApplication()
                .assets
                .open("teachers.csv")
                .bufferedReader(Charsets.UTF_8)
                .readLines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.isNotEmpty())
        for (l in lines) {
            assertEquals("строка не из 10 полей: $l", 10, l.split(';').size)
        }
    }

    @Test
    fun `синхронизация 2026-10-21 каталог 106 записей, ключевые строки корректны`() {
        assertEquals(106, teachers.size)
        assertEquals(106, teachers.map { it.name }.toSet().size)

        val bachilo = teachers.first { it.name.startsWith("Бачило") }
        assertEquals("Преподаватель", bachilo.position)
        assertEquals("ЦК монтажа и эксплуатации электрооборудования", bachilo.department)
        assertEquals("259", bachilo.room)

        val shatko = teachers.first { it.name.startsWith("Шатько") }
        assertEquals("229", shatko.room)
        assertEquals("338-33-42", shatko.phone)
        assertEquals("Директор колледжа", shatko.position)

        val dubrovsky = teachers.first { it.name.startsWith("Дубровский") }
        assertEquals("Дубровский Дмитрий Викторович", dubrovsky.name)
        assertEquals("Преподаватель физической культуры и здоровья", dubrovsky.position)

        assertEquals(80, teachers.count { it.room.isNotBlank() })
        assertEquals(12, teachers.count { it.phone.isNotBlank() })
        assertEquals(9, teachers.count { it.email.isNotBlank() })
        assertEquals(100, teachers.count { it.experience.isNotBlank() })
        assertEquals(96, teachers.count { it.category.isNotBlank() })
        assertEquals(105, teachers.count { it.subjects.isNotBlank() })
    }

    @Test
    fun `стаж и категория не наследуются при молчании источника`() {
        val noExperience =
            setOf(
                "Дубровский Дмитрий Викторович",
                "Ефимчик Ольга Владимировна",
                "Крамаренко Александр Андреевич",
                "Лынько Андрей Сергеевич",
                "Руммо Артём Дмитриевич",
                "Якубецкая Елена Викторовна",
            )
        val noCategory =
            setOf(
                "Германович Елена Аркадьевна",
                "Гуд Владимир Владимирович",
                "Дубровский Дмитрий Викторович",
                "Ефимчик Ольга Владимировна",
                "Карпеев Николай Васильевич",
                "Костюк Алексей Николаевич",
                "Крамаренко Александр Андреевич",
                "Лынько Андрей Сергеевич",
                "Рослик Егор Дмитриевич",
                "Руммо Артём Дмитриевич",
            )
        assertEquals(noExperience, teachers.filter { it.experience.isBlank() }.map { it.name }.toSet())
        assertEquals(noCategory, teachers.filter { it.category.isBlank() }.map { it.name }.toSet())
    }

    @Test
    fun `должности без просторечий и скобочных пометок`() {
        val forbidden =
            listOf(
                "физрук", "физра", "трудовик", "завуч", "училка", "директрис",
                "математичк", "историчк", "англичанк", "химичк", "биологичк",
            )
        for (t in teachers) {
            assertTrue("пустая должность у ${t.name}", t.position.isNotBlank())
            val lower = t.position.lowercase()
            for (word in forbidden) {
                assertFalse("просторечие «$word» в должности ${t.position} у ${t.name}", lower.contains(word))
            }
            assertFalse("скобочная пометка в должности ${t.position} у ${t.name}", t.position.contains("(") || t.position.contains(")"))
        }
    }

    @Test
    fun `каждая запись имеет фото, файлы в ассетах совпадают с CSV`() {
        val assets = RuntimeEnvironment.getApplication().assets
        val photos = assets.list("teachers_photos")!!.toSet()
        assertEquals(106, photos.size)
        for (t in teachers) {
            assertTrue("пустая ссылка на фото у ${t.name}", t.photo.isNotBlank())
            assertTrue("нет фото ${t.photo} у ${t.name}", t.photo in photos)
        }
        assertEquals(photos, teachers.map { it.photo }.toSet())
    }

    @Test
    fun `фамилии не теряются нормализацией`() {
        assertTrue(teachers.all { it.name.isNotBlank() })
        assertTrue(teachers.any { it.name.startsWith("Шатько") })
    }
}
