package com.zwiedzak.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun themeTextMeetsContrastRequirements() {
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            pairs(scheme).forEach { (foreground, background) ->
                val a = foreground.luminance()
                val b = background.luminance()
                val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
                assertTrue("Contrast $contrast for $foreground on $background", contrast >= 4.5f)
            }
        }
    }

    private fun pairs(scheme: ColorScheme): List<Pair<Color, Color>> = listOf(
        scheme.onBackground to scheme.background,
        scheme.onSurface to scheme.surface,
        scheme.onSurface to scheme.surfaceContainer,
        scheme.onSurface to scheme.surfaceContainerHigh,
        scheme.onSurface to scheme.surfaceContainerHighest,
        scheme.onSurfaceVariant to scheme.surfaceVariant,
        scheme.onPrimary to scheme.primary,
        scheme.onPrimaryContainer to scheme.primaryContainer,
        scheme.onSecondary to scheme.secondary,
        scheme.onSecondaryContainer to scheme.secondaryContainer,
        scheme.onTertiary to scheme.tertiary,
        scheme.onTertiaryContainer to scheme.tertiaryContainer
    )
}
