package com.ogh.shared.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Ogh brand colors.
 *
 * Small high-contrast palette shared by the UI and adaptive icon.
 */
object OghColors {
    val Violet = Color(0xFFD946EF)
    val VioletLight = Color(0xFFF0ABFC)
    val VioletDark = Color(0xFF701A75)
    val Pink = Color(0xFFF43F9E)
    val PinkLight = Color(0xFFF9A8D4)

    val SurfaceDark = Color(0xFF0B0B0E)
    val SurfaceContainer = Color(0xFF151217)
    val SurfaceContainerHigh = Color(0xFF1D181F)
    val SurfaceContainerHighest = Color(0xFF28202A)

    val OnSurface = Color(0xFFF4F2F7)
    val OnSurfaceVariant = Color(0xFFBBB7C2)
    val OnSurfaceMuted = Color(0xFF85818C)

    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)

    val Outline = Color(0xFF5B465F)
    val OutlineVariant = Color(0xFF302633)
}

/**
 * Ogh dark color scheme.
 */
val OghDarkColorScheme = darkColorScheme(
    primary = OghColors.Violet,
    onPrimary = Color.White,
    primaryContainer = OghColors.VioletDark,
    onPrimaryContainer = Color.White,
    secondary = OghColors.Pink,
    onSecondary = Color.White,
    secondaryContainer = OghColors.SurfaceContainerHighest,
    onSecondaryContainer = OghColors.PinkLight,
    background = OghColors.SurfaceDark,
    onBackground = OghColors.OnSurface,
    surface = OghColors.SurfaceDark,
    onSurface = OghColors.OnSurface,
    surfaceVariant = OghColors.SurfaceContainer,
    onSurfaceVariant = OghColors.OnSurfaceVariant,
    error = OghColors.Error,
    onError = Color.White,
    outline = OghColors.Outline,
    outlineVariant = OghColors.OutlineVariant,
    surfaceContainerLow = OghColors.SurfaceContainer,
    surfaceContainer = OghColors.SurfaceContainerHigh,
    surfaceContainerHigh = OghColors.SurfaceContainerHighest,
)

val OghShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

/**
 * Ogh typography.
 */
val OghTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * Ogh application theme.
 *
 * Wraps Material 3 with custom dark colors and typography.
 */
@Composable
fun OghTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OghDarkColorScheme,
        typography = OghTypography,
        shapes = OghShapes,
        content = content,
    )
}
