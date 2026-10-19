package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StealthMonochromeDarkScheme = darkColorScheme(
    primary = PlatinumWhite,
    onPrimary = ObsidianBg,
    primaryContainer = ElevatedGraphite,
    onPrimaryContainer = PlatinumWhite,
    secondary = SilverAccent,
    onSecondary = ObsidianBg,
    secondaryContainer = GraphiteSurface,
    onSecondaryContainer = SilverAccent,
    tertiary = LanSignalGreen,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = PlatinumWhite,
    surface = GraphiteSurface,
    onSurface = PlatinumWhite,
    surfaceVariant = ElevatedGraphite,
    onSurfaceVariant = ZincMuted,
    outline = HairlineBorder,
    outlineVariant = SlateDim,
    error = DangerAlertRed,
    onError = ObsidianBg
)

private val StealthMonochromeLightScheme = lightColorScheme(
    primary = InkBlack,
    onPrimary = PaperSurface,
    primaryContainer = PaperSubtle,
    onPrimaryContainer = InkBlack,
    secondary = InkMuted,
    onSecondary = PaperSurface,
    secondaryContainer = PaperSubtle,
    onSecondaryContainer = InkBlack,
    tertiary = Color(0xFF1F2937),
    onTertiary = PaperSurface,
    background = PaperBg,
    onBackground = InkBlack,
    surface = PaperSurface,
    onSurface = InkBlack,
    surfaceVariant = PaperSubtle,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFD4D4D0),
    outlineVariant = Color(0xFFE4E4E0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) StealthMonochromeDarkScheme else StealthMonochromeLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
