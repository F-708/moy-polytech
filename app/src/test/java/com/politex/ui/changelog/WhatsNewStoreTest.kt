package com.politex.ui.changelog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhatsNewStoreTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val v200 = 20000999
    private val v201 = 20001999
    private val v220 = 20200999

    @Before
    fun setUp() = WhatsNewStore.clear(context)

    @After
    fun tearDown() = WhatsNewStore.clear(context)

    @Test
    fun `после обновления с 2_0_0 до 2_0_1 возвращается запись с пунктами`() {
        WhatsNewStore.recordSeen(context, v200)
        val shown = WhatsNewStore.onLaunch(context, v201)
        assertEquals(1, shown.size)
        assertEquals(v201, shown.single().versionCode)
        assertEquals("2.0.1", shown.single().versionName)
        assertEquals(4, shown.single().items.size)
    }

    @Test
    fun `после обновления через несколько версий запись 2_0_1 всё равно показывается`() {
        WhatsNewStore.recordSeen(context, v200)
        val shown = WhatsNewStore.onLaunch(context, v220)
        assertEquals(listOf(v201), shown.map { it.versionCode })
    }

    @Test
    fun `повторный запуск после записи текущей версии ничего не показывает`() {
        WhatsNewStore.recordSeen(context, v200)
        assertTrue(WhatsNewStore.onLaunch(context, v201).isNotEmpty())
        WhatsNewStore.recordSeen(context, v201)
        assertTrue(WhatsNewStore.onLaunch(context, v201).isEmpty())
        assertTrue(WhatsNewStore.onLaunch(context, v220).isEmpty())
    }

    @Test
    fun `свежая установка не показывает диалог и сразу записывает версию`() {
        assertEquals(null, WhatsNewStore.lastSeen(context))
        val shown = WhatsNewStore.onLaunch(context, v220)
        assertTrue(shown.isEmpty())
        assertEquals(v220, WhatsNewStore.lastSeen(context))
    }

    @Test
    fun `даунгрейд не показывает диалог и не перезаписывает сохранённую версию`() {
        WhatsNewStore.recordSeen(context, v220)
        val shown = WhatsNewStore.onLaunch(context, v201)
        assertTrue(shown.isEmpty())
        assertEquals(v220, WhatsNewStore.lastSeen(context))
    }

    @Test
    fun `та же версия не показывает диалог`() {
        WhatsNewStore.recordSeen(context, v201)
        assertTrue(WhatsNewStore.onLaunch(context, v201).isEmpty())
        assertEquals(v201, WhatsNewStore.lastSeen(context))
    }

    @Test
    fun `превью-сборка не показывает что нового и молча записывает версию`() {
        val preview = 20300001
        WhatsNewStore.recordSeen(context, v220)
        assertTrue(WhatsNewStore.onLaunch(context, preview).isEmpty())
        assertEquals(preview, WhatsNewStore.lastSeen(context))
    }

    @Test
    fun `превью-сборка на свежей установке тоже молчит`() {
        val preview = 20300001
        assertTrue(WhatsNewStore.onLaunch(context, preview).isEmpty())
        assertEquals(preview, WhatsNewStore.lastSeen(context))
    }

    @Test
    fun `стабильная сборка той же линии после превью показывает только своё`() {
        val preview = 20300001
        val stable = 20300999
        WhatsNewStore.recordSeen(context, v220)
        assertTrue(WhatsNewStore.onLaunch(context, preview).isEmpty())
        // Записей для 2.3 ещё нет — окно не должно подтягивать чужие пункты.
        assertTrue(WhatsNewStore.onLaunch(context, stable).isEmpty())
        // Запись версии в stable-ветке делает MainActivity после показа окна.
        assertEquals(preview, WhatsNewStore.lastSeen(context))
    }

    @Test
    fun `признак стабильной сборки по последним трём цифрам кода`() {
        assertTrue(WhatsNewStore.isStableBuild(20200999))
        assertTrue(WhatsNewStore.isStableBuild(20300999))
        assertTrue(!WhatsNewStore.isStableBuild(20300001))
        assertTrue(!WhatsNewStore.isStableBuild(20300998))
    }
}
