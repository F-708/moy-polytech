package com.politex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography: Typography
    @Composable get() =
        Typography(
            displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.5).sp,
                color = ColorTextTitle,
            ),
            displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.3).sp,
                color = ColorTextTitle,
            ),
            titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
                color = ColorTextTitle,
            ),
            titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.sp,
            ),
            titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
            ),
            bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.25.sp,
                color = ColorTextBody,
            ),
            bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.2.sp,
                color = ColorTextBody,
            ),
            bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.2.sp,
                color = ColorTextMuted,
            ),
            labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
            labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.2.sp,
            ),
            labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.3.sp,
            ),
        )

val TextStyleTopDateBar: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = ColorMenuText,
            letterSpacing = 0.2.sp,
        )

val TextStyleCollegeBranding: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            color = ColorBrandBlue,
            letterSpacing = (-0.2).sp,
        )

val TextStyleMenuBarTitle: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = ColorMenuText,
            letterSpacing = 0.5.sp,
        )

val TextStylePageTitle: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            color = ColorTextTitle,
            letterSpacing = (-0.5).sp,
        )

val TextStyleSubPageTitle: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = ColorTextTitle,
            letterSpacing = (-0.5).sp,
        )

val TextStyleCalendarMonth: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = ColorTextTitle,
            letterSpacing = 0.3.sp,
        )

val TextStyleCalendarWeekdays: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = ColorTextWeekdays,
            letterSpacing = 0.4.sp,
        )

val TextStyleMenuItem: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = ColorMenuText,
            letterSpacing = 0.3.sp,
        )

val TextStyleMenuSubItem: TextStyle
    @Composable get() =
        TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = ColorMenuSubtext,
            letterSpacing = 0.2.sp,
        )
