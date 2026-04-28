package dev.hackathon.linkopener.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Type scale lifted from the design system (DESIGN.md → typography:). We use
// the platform default font family rather than bundling a custom one.
private val DefaultFamily = FontFamily.Default

val LinkOpenerTypography: Typography = Typography(
    headlineSmall = TextStyle(
        fontFamily = DefaultFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal,
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = DefaultFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontFamily = DefaultFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        fontWeight = FontWeight.Medium,
    ),
)
