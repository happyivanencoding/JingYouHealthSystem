package com.thegreatnovel.jingyouhealth.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.thegreatnovel.jingyouhealth.model.ThemeMode

// Warm ceramic, quiet cobalt and sea glass: a personal, unhurried atmosphere.
val Mist = Color(0xFFF2F1EF)
val Ceramic = Color(0xFFF8F7F3)
val MistBlue = Color(0xFFE7EBF0)
val Graphite = Color(0xFF202932)
val GraphiteSoft = Color(0xFF606A75)
val CobaltInk = Color(0xFF3D608B)
val ElectricCyan = Color(0xFF68C7C2)
val ArcticBlue = Color(0xFF6F89D6)
val AuroraViolet = Color(0xFFA184D0)
val DeepViolet = Color(0xFF745A9A)
val Amber = Color(0xFFD2A16F)
val Rose = Color(0xFFC87D86)
val GlassLight = Color(0x99FFFFFF)
val GlassBorderLight = Color(0x8CFFFFFF)

val Void = Color(0xFF0B1118)
val NightBlue = Color(0xFF141C28)
val NightRaised = Color(0xFF1C2735)
val Frost = Color(0xFFF2F0EC)
val FrostSoft = Color(0xFFA9A6B0)
val GlassDark = Color(0x801C202A)
val GlassBorderDark = Color(0x24FFFFFF)

private val LightColors = lightColorScheme(
    primary = CobaltInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E8F0),
    onPrimaryContainer = Graphite,
    secondary = Color(0xFF746391),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE4F1),
    onSecondaryContainer = Graphite,
    tertiary = Color(0xFF397F7D),
    onTertiary = Color.White,
    background = Mist,
    onBackground = Graphite,
    surface = Ceramic,
    onSurface = Graphite,
    surfaceVariant = MistBlue,
    onSurfaceVariant = GraphiteSoft,
    surfaceContainerLowest = Color(0xFFFCFBF8),
    surfaceContainerLow = Color(0xFFF4F3F0),
    surfaceContainer = Color(0xFFEFEEEB),
    surfaceContainerHigh = Color(0xFFE8E9E7),
    surfaceContainerHighest = Color(0xFFE1E4E5),
    outline = Graphite.copy(alpha = 0.18f),
    outlineVariant = Graphite.copy(alpha = 0.08f),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA2C1E2),
    onPrimary = Void,
    primaryContainer = Color(0xFF293F59),
    onPrimaryContainer = Frost,
    secondary = Color(0xFFC1B2D7),
    onSecondary = Void,
    secondaryContainer = Color(0xFF423952),
    onSecondaryContainer = Frost,
    tertiary = Color(0xFF91CCC5),
    onTertiary = Void,
    background = Void,
    onBackground = Frost,
    surface = NightBlue,
    onSurface = Frost,
    surfaceVariant = NightRaised,
    onSurfaceVariant = FrostSoft,
    surfaceContainerLowest = Color(0xFF080E15),
    surfaceContainerLow = Color(0xFF111A25),
    surfaceContainer = NightBlue,
    surfaceContainerHigh = NightRaised,
    surfaceContainerHighest = Color(0xFF273241),
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
    val themeProgress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(450),
        label = "theme-light",
    )
    // Interpolate surfaces and ink together, so switching appearance does not flash.
    val colors = (if (dark) DarkColors else LightColors).copy(
        primary = lerp(LightColors.primary, DarkColors.primary, themeProgress),
        onPrimary = lerp(LightColors.onPrimary, DarkColors.onPrimary, themeProgress),
        primaryContainer = lerp(LightColors.primaryContainer, DarkColors.primaryContainer, themeProgress),
        onPrimaryContainer = lerp(LightColors.onPrimaryContainer, DarkColors.onPrimaryContainer, themeProgress),
        secondary = lerp(LightColors.secondary, DarkColors.secondary, themeProgress),
        onSecondary = lerp(LightColors.onSecondary, DarkColors.onSecondary, themeProgress),
        secondaryContainer = lerp(LightColors.secondaryContainer, DarkColors.secondaryContainer, themeProgress),
        onSecondaryContainer = lerp(LightColors.onSecondaryContainer, DarkColors.onSecondaryContainer, themeProgress),
        tertiary = lerp(LightColors.tertiary, DarkColors.tertiary, themeProgress),
        onTertiary = lerp(LightColors.onTertiary, DarkColors.onTertiary, themeProgress),
        background = lerp(LightColors.background, DarkColors.background, themeProgress),
        onBackground = lerp(LightColors.onBackground, DarkColors.onBackground, themeProgress),
        surface = lerp(LightColors.surface, DarkColors.surface, themeProgress),
        onSurface = lerp(LightColors.onSurface, DarkColors.onSurface, themeProgress),
        surfaceVariant = lerp(LightColors.surfaceVariant, DarkColors.surfaceVariant, themeProgress),
        onSurfaceVariant = lerp(LightColors.onSurfaceVariant, DarkColors.onSurfaceVariant, themeProgress),
        surfaceContainerLowest = lerp(LightColors.surfaceContainerLowest, DarkColors.surfaceContainerLowest, themeProgress),
        surfaceContainerLow = lerp(LightColors.surfaceContainerLow, DarkColors.surfaceContainerLow, themeProgress),
        surfaceContainer = lerp(LightColors.surfaceContainer, DarkColors.surfaceContainer, themeProgress),
        surfaceContainerHigh = lerp(LightColors.surfaceContainerHigh, DarkColors.surfaceContainerHigh, themeProgress),
        surfaceContainerHighest = lerp(LightColors.surfaceContainerHighest, DarkColors.surfaceContainerHighest, themeProgress),
        outline = lerp(LightColors.outline, DarkColors.outline, themeProgress),
        outlineVariant = lerp(LightColors.outlineVariant, DarkColors.outlineVariant, themeProgress),
    )
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
