package com.local.notiguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Nothing-style accent — the signature red dot. */
val NothingRed = Color(0xFFD71921)

private val DarkColors = darkColorScheme(
    primary = NothingRed,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFEDEDED),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0C0C0C),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF8A8A8A),
    outline = Color(0xFF2B2B2B),
    error = NothingRed,
    onError = Color(0xFFFFFFFF),
    tertiary = Color(0xFFBDBDBD),
)

private val LightColors = lightColorScheme(
    primary = NothingRed,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF1A1A1A),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outline = Color(0xFFD8D8D8),
    error = NothingRed,
    onError = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4A4A4A),
)

// Monospace everywhere + wide tracking = the Nothing "technical" feel.
private val mono = FontFamily.Monospace
private val NTypography = Typography(
    titleLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 1.sp),
    bodyLarge = TextStyle(fontFamily = mono, fontSize = 14.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = mono, fontSize = 13.sp, letterSpacing = 0.3.sp),
    bodySmall = TextStyle(fontFamily = mono, fontSize = 12.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp),
)

// Squared, industrial corners.
private val NShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
)

@Composable
fun NotiGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NTypography,
        shapes = NShapes,
        content = content,
    )
}
