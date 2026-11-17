package com.zwiedzak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF00363B),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFB8F0F2),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF173B35),
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFD1EFE6),
    tertiary = DarkTertiary,
    onTertiary = Color(0xFF402C00),
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = Color(0xFFFFE4A5),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF1A2D29),
    surfaceContainerLow = Color(0xFF223831),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = Color(0xFF30463F),
    surfaceContainerHighest = Color(0xFF3A514A)
)

internal val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF00363B),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF123C35),
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = Color(0xFF281900),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4FAF6),
    surfaceContainer = Color(0xFFECF5F0),
    surfaceContainerHigh = Color(0xFFE5F0EA),
    surfaceContainerHighest = Color(0xFFDCEAE3)
)

@Composable
fun ZwiedzakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}