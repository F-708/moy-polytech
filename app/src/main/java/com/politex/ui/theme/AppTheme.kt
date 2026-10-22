package com.politex.ui.theme

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

enum class AppTheme(
    val id: String,
    val title: String,
    val hint: String,
    val palette: MpkPalette,
    val darkPalette: MpkPalette,
) {
    COLLEGE(
        "college",
        "Фирменная",
        "Синие цвета колледжа — как на сайте",
        LightPalette,
        DarkPalette,
    ),
    EMERALD(
        "emerald",
        "Изумрудная",
        "Тёмно-зелёный акцент вместо синего",
        paletteWithAccents(
            topBar = Color(0xFF03291A),
            brand = Color(0xFF0B5D3B),
            active = Color(0xFF0E8A55),
        ),
        darkPaletteWithAccents(
            topBar = Color(0xFF26473A),
            brand = Color(0xFF689B85),
            active = Color(0xFF3EA177),
            brandFill = Color(0xFF1A6747),
            activeFill = Color(0xFF0D7F4E),
        ),
    ),
    BORDEAUX(
        "bordeaux",
        "Бордовая",
        "Тёмно-красный акцент",
        paletteWithAccents(
            topBar = Color(0xFF2B0A10),
            brand = Color(0xFF7B1225),
            active = Color(0xFFA81B33),
        ),
        darkPaletteWithAccents(
            topBar = Color(0xFF492C31),
            brand = Color(0xFFB87F89),
            active = Color(0xFFCB7685),
            brandFill = Color(0xFF933D4C),
            activeFill = Color(0xFFAB243B),
        ),
    ),
    GRAPHITE(
        "graphite",
        "Графитовая",
        "Спокойная серая, без ярких цветов",
        paletteWithAccents(
            topBar = Color(0xFF1C1E20),
            brand = Color(0xFF37474F),
            active = Color(0xFF546E7A),
        ),
        darkPaletteWithAccents(
            topBar = Color(0xFF3C3E3F),
            brand = Color(0xFF8FA0A8),
            active = Color(0xFF80949D),
            brandFill = Color(0xFF4F5D64),
            activeFill = Color(0xFF546E7A),
        ),
    ),

    INDIGO(
        "indigo",
        "Индиго",
        "Насыщенный сине-фиолетовый акцент",
        paletteWithAccents(
            topBar = Color(0xFF1E1B4B),

            brand = Color(0xFF3730A3),
            active = Color(0xFF4F46E5),
        ),
        darkPaletteWithAccents(
            topBar = Color(0xFF3E3B64),
            brand = Color(0xFF8B87CA),
            active = Color(0xFF8B85EE),
            brandFill = Color(0xFF534DB0),
            activeFill = Color(0xFF4F46E5),
        ),
    ),
    AMBER(
        "amber",
        "Янтарная",
        "Тёплая коричнево-золотая гамма",
        paletteWithAccents(
            topBar = Color(0xFF3E2723),
            brand = Color(0xFF92400E),
            active = Color(0xFFB45309),
        ),
        darkPaletteWithAccents(
            topBar = Color(0xFF594542),
            brand = Color(0xFFB98565),
            active = Color(0xFFC88049),
            brandFill = Color(0xFF944413),
            activeFill = Color(0xFFB45309),
        ),
    ),
    STEEL(
        "steel",
        "Стальная",
        "Светло-голубая, легче фирменной",
        paletteWithAccents(
            topBar = Color(0xFF0C4A6E),
            brand = Color(0xFF075985),
            active = Color(0xFF0369A1),
        ),
        darkPaletteWithAccents(
            topBar = Color(0xFF2E6382),
            brand = Color(0xFF6095B1),
            active = Color(0xFF4F96BD),
            brandFill = Color(0xFF11608A),
            activeFill = Color(0xFF0369A1),
        ),
    ),
    PLUM(
        "plum",
        "Сливовая",
        "Тёплый фиолетово-сливовый акцент",
        paletteWithAccents(
            topBar = Color(0xFF2E1027),
            brand = Color(0xFF6D2A57),
            active = Color(0xFF8E3573),
        ).copy(
            mapRoomFill = Color(0x336D2A57),
            mapRoomBorder = Color(0xFF6D2A57),
        ),
        darkPaletteWithAccents(
            topBar = Color(0xFF4A2C44),
            brand = Color(0xFFC993B8),
            active = Color(0xFFE18ACB),
            brandFill = Color(0xFF7C3A66),
            activeFill = Color(0xFF94407C),
        ).copy(
            mapRoomFill = Color(0x3DC993B8),
            mapRoomBorder = Color(0xFFC993B8),
        ),
    ),
    ;

    companion object {
        fun from(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: COLLEGE
    }
}

private fun paletteWithAccents(
    topBar: Color,
    brand: Color,
    active: Color,
): MpkPalette = LightPalette.copy(
    topBar = topBar,
    brandBlue = brand,
    activeBlue = active,
    brandFill = brand,
    activeFill = active,
    badgeDiploma = brand,
    navSelected = topBar,
)

private fun darkPaletteWithAccents(
    topBar: Color,
    brand: Color,
    active: Color,
    brandFill: Color,
    activeFill: Color,
): MpkPalette = DarkPalette.copy(
    topBar = topBar,
    brandBlue = brand,
    activeBlue = active,
    brandFill = brandFill,
    activeFill = activeFill,
    badgeDiploma = brand,
    navSelected = active,
)

private var appliedTheme: AppTheme = AppTheme.COLLEGE

private val darkModeState = mutableStateOf(false)

val isDarkThemeActive: Boolean get() = darkModeState.value

val darkThemeState: State<Boolean> get() = darkModeState

fun applyAppTheme(
    theme: AppTheme,
    dark: Boolean = darkModeState.value,
) {
    appliedTheme = theme
    darkModeState.value = dark
    paletteState = if (dark) theme.darkPalette else theme.palette
}

fun applyDarkMode(dark: Boolean) {
    applyAppTheme(appliedTheme, dark)
}
