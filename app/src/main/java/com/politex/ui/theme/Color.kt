package com.politex.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class MpkPalette(
    val topBar: Color,
    val brandBlue: Color,
    val activeBlue: Color,
    val brandFill: Color,
    val activeFill: Color,
    val menuBg: Color,
    val menuSubBg: Color,
    val menuBorder: Color,
    val menuText: Color,
    val menuSubtext: Color,
    val menuIcon: Color,
    val bgMain: Color,
    val surfaceVariant: Color,
    val surfaceHighlight: Color,
    val textTitle: Color,
    val textBody: Color,
    val textMuted: Color,
    val textWeekdays: Color,
    val textDisabled: Color,
    val borderLight: Color,
    val dividerLight: Color,
    val success: Color,
    val successBg: Color,
    val successBorder: Color,
    val successText: Color,
    val warning: Color,
    val error: Color,
    val errorText: Color,
    val dangerFill: Color,
    val badgeHW: Color,
    val badgeLab: Color,
    val badgePract: Color,
    val badgeExam: Color,
    val badgeCourse: Color,
    val badgeDiploma: Color,
    val mapRoomFill: Color,
    val mapRoomBorder: Color,
    val mapDead: Color,
    val mapDeadBg: Color,
    val mapDeadText: Color,
    val mapRouteCore: Color,
    val accentGold: Color,
    val onAccentGold: Color,
    val accentGoldBg: Color,
    val accentGoldBright: Color,
    val accentGoldSoft: Color,
    val accentGoldMuted: Color,
    val annotationDanger: Color,
    val navSelected: Color,
)

val LightPalette =
    MpkPalette(
        topBar = Color(0xFF001737),
        brandBlue = Color(0xFF0B3564),
        activeBlue = Color(0xFF0072CE),
        brandFill = Color(0xFF0B3564),
        activeFill = Color(0xFF0072CE),
        menuBg = Color(0xFF232527),
        menuSubBg = Color(0xFF1C1E20),
        menuBorder = Color(0xFF35393D),
        menuText = Color(0xFFFFFFFF),
        menuSubtext = Color(0xFFD1D5DB),
        menuIcon = Color(0xFF9CA3AF),
        bgMain = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF8FAFC),
        surfaceHighlight = Color(0xFFEDF2F7),
        textTitle = Color(0xFF111827),
        textBody = Color(0xFF1F2937),
        textMuted = Color(0xFF6B7280),
        textWeekdays = Color(0xFF4B5563),
        textDisabled = Color(0xFFCBD5E1),
        borderLight = Color(0xFFE2E8F0),
        dividerLight = Color(0xFFE5E7EB),
        success = Color(0xFF16A34A),
        successBg = Color(0xFFF0FDF4),
        successBorder = Color(0xFFBBF7D0),
        successText = Color(0xFF166534),
        warning = Color(0xFFF59E0B),
        error = Color(0xFFEF4444),
        errorText = Color(0xFFDC2626),
        dangerFill = Color(0xFFDC2626),
        badgeHW = Color(0xFF0072CE),
        badgeLab = Color(0xFF8B5CF6),
        badgePract = Color(0xFF0D9488),
        badgeExam = Color(0xFFE11D48),
        badgeCourse = Color(0xFFF97316),
        badgeDiploma = Color(0xFF0B3564),
        mapRoomFill = Color(0x331976D2),
        mapRoomBorder = Color(0xFF0057A8),
        mapDead = Color(0xFFC0392B),
        mapDeadBg = Color(0xFFFDECEC),
        mapDeadText = Color(0xFFC0392B),
        mapRouteCore = Color(0xFFFFFFFF),
        accentGold = Color(0xFF7A5C00),
        onAccentGold = Color(0xFFFFFFFF),
        accentGoldBg = Color(0xFF2A2313),
        accentGoldBright = Color(0xFFE8C55A),
        accentGoldSoft = Color(0xFFD9CBA8),
        accentGoldMuted = Color(0xFFB8A87F),
        annotationDanger = Color(0xFFE53935),
        navSelected = Color(0xFF001737),
    )

