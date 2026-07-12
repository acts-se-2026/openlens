package com.openlens.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** OpenLens design tokens — quiet, premium scanner. Dark only for v1. */
object OpenLensColors {
    val Bg = Color(0xFF0E0E11)       // near-black, keeps depth
    val Surface = Color(0xFF1A1A1F)  // cards / sheets off the camera
    val Accent = Color(0xFF3DE1FF)   // the one "actionable / detected" color
    val OnAccent = Color(0xFF04232B) // dark content on the light cyan
    val TextHi = Color(0xFFF5F5F7)
    val TextLo = Color(0xFF9A9AA2)
}

private val colorScheme = darkColorScheme(
    primary = OpenLensColors.Accent,
    onPrimary = OpenLensColors.OnAccent,
    background = OpenLensColors.Bg,
    onBackground = OpenLensColors.TextHi,
    surface = OpenLensColors.Surface,
    onSurface = OpenLensColors.TextHi,
    surfaceVariant = OpenLensColors.Surface,
    onSurfaceVariant = OpenLensColors.TextLo,
)

@Composable
fun OpenLensTheme(content: @Composable () -> Unit) {
    // Neutral geometric sans: using the platform default for now; bundling Inter/Geist is a follow-up.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
