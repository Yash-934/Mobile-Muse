package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF042F2E),
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = Color(0xFFCFFAFE),
    secondary = CrispIndigo,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = CrispIndigoDim,
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = EmeraldHealthy,
    onTertiary = Color(0xFF022C22),
    error = RoseError,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ElevatedSlate,
    onSurface = TextPrimaryDark,
    surfaceVariant = SubtleGraphite,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSlate
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3ECE4),
    onPrimaryContainer = MuseCharcoalPrimary,
    secondary = IndigoSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCEFE5),
    onSecondaryContainer = Color(0xFF4A250D),
    tertiary = EmeraldHealthy,
    error = RoseError,
    background = AlabasterBg,
    onBackground = TextPrimaryLight,
    surface = SurfaceWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = TextSecondaryLight,
    outline = MuseWarmBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
