package ru.azimut.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB2E07A),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2E3D1F),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFE0E0E0),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF000000),
    outline = Color(0xFF8A8A8A),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerHigh = Color(0xFF262626),
    surfaceContainerHighest = Color(0xFF2C2C2C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF33691E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F0B4),
    onPrimaryContainer = Color(0xFF1B2A10),
    secondary = Color(0xFF8A5100),
    background = Color(0xFFF6F8F1),
    onBackground = Color(0xFF1A1D16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1D16),
    surfaceVariant = Color(0xFFE2E7DA),
    onSurfaceVariant = Color(0xFF43493C),
)

@Composable
fun AzimutTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
