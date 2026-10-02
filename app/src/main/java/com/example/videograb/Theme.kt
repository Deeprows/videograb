package com.example.videograb

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val BrandA = Color(0xFF7C5CFF)
val BrandB = Color(0xFFFF4D8D)
val BrandBrush: Brush = Brush.linearGradient(listOf(BrandA, BrandB))

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9B8CFF), onPrimary = Color(0xFF160F3D),
    primaryContainer = Color(0xFF2A2366), onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFFF7FAF), onSecondary = Color(0xFF3F0020),
    secondaryContainer = Color(0xFF4A1B34), onSecondaryContainer = Color(0xFFFFD9E6),
    background = Color(0xFF0C0D17), onBackground = Color(0xFFEDEBF8),
    surface = Color(0xFF0C0D17), onSurface = Color(0xFFEDEBF8),
    surfaceVariant = Color(0xFF1A1C2C), onSurfaceVariant = Color(0xFFABAEC8),
    outline = Color(0xFF3A3D57), outlineVariant = Color(0xFF272A40),
    error = Color(0xFFFF6B6B), onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF4A1C1C), onErrorContainer = Color(0xFFFFD9D9),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6A47F5), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E2FF), onPrimaryContainer = Color(0xFF1D0F66),
    secondary = Color(0xFFD6246F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E6), onSecondaryContainer = Color(0xFF3F0020),
    background = Color(0xFFF7F6FF), onBackground = Color(0xFF16152A),
    surface = Color(0xFFF7F6FF), onSurface = Color(0xFF16152A),
    surfaceVariant = Color(0xFFECEAF8), onSurfaceVariant = Color(0xFF5A5C78),
    outline = Color(0xFFB9BAD3), outlineVariant = Color(0xFFDCDCEE),
    error = Color(0xFFD63B3B), onError = Color.White,
    errorContainer = Color(0xFFFFE0E0), onErrorContainer = Color(0xFF5A1010),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun VideoGrabTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.surfaceVariant.toArgb()
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