val DarkPalette =
    MpkPalette(
        topBar = Color(0xFF243753),
        brandBlue = Color(0xFF6FA3D8),
        activeBlue = Color(0xFF3D94DA),
        brandFill = Color(0xFF3C5D83),
        activeFill = Color(0xFF0072CE),
        menuBg = Color(0xFF12161B),
        menuSubBg = Color(0xFF0C1014),
        menuBorder = Color(0xFF2E3338),
        menuText = Color(0xFFFFFFFF),
        menuSubtext = Color(0xFFC7CDD6),
        menuIcon = Color(0xFF98A2B3),
        bgMain = Color(0xFF0F1419),
        surfaceVariant = Color(0xFF161C24),
        surfaceHighlight = Color(0xFF1F2833),
        textTitle = Color(0xFFF3F5F7),
        textBody = Color(0xFFE3E7EC),
        textMuted = Color(0xFF98A2B3),
        textWeekdays = Color(0xFF8B95A5),
        textDisabled = Color(0xFF55606F),
        borderLight = Color(0xFF2A333F),
        dividerLight = Color(0xFF232C37),
        success = Color(0xFF4ADE80),
        successBg = Color(0xFF0D2417),
        successBorder = Color(0xFF15803D),
        successText = Color(0xFF86EFAC),
        warning = Color(0xFFFBBF24),
        error = Color(0xFFF87171),
        errorText = Color(0xFFFCA5A5),
        dangerFill = Color(0xFFDC2626),
        badgeHW = Color(0xFF5DADE2),
        badgeLab = Color(0xFFA78BFA),
        badgePract = Color(0xFF2DD4BF),
        badgeExam = Color(0xFFFB7185),
        badgeCourse = Color(0xFFFB923C),
        badgeDiploma = Color(0xFF6FA3D8),
        mapRoomFill = Color(0x3D6FA3D8),
        mapRoomBorder = Color(0xFF6FA3D8),
        mapDead = Color(0xFFE57373),
        mapDeadBg = Color(0xFF3D1B17),
        mapDeadText = Color(0xFFF28B82),
        mapRouteCore = Color(0xFF0F1419),
        accentGold = Color(0xFFC9A24B),
        onAccentGold = Color(0xFF2A2313),
        accentGoldBg = Color(0xFF2A2313),
        accentGoldBright = Color(0xFFD9B45C),
        accentGoldSoft = Color(0xFFD9CBA8),
        accentGoldMuted = Color(0xFFB8A87F),
        annotationDanger = Color(0xFFFF8A80),
        navSelected = Color(0xFF3D94DA),
    )

internal var paletteState by mutableStateOf(LightPalette)

val ColorTopBar: Color get() = paletteState.topBar
val ColorBrandBlue: Color get() = paletteState.brandBlue
val ColorActiveBlue: Color get() = paletteState.activeBlue
val ColorBrandFill: Color get() = paletteState.brandFill
val ColorActiveFill: Color get() = paletteState.activeFill

val ColorMenuBg: Color get() = paletteState.menuBg
val ColorMenuSubBg: Color get() = paletteState.menuSubBg
val ColorMenuBorder: Color get() = paletteState.menuBorder
val ColorMenuText: Color get() = paletteState.menuText
val ColorMenuSubtext: Color get() = paletteState.menuSubtext
val ColorMenuIcon: Color get() = paletteState.menuIcon

val ColorBgMain: Color get() = paletteState.bgMain
val ColorSurfaceLight: Color get() = paletteState.bgMain
val ColorSurfaceVariantLight: Color get() = paletteState.surfaceVariant
val ColorSurfaceHighlight: Color get() = paletteState.surfaceHighlight
val ColorTextTitle: Color get() = paletteState.textTitle
val ColorTextBody: Color get() = paletteState.textBody
val ColorTextMuted: Color get() = paletteState.textMuted
val ColorTextWeekdays: Color get() = paletteState.textWeekdays
val ColorTextDisabled: Color get() = paletteState.textDisabled
val ColorBorderLight: Color get() = paletteState.borderLight
val ColorDividerLight: Color get() = paletteState.dividerLight

val ColorSuccess: Color get() = paletteState.success
val ColorSuccessBg: Color get() = paletteState.successBg
val ColorSuccessBorder: Color get() = paletteState.successBorder
val ColorSuccessText: Color get() = paletteState.successText
val ColorWarning: Color get() = paletteState.warning
val ColorError: Color get() = paletteState.error
val ColorErrorText: Color get() = paletteState.errorText
val ColorDangerFill: Color get() = paletteState.dangerFill

val ColorBadgeHW: Color get() = paletteState.badgeHW
val ColorBadgeLab: Color get() = paletteState.badgeLab
val ColorBadgePract: Color get() = paletteState.badgePract
val ColorBadgeExam: Color get() = paletteState.badgeExam
val ColorBadgeCourse: Color get() = paletteState.badgeCourse
val ColorBadgeDiploma: Color get() = paletteState.badgeDiploma

val ColorMapRoomFill: Color get() = paletteState.mapRoomFill
val ColorMapRoomBorder: Color get() = paletteState.mapRoomBorder
val ColorMapDead: Color get() = paletteState.mapDead
val ColorMapDeadBg: Color get() = paletteState.mapDeadBg
val ColorMapDeadText: Color get() = paletteState.mapDeadText
val ColorMapRouteCore: Color get() = paletteState.mapRouteCore

val ColorAccentGold: Color get() = paletteState.accentGold
val ColorOnAccentGold: Color get() = paletteState.onAccentGold
val ColorAccentGoldBg: Color get() = paletteState.accentGoldBg
val ColorAccentGoldBright: Color get() = paletteState.accentGoldBright
val ColorAccentGoldSoft: Color get() = paletteState.accentGoldSoft
val ColorAccentGoldMuted: Color get() = paletteState.accentGoldMuted

val ColorAnnotationDanger: Color get() = paletteState.annotationDanger

val ColorNavSelected: Color get() = paletteState.navSelected
