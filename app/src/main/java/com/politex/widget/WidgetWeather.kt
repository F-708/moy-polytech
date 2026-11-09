package com.politex.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import com.politex.R
import com.politex.weather.EffectsMode
import com.politex.weather.Precipitation
import com.politex.weather.SkyMode
import com.politex.weather.WeatherHub
import com.politex.weather.WeatherStore
import com.politex.weather.resolveNight
import com.politex.weather.resolvePrecipitation
import kotlin.random.Random

/**
 * Погодная подложка виджета — статичная картинка под текстом.
 *
 * `RemoteViews` не умеют ни Canvas, ни нормальную анимацию, поэтому при каждом
 * обновлении виджета рисуем один Bitmap: дождь, снег или звёзды. Погода берётся
 * из прогноза автоматически, без настройки оформления.
 */
object WidgetWeather {

    private const val MAX_BITMAP_WIDTH_PX = 420
    private const val MAX_BITMAP_HEIGHT_PX = 240

    /** Последние картинки: минутные обновления виджетов не должны перерисовывать Canvas. */
    private const val MAX_CACHED_BACKDROPS = 4

    private val backdropCache = LinkedHashMap<String, Bitmap>(MAX_CACHED_BACKDROPS, 0.75f, true)

    private fun backdropKey(
        widthPx: Int,
        heightPx: Int,
        precipitation: Precipitation,
        night: Boolean,
        darkBackground: Boolean,
        seed: Long,
    ): String = "$widthPx:$heightPx:${precipitation.name}:$night:$darkBackground:$seed"

    /**
     * Ставит картинку погоды в [targetId] — `ImageView` поверх фона и под текстом.
     * Без осадков и ночи картинка прячется.
     */
    fun applyBackdrop(
        views: RemoteViews,
        context: Context,
        appWidgetId: Int,
        targetId: Int = R.id.iv_weather_backdrop,
        colors: WidgetColors? = null,
    ) {
        val snapshot = WeatherStore.readSnapshot(context)
        // Виджет не ждёт открытия приложения: если прогноза нет или он протух,
        // просим обновление сами. Сеть отсекается часовым кэшем в WeatherHub,
        // а после удачной загрузки он сам перерисует все виджеты.
        val fetchedAt = WeatherStore.readFetchedAt(context)
        if (snapshot == null || fetchedAt == null || System.currentTimeMillis() - fetchedAt > STALE_MS) {
            runCatching { WeatherHub.ensureFresh(context) }
        }
        if (snapshot == null) {
            views.setViewVisibility(targetId, View.GONE)
            return
        }
        val precipitation = resolvePrecipitation(EffectsMode.AUTO, snapshot)
        val night = resolveNight(SkyMode.AUTO, snapshot)
        if (precipitation == Precipitation.NONE && !night) {
            views.setViewVisibility(targetId, View.GONE)
            return
        }

        val (widthPx, heightPx) = widgetSizePx(context, appWidgetId)
        val palette = colors ?: WidgetStyle.load(context, appWidgetId).colors(context)
        val seed = appWidgetId.toLong() * 31L + 7L
        val bitmap =
            backdrop(
                widthPx = widthPx,
                heightPx = heightPx,
                precipitation = precipitation,
                night = night,
                darkBackground = isDark(palette.backgroundColor),
                seed = seed,
            )
        views.setViewVisibility(targetId, View.VISIBLE)
        views.setImageViewBitmap(targetId, bitmap)
    }

    /** Прогноз старше этого срока виджет обновляет сам, не дожидаясь приложения. */
    private const val STALE_MS = 15L * 60L * 1000L

