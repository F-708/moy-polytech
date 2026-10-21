package com.politex

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.politex.util.BellCountdownNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
class BellLineKeepAliveTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun drainServiceIntents(app: ShadowApplication) {
        app.clearStartedServices()
        while (app.nextStoppedService != null) {
        }
    }

    @Test
    fun `KK14 serviceActionFor - включённая строка никогда не даёт STOP`() {
        assertEquals(
            BellCountdownNotifier.ServiceAction.START,
            BellCountdownNotifier.serviceActionFor(enabled = true, running = false),
        )
        assertEquals(
            BellCountdownNotifier.ServiceAction.NONE,
            BellCountdownNotifier.serviceActionFor(enabled = true, running = true),
        )
        assertEquals(
            BellCountdownNotifier.ServiceAction.STOP,
            BellCountdownNotifier.serviceActionFor(enabled = false, running = true),
        )
        assertEquals(
            BellCountdownNotifier.ServiceAction.STOP,
            BellCountdownNotifier.serviceActionFor(enabled = false, running = false),
        )
    }

    @Test
    fun `KK14 смена стиля темы scope при включённой строке не стопит сервис и гарантирует рестарт`() {
        val app = shadowOf(context.applicationContext as Application)
        BellCountdownNotifier.setEnabled(context, true)
        drainServiceIntents(app)

        BellCountdownNotifier.setStyle(context, BellCountdownNotifier.Style.WITH_SECONDS)
        BellCountdownNotifier.setTheme(context, BellCountdownNotifier.Theme.APP)
        BellCountdownNotifier.setScope(context, BellCountdownNotifier.Scope.COLLEGE)

        assertNull(
            "смена настроек строки не должна останавливать сервис",
            app.nextStoppedService,
        )
        assertNotNull(
            "сервис в тесте физически не поднят - смена настроек обязана гарантированно перезапускать тик",
            app.nextStartedService,
        )
    }

    @Test
    fun `KK14 выключение строки по-прежнему останавливает сервис`() {
        val app = shadowOf(context.applicationContext as Application)
        BellCountdownNotifier.setEnabled(context, true)
        drainServiceIntents(app)

        BellCountdownNotifier.setEnabled(context, false)

        assertNotNull(
            "выключение строки должно останавливать сервис",
            app.nextStoppedService,
        )
        assertNull(
            "после выключения не должно быть повторного старта",
            app.nextStartedService,
        )
    }

    @Test
    fun `KK14 повторное включение после выключения снова поднимает сервис`() {
        val app = shadowOf(context.applicationContext as Application)
        BellCountdownNotifier.setEnabled(context, false)
        drainServiceIntents(app)

        BellCountdownNotifier.setEnabled(context, true)

        assertNotNull(
            "включение строки должно стартовать сервис",
            app.nextStartedService,
        )
        assertNull(
            "включение не должно останавливать сервис",
            app.nextStoppedService,
        )
    }
}
