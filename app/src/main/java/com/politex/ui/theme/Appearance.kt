package com.politex.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Режим оформления приложения.
 *
 * Хранится в SharedPreferences по [id] — стабильной строке, а не по [name]:
 * так R8 не может сломать сохранённые настройки пользователя.
 *
 * [SYSTEM] — «как в системе», дефолт для новых пользователей: приложение
 * следует системной тёмной теме без перезапуска.
 */
enum class ThemeMode(
    val id: String,
    val title: String,
) {
    SYSTEM("system", "Системная"),
    LIGHT("light", "Светлая"),
    DARK("dark", "Тёмная"),
    ;

    companion object {
        fun from(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * «Характер» поверхностей: единственная ось кастомизации сверх режима.
 *
 * [NATIVE] — как было (дефолт, привычный вид не меняется ни у кого);
 * [SOFT] — мягче и светлее: в светлой теме фон тонируется, в тёмной — «светло-тёмная»;
 * [DEEP] — контрастнее: в светлой теме сильнее границы и второстепенный текст,
 * в тёмной — чистый чёрный фон (AMOLED).
 */
enum class SurfaceTone(
    val id: String,
    val title: String,
) {
    NATIVE("native", "Обычный"),
    SOFT("soft", "Мягкий"),
    DEEP("deep", "Глубокий"),
    ;

    companion object {
        fun from(id: String?): SurfaceTone = entries.firstOrNull { it.id == id } ?: NATIVE
    }
}

/** Текущая настройка внешнего вида целиком (без учёта системной темы — она приходит снаружи). */
data class AppearanceConfig(
    val theme: AppTheme = AppTheme.COLLEGE,
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val tone: SurfaceTone = SurfaceTone.NATIVE,
)

/** Порог WCAG AA для обычного текста. */
const val AA_TEXT_CONTRAST: Double = 4.5

/** Тот же порог с запасом: используется при вычислении производных палитр, чтобы не жить на грани. */
internal const val AA_TEXT_SAFE: Double = 4.6

/** Порог WCAG AA для крупного текста и графических элементов (иконки, границы). */
const val AA_LARGE_CONTRAST: Double = 3.0

/** Эффективная тьма: ручной выбор важнее системного. */
fun effectiveDark(
    mode: ThemeMode,
    systemDark: Boolean,
): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Единственная точка сборки палитры: пресет × режим × тон.
 *
 * Чистая функция — можно звать из виджетов и тестов, не трогая глобальное состояние.
 * Для [SurfaceTone.NATIVE] возвращается ровно пресетная палитра (байт-в-байт),
 * поэтому привычный вид при дефолтных настройках не меняется.
 */
fun resolvePalette(
    theme: AppTheme,
    mode: ThemeMode,
    tone: SurfaceTone,
    systemDark: Boolean,
): MpkPalette {
    val dark = effectiveDark(mode, systemDark)
    val base = if (dark) theme.darkPalette else theme.palette
    return base.withTone(tone, dark)
}

/**
 * Производная палитра под выбранный «характер».
 *
 * Никаких 8×N ручных палитр: меняются только поверхности, границы и второстепенный
 * текст, акцентные цвета (8 пресетов) остаются нетронутыми. Все пары текст/фон
 * прогоняются через [ensureContrast], поэтому гейт контраста проходит по построению.
 */
internal fun MpkPalette.withTone(
    tone: SurfaceTone,
    dark: Boolean,
): MpkPalette = when (tone) {
    SurfaceTone.NATIVE -> this
    SurfaceTone.SOFT -> if (dark) softDark() else softLight()
    SurfaceTone.DEEP -> if (dark) deepDark() else deepLight()
}

private fun MpkPalette.softLight(): MpkPalette {
    val bg = mix(bgMain, brandBlue, 0.04f)
    val surface = mix(surfaceVariant, brandBlue, 0.03f)
    val highlight = mix(surfaceHighlight, brandBlue, 0.04f)
    return copy(
        bgMain = bg,
        surfaceVariant = surface,
        surfaceHighlight = highlight,
        borderLight = mix(borderLight, bg, 0.25f),
        dividerLight = mix(dividerLight, bg, 0.25f),
        textMuted = ensureContrast(textMuted, bg, AA_TEXT_SAFE, dark = false),
        textWeekdays = ensureContrast(textWeekdays, bg, AA_TEXT_SAFE, dark = false),
        errorText = ensureContrast(errorText, bg, AA_TEXT_SAFE, dark = false),
        navSelected = ensureContrast(navSelected, highlight, AA_TEXT_SAFE, dark = false),
    )
}

private fun MpkPalette.softDark(): MpkPalette {
    val bg = mix(bgMain, Color.White, 0.055f)
    val surface = mix(surfaceVariant, Color.White, 0.09f)
    val highlight = mix(surfaceHighlight, Color.White, 0.10f)
    return copy(
        bgMain = bg,
        surfaceVariant = surface,
        surfaceHighlight = highlight,
        mapRouteCore = bg,
        textMuted = ensureContrast(textMuted, bg, AA_TEXT_SAFE, dark = true),
        textWeekdays = ensureContrast(textWeekdays, bg, AA_TEXT_SAFE, dark = true),
        errorText = ensureContrast(errorText, bg, AA_TEXT_SAFE, dark = true),
        navSelected = ensureContrast(navSelected, highlight, AA_TEXT_SAFE, dark = true),
    )
}

private fun MpkPalette.deepLight(): MpkPalette {
    val surface = mix(surfaceVariant, textBody, 0.05f)
    val highlight = mix(surfaceHighlight, textBody, 0.06f)
    return copy(
        surfaceVariant = surface,
        surfaceHighlight = highlight,
        borderLight = mix(borderLight, textBody, 0.55f),
        dividerLight = mix(dividerLight, textBody, 0.40f),
        textMuted = ensureContrast(textMuted, bgMain, 5.0, dark = false),
        textWeekdays = ensureContrast(textWeekdays, bgMain, AA_TEXT_SAFE, dark = false),
        textDisabled = mix(textDisabled, textBody, 0.30f),
        errorText = ensureContrast(errorText, bgMain, AA_TEXT_SAFE, dark = false),
        navSelected = ensureContrast(navSelected, highlight, AA_TEXT_SAFE, dark = false),
    )
}

private fun MpkPalette.deepDark(): MpkPalette {
    val highlight = Color(0xFF121519)
    return copy(
        bgMain = Color.Black,
        surfaceVariant = Color(0xFF0A0C0F),
        surfaceHighlight = highlight,
        menuBg = Color.Black,
        menuSubBg = Color.Black,
        mapRouteCore = Color.Black,
        borderLight = mix(borderLight, Color.White, 0.22f),
        dividerLight = mix(dividerLight, Color.White, 0.16f),
        textMuted = ensureContrast(textMuted, Color.Black, AA_TEXT_SAFE, dark = true),
        textWeekdays = ensureContrast(textWeekdays, Color.Black, AA_TEXT_SAFE, dark = true),
        errorText = ensureContrast(errorText, Color.Black, AA_TEXT_SAFE, dark = true),
        navSelected = ensureContrast(navSelected, highlight, AA_TEXT_SAFE, dark = true),
    )
}

/** Линейное смешение двух цветов в sRGB: [t] = 0 → [a], 1 → [b]. */
internal fun mix(
    a: Color,
    b: Color,
    t: Float,
): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k,
    )
}

/** Относительная яркость по WCAG 2.x. */
internal fun relativeLuminance(color: Color): Double {
    fun channel(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** Коэффициент контраста по WCAG 2.x (1.0 … 21.0). */
internal fun contrastRatio(
    a: Color,
    b: Color,
): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * Детерминированно доводит [fg] до контраста [min] на фоне [bg]:
 * на светлом фоне затемняет, на тёмном — осветляет, шагами по 6 % (не более 10 шагов).
 *
 * Если исходный цвет уже проходит — возвращается он же (важно для дефолтных палитр:
 * контрастные пары не должны меняться от одного факта вызова).
 */
internal fun ensureContrast(
    fg: Color,
    bg: Color,
    min: Double,
    dark: Boolean,
): Color {
    if (contrastRatio(fg, bg) >= min) return fg
    val target = if (dark) Color.White else Color.Black
    var result = fg
    repeat(10) {
        result = mix(result, target, 0.06f)
        if (contrastRatio(result, bg) >= min) return result
    }
    return result
}

/** Светлый ли цвет — для выбора тёмных/светлых иконок системных баров. */
fun isLightColor(color: Color): Boolean = relativeLuminance(color) > 0.5