    /** Размер виджета в пикселях, но не больше разумного для Bitmap в RemoteViews. */
    internal fun widgetSizePx(
        context: Context,
        appWidgetId: Int,
    ): Pair<Int, Int> {
        val options =
            runCatching {
                AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
            }.getOrNull()
        val density = context.resources.displayMetrics.density
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250) ?: 250
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) ?: 110
        val width = (widthDp * density).toInt().coerceIn(160, MAX_BITMAP_WIDTH_PX)
        val height = (heightDp * density).toInt().coerceIn(90, MAX_BITMAP_HEIGHT_PX)
        return width to height
    }

    /** Картинка сцены: одна и та же погода и размер дают один и тот же Bitmap. */
    @Synchronized
    private fun backdrop(
        widthPx: Int,
        heightPx: Int,
        precipitation: Precipitation,
        night: Boolean,
        darkBackground: Boolean,
        seed: Long,
    ): Bitmap {
        val key = backdropKey(widthPx, heightPx, precipitation, night, darkBackground, seed)
        backdropCache[key]?.let { return it }
        val bitmap =
            renderBackdrop(
                widthPx = widthPx,
                heightPx = heightPx,
                precipitation = precipitation,
                night = night,
                darkBackground = darkBackground,
                seed = seed,
            )
        if (backdropCache.size >= MAX_CACHED_BACKDROPS) {
            backdropCache.remove(backdropCache.keys.first())
        }
        backdropCache[key] = bitmap
        return bitmap
    }

    /** Один статичный кадр погоды для виджета. */
    internal fun renderBackdrop(
        widthPx: Int,
        heightPx: Int,
        precipitation: Precipitation,
        night: Boolean,
        darkBackground: Boolean,
        seed: Long,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Ночную «синеву» на виджете не заливаем: на маленьком Bitmap градиент
    // даёт кольца и грязь. Ночь здесь — звёзды в ясном небе, а не тонировка.
    when (precipitation) {
            Precipitation.RAIN -> drawRain(canvas, paint, widthPx, heightPx, darkBackground, Random(seed))
            Precipitation.SNOW -> drawSnow(canvas, paint, widthPx, heightPx, darkBackground, Random(seed + 1))
            Precipitation.NONE -> if (night) drawStars(canvas, paint, widthPx, heightPx, darkBackground, Random(seed + 2))
        }
        return bitmap
    }

    /** Вертикальные штрихи по всей ширине: наклон не рисуем, дождь падает ровно вниз. */
    private fun drawRain(
        canvas: Canvas,
        paint: Paint,
        widthPx: Int,
        heightPx: Int,
        darkBackground: Boolean,
        random: Random,
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.isDither = true
        paint.strokeWidth = (widthPx / 300f).coerceIn(1.5f, 2.6f)
        paint.color = if (darkBackground) 0xFFD6E6FA.toInt() else 0xFF3D628F.toInt()
        repeat(30) {
            val x = widthPx * random.nextFloat()
            val length = heightPx * (0.06f + random.nextFloat() * 0.10f)
            val y = random.nextFloat() * heightPx - length
            paint.alpha = 150 + random.nextInt(80)
            canvas.drawLine(x, y, x, y + length, paint)
        }
    }

    /** Снежинки — точки разного размера. */
    private fun drawSnow(
        canvas: Canvas,
        paint: Paint,
        widthPx: Int,
        heightPx: Int,
        darkBackground: Boolean,
        random: Random,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = if (darkBackground) 0xFFFFFFFF.toInt() else 0xFF4A7099.toInt()
        repeat(34) {
            val x = widthPx * random.nextFloat()
            val y = random.nextFloat() * heightPx
            val radius = (1.6f + random.nextFloat() * 2.0f) * (widthPx / 320f).coerceIn(0.9f, 1.8f)
            paint.alpha = 180 + random.nextInt(76)
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    /** Ясная ночь: несколько спокойных звёзд, без мерцания. */
    private fun drawStars(
        canvas: Canvas,
        paint: Paint,
        widthPx: Int,
        heightPx: Int,
        darkBackground: Boolean,
        random: Random,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = if (darkBackground) 0xFFFFF6E0.toInt() else 0xFF44506B.toInt()
        repeat(9) {
            val x = widthPx * (0.52f + random.nextFloat() * 0.45f)
            val y = random.nextFloat() * heightPx * 0.6f
            paint.alpha = 70 + random.nextInt(70)
            canvas.drawCircle(x, y, 0.8f + random.nextFloat() * 1.2f, paint)
        }
    }

    private fun isDark(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (0.299f * r + 0.587f * g + 0.114f * b) < 138f
    }
}