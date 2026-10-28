package com.tkdpro.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkdpro.R
import com.tkdpro.core.datastore.ThemeMode

object TkdPalette {
    val DarkBackground = Color(0xFF101114)
    val DarkSurface = Color(0xFF1B1D23)
    val DarkElevated = Color(0xFF242730)
    val DarkAccent = Color(0xFFE53945)
    val DarkText = Color(0xFFFFFFFF)
    val DarkTextSecondary = Color(0xFFA4A7B0)
    val Success = Color(0xFF35C78A)

    val LightBackground = Color(0xFFF5F6F8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightElevated = Color(0xFFEDEEF2)
    val LightAccent = Color(0xFFD92F3D)
    val LightText = Color(0xFF17191F)
    val LightTextSecondary = Color(0xFF686C76)
    val LightSuccess = Color(0xFF1F9E68)

    val Warning = Color(0xFFE0A030)
    val Info = Color(0xFF4C8DF6)
    val Ai = Color(0xFF9B7BFF)
}

/** Colours not covered by the Material scheme. */
@Immutable
data class TkdExtendedColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val ai: Color,
    val elevated: Color,
    val textSecondary: Color,
    val chart: List<Color>,
)

val LocalTkdColors = staticCompositionLocalOf {
    TkdExtendedColors(
        TkdPalette.Success, TkdPalette.Warning, TkdPalette.Info, TkdPalette.Ai,
        TkdPalette.DarkElevated, TkdPalette.DarkTextSecondary, emptyList(),
    )
}

private val DarkColors = darkColorScheme(
    primary = TkdPalette.DarkAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1519),
    onPrimaryContainer = Color(0xFFFFDAD8),
    secondary = TkdPalette.DarkTextSecondary,
    onSecondary = TkdPalette.DarkBackground,
    secondaryContainer = TkdPalette.DarkElevated,
    onSecondaryContainer = TkdPalette.DarkText,
    tertiary = TkdPalette.Success,
    background = TkdPalette.DarkBackground,
    onBackground = TkdPalette.DarkText,
    surface = TkdPalette.DarkSurface,
    onSurface = TkdPalette.DarkText,
    surfaceVariant = TkdPalette.DarkElevated,
    onSurfaceVariant = TkdPalette.DarkTextSecondary,
    surfaceContainerLowest = TkdPalette.DarkBackground,
    surfaceContainerLow = TkdPalette.DarkSurface,
    surfaceContainer = TkdPalette.DarkSurface,
    surfaceContainerHigh = TkdPalette.DarkElevated,
    surfaceContainerHighest = Color(0xFF2E323C),
    outline = Color(0xFF3A3E49),
    outlineVariant = Color(0xFF2A2D35),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = TkdPalette.LightAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410006),
    secondary = TkdPalette.LightTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = TkdPalette.LightElevated,
    onSecondaryContainer = TkdPalette.LightText,
    tertiary = TkdPalette.LightSuccess,
    background = TkdPalette.LightBackground,
    onBackground = TkdPalette.LightText,
    surface = TkdPalette.LightSurface,
    onSurface = TkdPalette.LightText,
    surfaceVariant = TkdPalette.LightElevated,
    onSurfaceVariant = TkdPalette.LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = TkdPalette.LightSurface,
    surfaceContainer = TkdPalette.LightSurface,
    surfaceContainerHigh = TkdPalette.LightElevated,
    surfaceContainerHighest = Color(0xFFE2E4E9),
    outline = Color(0xFFCDD0D6),
    outlineVariant = Color(0xFFE4E6EA),
    error = Color(0xFFC62828),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, weight: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.sp,
)

val TkdTypography = Typography(
    displayLarge = style(48, 800, 56, -1.0),
    displayMedium = style(40, 800, 48, -0.8),
    displaySmall = style(32, 700, 40, -0.5),
    headlineLarge = style(28, 700, 36, -0.4),
    headlineMedium = style(24, 700, 32, -0.3),
    headlineSmall = style(20, 700, 28, -0.2),
    titleLarge = style(18, 700, 26),
    titleMedium = style(16, 600, 24),
    titleSmall = style(14, 600, 20),
    bodyLarge = style(16, 400, 24),
    bodyMedium = style(14, 400, 20),
    bodySmall = style(12, 400, 16),
    labelLarge = style(14, 600, 20, 0.1),
    labelMedium = style(12, 600, 16, 0.3),
    labelSmall = style(11, 600, 14, 0.4),
)

val TkdShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun TkdTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val extended = if (dark) {
        TkdExtendedColors(
            success = TkdPalette.Success,
            warning = TkdPalette.Warning,
            info = TkdPalette.Info,
            ai = TkdPalette.Ai,
            elevated = TkdPalette.DarkElevated,
            textSecondary = TkdPalette.DarkTextSecondary,
            chart = listOf(TkdPalette.DarkAccent, TkdPalette.Info, TkdPalette.Success, TkdPalette.Warning, TkdPalette.Ai, Color(0xFF5ED0E0)),
        )
    } else {
        TkdExtendedColors(
            success = TkdPalette.LightSuccess,
            warning = Color(0xFFB7791F),
            info = Color(0xFF2F6FDB),
            ai = Color(0xFF6E4FE0),
            elevated = TkdPalette.LightElevated,
            textSecondary = TkdPalette.LightTextSecondary,
            chart = listOf(TkdPalette.LightAccent, Color(0xFF2F6FDB), TkdPalette.LightSuccess, Color(0xFFB7791F), Color(0xFF6E4FE0), Color(0xFF138A9C)),
        )
    }
    CompositionLocalProvider(LocalTkdColors provides extended) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = TkdTypography,
            shapes = TkdShapes,
            content = content,
        )
    }
}

object TkdTheme {
    val colors: TkdExtendedColors
        @Composable get() = LocalTkdColors.current
}

/** Spacing scale used across all screens. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
}
