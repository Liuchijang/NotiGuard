package com.local.notiguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Nothing-style accent — the signature red dot. Fills only; red *text* uses [StatusColors.bad]. */
val NothingRed = Color(0xFFD71921)

/**
 * Status text/indicator colors per theme, all ≥ 4.5:1 on the background (WCAG AA):
 * the bright green/amber fail on white and #D71921 text fails on black (4.05:1).
 */
@Immutable
data class StatusColors(val ok: Color, val warn: Color, val bad: Color)

private val DarkStatus = StatusColors(ok = Color(0xFF00C853), warn = Color(0xFFFFAB00), bad = Color(0xFFFF4A52))
private val LightStatus = StatusColors(ok = Color(0xFF00873A), warn = Color(0xFF9A6700), bad = NothingRed)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

// Primary is monochrome like Nothing OS (white pills / switches on black); red is only an accent,
// used explicitly for the brand dot, active selection and errors.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F2F2),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFEDEDED),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0C0C0C),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF8A8A8A),
    outline = Color(0xFF2B2B2B),
    error = Color(0xFFFF4A52),
    onError = Color(0xFFFFFFFF),
    tertiary = Color(0xFFBDBDBD),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A0A0A),
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

// Mono only where it carries the Nothing "technical" feel (app name, section labels, buttons, tags,
// values); running text is sans with normal tracking so descriptions stay easy to read.
private val mono = FontFamily.Monospace
private val sans = FontFamily.Default
private val NTypography = Typography(
    titleLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.5.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
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
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = NTypography,
            shapes = NShapes,
            content = content,
        )
    }
}
