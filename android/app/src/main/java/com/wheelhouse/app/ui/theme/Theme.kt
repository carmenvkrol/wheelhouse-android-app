package com.wheelhouse.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val WheelHouseColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Ink2,
    background = Bg,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Fill,
    onSurfaceVariant = Ink2,
    outline = Line,
    outlineVariant = Line2,
    error = AlarmInk,
    errorContainer = AlarmBg,
)

/**
 * Light-only for now — the wireframes are grey-box on purpose (design.md:
 * "layout and state logic, not visual design"). Dark theme is a real design
 * pass, not a free ColorScheme swap, so it's left for later.
 */
@Composable
fun WheelHouseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WheelHouseColorScheme,
        typography = Typography,
        content = content,
    )
}
