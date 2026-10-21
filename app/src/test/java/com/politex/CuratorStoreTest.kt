package com.politex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.CuratorStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CuratorStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        CuratorStore.clearAll(context)
    }

    @After
    fun tearDown() {
        CuratorStore.clearAll(context)
    }

    @Test
    fun `record и curatorFor — roundtrip с updatedAt`() {
        CuratorStore.record(mapOf("41О" to "Иванова А.Б."), now = 1_000L)
        assertEquals("Иванова А.Б.", CuratorStore.curatorFor("41О"))
        val entry = CuratorStore.entryFor("41О")
        assertNotNull(entry)
        assertEquals(1_000L, entry!!.updatedAt)
    }

    @Test
    fun `неизвестная группа — null`() {
        CuratorStore.record(mapOf("41О" to "Иванова А.Б."))
        assertNull(CuratorStore.curatorFor("21М"))
    }

    @Test
    fun `ключ группы нормализуется при записи и чтении`() {
        CuratorStore.record(mapOf("41 О" to "Петров П.П."))
        assertEquals("Петров П.П.", CuratorStore.curatorFor("41О"))
        assertEquals("Петров П.П.", CuratorStore.curatorFor("41 о"))
    }

    @Test
    fun `деградация — пустой record не трогает старое значение`() {
        CuratorStore.record(mapOf("21М" to "Сидоров С.С."), now = 1_000L)
        CuratorStore.record(emptyMap(), now = 2_000L)
        assertEquals("Сидоров С.С.", CuratorStore.curatorFor("21М"))
        assertEquals(1_000L, CuratorStore.entryFor("21М")!!.updatedAt)
    }

    @Test
    fun `документ без куратора одной группы не стирает её старое значение`() {
        CuratorStore.record(mapOf("21М" to "Сидоров С.С."), now = 1_000L)
        CuratorStore.record(mapOf("11А" to "Другой Д.Д."), now = 2_000L)
        assertEquals("Сидоров С.С.", CuratorStore.curatorFor("21М"))
        assertEquals("Другой Д.Д.", CuratorStore.curatorFor("11А"))
    }

    @Test
    fun `revision растёт при изменении и стоит при повторе того же куратора`() {
        val r0 = CuratorStore.revision.intValue
        CuratorStore.record(mapOf("41О" to "Иванова А.Б."), now = 1_000L)
        val r1 = CuratorStore.revision.intValue
        assertTrue(r1 > r0)

        CuratorStore.record(mapOf("41О" to "Иванова А.Б."), now = 2_000L)
        assertEquals(r1, CuratorStore.revision.intValue)

        CuratorStore.record(mapOf("41О" to "Петрова В.Г."), now = 3_000L)
        assertTrue(CuratorStore.revision.intValue > r1)
        assertEquals("Петрова В.Г.", CuratorStore.curatorFor("41О"))
    }

    @Test
    fun `персистентность — значение переживает сброс памяти и перечитывается из prefs`() {
        CuratorStore.record(mapOf("41О" to "Иванова А.Б."), now = 5_000L)
        val json = context
            .getSharedPreferences("mpk_curators", Context.MODE_PRIVATE)
            .getString("map", "") ?: ""
        assertTrue(json.contains("Иванова"))

        CuratorStore.resetInMemory()
        assertNull(CuratorStore.snapshot()["41О"])

        CuratorStore.attach(context)
        assertEquals("Иванова А.Б.", CuratorStore.curatorFor("41О"))
        assertEquals(5_000L, CuratorStore.entryFor("41О")!!.updatedAt)
    }

    @Test
    fun `сериализация roundtrip сохраняет teacher и updatedAt`() {
        val map = mapOf(
            "41О" to CuratorStore.Entry("Иванова А.Б.", 10L),
            "21М" to CuratorStore.Entry("Сидоров С.С.", 20L),
        )
        assertEquals(map, CuratorStore.parseMap(CuratorStore.serializeMap(map)))
    }

    @Test
    fun `parseMap устойчив к битым данным`() {
        assertEquals(emptyMap<String, CuratorStore.Entry>(), CuratorStore.parseMap(""))
        assertEquals(emptyMap<String, CuratorStore.Entry>(), CuratorStore.parseMap("not json {{{"))
        assertEquals(emptyMap<String, CuratorStore.Entry>(), CuratorStore.parseMap("{\"41О\":{\"t\":\"\"}}"))
        assertEquals(emptyMap<String, CuratorStore.Entry>(), CuratorStore.parseMap("{\"41О\":\"oops\"}"))
    }

    @Test
    fun `пустые teacher и group не сохраняются`() {
        CuratorStore.record(mapOf("41О" to "   ", "" to "Иванов И.И.", "21М" to "Сидоров С.С."))
        assertNull(CuratorStore.curatorFor("41О"))
        assertEquals("Сидоров С.С.", CuratorStore.curatorFor("21М"))
        assertEquals(1, CuratorStore.snapshot().size)
    }
}
