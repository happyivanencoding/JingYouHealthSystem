package com.thegreatnovel.jingyouhealth.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.thegreatnovel.jingyouhealth.model.ThemeMode

// Oura-like cool mist base: soft color temperature, never office-white.
val Mist = Color(0xFFF0F3FA)
val Ceramic = Color(0xFFF8F9FC)
val MistBlue = Color(0xFFE4EAF5)
val Graphite = Color(0xFF1B1C22)
val GraphiteSoft = Color(0xFF686A73)
val ElectricCyan = Color(0xFF68C7C2)
val ArcticBlue = Color(0xFF6F89D6)
val AuroraViolet = Color(0xFFA184D0)
val DeepViolet = Color(0xFF745A9A)
val Amber = Color(0xFFD2A16F)
val Rose = Color(0xFFC87D86)
val GlassLight = Color(0x99FFFFFF)
val GlassBorderLight = Color(0x8CFFFFFF)

val Void = Color(0xFF080A10)
val NightBlue = Color(0xFF111522)
val NightRaised = Color(0xFF1A2030)
val Frost = Color(0xFFF2F0EC)
val FrostSoft = Color(0xFFA9A6B0)
val GlassDark = Color(0x801C202A)
val GlassBorderDark = Color(0x24FFFFFF)

private val LightColors = lightColorScheme(
    primary = ArcticBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Graphite,
    secondary = AuroraViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E2FF),
    onSecondaryContainer = Graphite,
    tertiary = ElectricCyan,
    onTertiary = Graphite,
    background = Mist,
    onBackground = Graphite,
    surface = Ceramic,
    onSurface = Graphite,
    surfaceVariant = MistBlue,
    onSurfaceVariant = GraphiteSoft,
    outline = Graphite.copy(alpha = 0.18f),
    outlineVariant = Graphite.copy(alpha = 0.08f),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EB0FF),
    onPrimary = Void,
    primaryContainer = Color(0xFF273D80),
    onPrimaryContainer = Frost,
    secondary = Color(0xFFC8B7FF),
    onSecondary = Void,
    secondaryContainer = Color(0xFF41366F),
    onSecondaryContainer = Frost,
    tertiary = Color(0xFF78DFEA),
    onTertiary = Void,
    background = Void,
    onBackground = Frost,
    surface = NightBlue,
    onSurface = Frost,
    surfaceVariant = NightRaised,
    onSurfaceVariant = FrostSoft,
    outline = Frost.copy(alpha = 0.20f),
    outlineVariant = Frost.copy(alpha = 0.08f),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.45).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

val LocalJingYouDarkTheme = staticCompositionLocalOf { false }

@Composable
fun JingYouTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
        }
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalJingYouDarkTheme provides dark,
        LocalContentColor provides colors.onBackground,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content,
        )
    }
}
