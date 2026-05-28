package com.example.test_pj.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberpunkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = OnGreen,
    secondary = GreenDim,
    onSecondary = OnGreen,
    background = DarkBg,
    onBackground = OnDarkText,
    surface = SurfaceDark,
    onSurface = OnDarkText,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnDarkSecondary,
    error = ErrorRed,
    onError = OnDarkText,
)

@Composable
fun TestPjTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = Typography,
        content = content,
    )
}
