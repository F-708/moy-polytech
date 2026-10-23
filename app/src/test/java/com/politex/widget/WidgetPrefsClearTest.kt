package com.politex.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 31, 33, 35])
class WidgetPrefsClearTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `clear удаляет все четыре ключа виджета и щадит остальные`() {
        val id = 42
        val prefs = WidgetUpdateHelper.getPrefs(ctx)
        prefs
            .edit()
            .putString("widget_style_$id", "DARK")
            .putString("countdown_mode_$id", "ALL_BELLS")
            .putBoolean("widget_show_room_$id", false)
            .putBoolean("widget_seconds_$id", true)
            .putString("widget_style_99", "LIGHT")
            .putString("selected_group", "41О")
            .apply()

        WidgetPrefs.clear(ctx, id)

        assertFalse("widget_style_$id", prefs.contains("widget_style_$id"))
        assertFalse("countdown_mode_$id", prefs.contains("countdown_mode_$id"))
        assertFalse("widget_show_room_$id", prefs.contains("widget_show_room_$id"))
        assertFalse("widget_seconds_$id", prefs.contains("widget_seconds_$id"))
        assertTrue("чужой id не трогаем", prefs.contains("widget_style_99"))
        assertTrue("несвязанный ключ не трогаем", prefs.contains("selected_group"))
    }
}
