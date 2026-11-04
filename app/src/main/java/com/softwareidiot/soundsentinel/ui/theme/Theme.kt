package com.softwareidiot.soundsentinel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = TextPrimary,
    primaryContainer = CoralDark,
    onPrimaryContainer = CoralLight,
    secondary = GoldAccent,
    onSecondary = DarkBackground,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = GoldLight,
    tertiary = CyanAccent,
    onTertiary = DarkBackground,
    error = ThreatRed,
    onError = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderColor
)

@Composable
fun SoundSentinelTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        content = content
    )
}
