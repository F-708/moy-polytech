package com.politex.weather

import android.graphics.Bitmap
import android.graphics.Color
import com.politex.widget.WidgetWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Статичная погодная картинка виджета: Canvas → Bitmap.
 *
 * Проверяем то, что реально уходит в RemoteViews: размер, наличие мотива и
 * прозрачность в ясный день (лишний Bitmap не должен висеть в памяти).
 * Скриншоты кладутся в `build/screenshots/widget-weather-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WidgetWeatherBackdropTest {

    private val outDir = File("build/screenshots")

    private fun render(
        name: String,
        precipitation: Precipitation,
        night: Boolean,
        darkBackground: Boolean,
    ): Bitmap {
        val bitmap =
            WidgetWeather.renderBackdrop(
                widthPx = 360,
                heightPx = 180,
                precipitation = precipitation,
                night = night,
                darkBackground = darkBackground,
                seed = 42L,
            )
        outDir.mkdirs()
        FileOutputStream(File(outDir, "widget-weather-$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    private fun opaquePixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) > 8) count++
            }
        }
        return count
    }

    @Test
    fun `дождь рисует капли на размере виджета`() {
        val bitmap = render("rain-light", Precipitation.RAIN, night = false, darkBackground = false)
        assertEquals(360, bitmap.width)
        assertEquals(180, bitmap.height)
        assertTrue("капель не видно", opaquePixels(bitmap) > 200)
    }

    @Test
    fun `снег рисует точки на тёмном фоне`() {
        val bitmap = render("snow-dark", Precipitation.SNOW, night = false, darkBackground = true)
        assertTrue("снежинок не видно", opaquePixels(bitmap) > 300)
    }

    @Test
    fun `ясная ночь рисует звёзды в углу`() {
        val bitmap = render("night-dark", Precipitation.NONE, night = true, darkBackground = true)
        assertTrue("звёзд не видно", opaquePixels(bitmap) > 20)
    }

    @Test
    fun `ясный день не тратит Bitmap`() {
        val bitmap = render("clear-day", Precipitation.NONE, night = false, darkBackground = false)
        assertEquals("картинка должна быть полностью прозрачной", 0, opaquePixels(bitmap))
    }
}