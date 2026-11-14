package com.politex.weather

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.PI

/**
 * Астрономическая ночь в точке колледжа — без сети и зависимостей.
 *
 * Заказчик (2026-11-12): звёзды должны появляться, когда на улице реально темно,
 * а не по `is_day` из прогноза (прогноз может отсутствовать вовсе). Считаем
 * восход/заход упрощёнными формулами NOAA: точность около минуты, для «темно
 * или нет» этого с запасом.
 *
 * Чистая функция от абсолютного времени: Юлианская дата не знает часовых поясов,
 * долгота учтена в среднем солнечном полудне, поэтому TimeZone не нужен.
 */
object SolarNight {

    /** Координаты колледжа МГПК — те же, что в запросе прогноза. */
    const val LAT_DEG = 53.9239
    const val LON_DEG = 27.6033

    /** Официальный заход: верхний край диска за горизонтом с учётом рефракции. */
    private const val ALTITUDE_DEG = -0.833

    /** Солнце ниже горизонта в точке колледжа в момент [millis] (UTC-независимо). */
    fun isNight(millis: Long): Boolean {
        val jd = millis / 86400000.0 + 2440587.5
        val (rise, set) = riseSetJulian(jd)
        return jd < rise || jd > set
    }

    /**
     * Глубокая ночь: с момента захода прошло и до восхода осталось не меньше
     * [DEEP_NIGHT_MARGIN_MIN] минут.
     *
     * Заказчик (2026-11-13): тема звёзд — только глубокой ночью, а не с первой
     * минуты сумерек. Отступ ограничен третью длины ночи: летом ночь короче
     * четырёх часов, и при жёстком отступе звёзды не включались бы вовсе.
     */
    const val DEEP_NIGHT_MARGIN_MIN = 120.0

    fun isDeepNight(millis: Long): Boolean {
        val jd = millis / 86400000.0 + 2440587.5
        val (rise, set) = riseSetJulian(jd)
        return when {
            // Предрассветный кусок ночи: заход был вчера вечером.
            jd < rise -> deepSegment(jd, riseSetJulian(jd - 1.0).second, rise)
            // Вечерний кусок ночи: восход будет завтра утром.
            jd > set -> deepSegment(jd, set, riseSetJulian(jd + 1.0).first)
            else -> false
        }
    }

    /** [jd] внутри ночи [fromSet]..[toRise] дальше отступа от обоих краёв. */
    private fun deepSegment(
        jd: Double,
        fromSet: Double,
        toRise: Double,
    ): Boolean {
        val nightLen = toRise - fromSet
        val margin = minOf(DEEP_NIGHT_MARGIN_MIN / 1440.0, nightLen / 3.0).coerceAtLeast(0.0)
        return (jd - fromSet) >= margin && (toRise - jd) >= margin
    }

    /**
     * Юлианские даты восхода и захода для суток, содержащих [jd].
     *
     * Пара возвращается для тех же суток, что и вход: сравнение «jd вне
     * интервала» корректно и вблизи полуночи, потому что за пределами
     * полярных широт восход всегда раньше захода внутри суток.
     */
    internal fun riseSetJulian(jd: Double): Pair<Double, Double> {
        // Приближённый средний солнечный полдень (Wikipedia, sunrise equation):
        // долгота восточная, поэтому западная lw = -LON_DEG.
        val jStar = 2451545.0 + 0.0009 - LON_DEG / 360.0
        // Целый юлианский цикл: без округления «полдень» следовал бы за входным
        // временем, а не за сутками.
        val n = kotlin.math.round(jd - jStar)
        val meanAnomaly = degToRad((357.5291 + 0.98560028 * n) % 360.0)
        val equation =
            1.9148 * sin(meanAnomaly) +
                0.0200 * sin(2 * meanAnomaly) +
                0.0003 * sin(3 * meanAnomaly)
        val eclipticLongitude =
            degToRad((radToDeg(meanAnomaly) + equation + 180.0 + 102.9372) % 360.0)
        // Точный солнечный полдень в юлианской дате.
        val transit = jStar + n + 0.0053 * sin(meanAnomaly) - 0.0069 * sin(2 * eclipticLongitude)
        val declination = asin(sin(eclipticLongitude) * degToRad(23.4397))

        val lat = degToRad(LAT_DEG)
        val cosHour =
            (sin(degToRad(ALTITUDE_DEG)) - sin(lat) * sin(declination)) /
                (cos(lat) * cos(declination))
        // Полярные сутки: солнце не заходит / не восходит — ночи нет вовсе
        // либо ночь круглые сутки. Для широты колледжа недостижимо, но
        // функция обязана быть тотальной.
        if (cosHour >= 1.0) return Pair(transit, transit)
        if (cosHour <= -1.0) return Pair(transit - 0.5, transit + 0.5)
        val hourAngle = radToDeg(atan2(sqrt1m(cosHour * cosHour), cosHour)) / 360.0
        return Pair(transit - hourAngle, transit + hourAngle)
    }

    private fun sqrt1m(x: Double): Double = kotlin.math.sqrt((1.0 - x).coerceAtLeast(0.0))

    private fun degToRad(deg: Double): Double = deg * PI / 180.0
    private fun radToDeg(rad: Double): Double = rad * 180.0 / PI
}
