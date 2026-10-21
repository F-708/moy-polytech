package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.SubjectRenameStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubjectRenameStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        SubjectRenameStore.clearAll(context)
    }

    @After
    fun tearDown() {
        SubjectRenameStore.clearAll(context)
    }

    @Test
    fun `set-get-reset — полный цикл переименования`() {
        val original = "Системы технической организации сервиса"
        assertNull(SubjectRenameStore.customName(context, original))

        SubjectRenameStore.setCustomName(context, original, "СистТехОрг")
        assertEquals("СистТехОрг", SubjectRenameStore.customName(context, original))

        SubjectRenameStore.reset(context, original)
        assertNull(SubjectRenameStore.customName(context, original))
    }

    @Test
    fun `пустое имя трактуется как исходное`() {
        val original = "Математика"
        SubjectRenameStore.setCustomName(context, original, "   ")
        assertNull(SubjectRenameStore.customName(context, original))
        assertEquals("Математика", SubjectRenameStore.displayName(context, original))
    }

    @Test
    fun `имя равное исходному не сохраняется`() {
        val original = "Физика"
        SubjectRenameStore.setCustomName(context, original, "Физика")
        assertNull(SubjectRenameStore.customName(context, original))
    }

    @Test
    fun `roundtrip — сериализация и парсинг сохраняют map`() {
        val map =
            mapOf(
                "Системы технической организации сервиса" to "СистТехОрг",
                "Диагностика, техническое обслуживание и ремонт механических транспортных средств" to "Диагностика МТС",
                "Иностранный язык (профессиональная лексика)" to "Английский",
            )
        val json = SubjectRenameStore.serializeMap(map)
        assertEquals(map, SubjectRenameStore.parseMap(json))
    }

    @Test
    fun `parseMap устойчив к битым данным`() {
        assertEquals(emptyMap<String, String>(), SubjectRenameStore.parseMap(""))
        assertEquals(emptyMap<String, String>(), SubjectRenameStore.parseMap("not json {{{"))
        assertEquals(emptyMap<String, String>(), SubjectRenameStore.parseMap("{\"a\":\"\"}"))
    }

    @Test
    fun `displayName — со звёздой при переименовании, без — при оригинале`() {
        val original = "Охрана труда"
        assertEquals("Охрана труда", SubjectRenameStore.displayName(context, original))

        SubjectRenameStore.setCustomName(context, original, "ОТ")
        assertEquals("*ОТ", SubjectRenameStore.displayName(context, original))
        assertEquals("Другой предмет", SubjectRenameStore.displayName(context, "Другой предмет"))
    }

    @Test
    fun `cached-варианты читают in-memory map без контекста`() {
        val original = "Информатика"
        assertEquals("Информатика", SubjectRenameStore.displayNameCached(original))

        SubjectRenameStore.setCustomName(context, original, "Компы")
        assertEquals("Компы", SubjectRenameStore.customNameCached(original))
        assertEquals("*Компы", SubjectRenameStore.displayNameCached(original))

        SubjectRenameStore.reset(context, original)
        assertEquals("Информатика", SubjectRenameStore.displayNameCached(original))
    }
}
