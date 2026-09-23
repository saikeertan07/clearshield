package com.clearshield.adblocker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    secondary = MintGreen,
    tertiary = SlateNavy,
    background = CyberNavy,
    surface = DarkSurface,
    surfaceVariant = DarkCard,
    onPrimary = CyberNavy,
    onSecondary = CyberNavy,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = CrimsonRed
)

@Composable
fun ClearShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
