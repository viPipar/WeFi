package com.wefi.analyzer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BlynkBlue,
    onPrimary = Color.White,
    primaryContainer = BlynkBlueTint,
    onPrimaryContainer = Color(0xFF1E3A8A),
    background = BlynkBackgroundLight,
    onBackground = TextPrimaryLight,
    surface = BlynkSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSecondaryLight,
    outline = BlynkBorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = BlynkBlue,
    onPrimary = Color.White,
    primaryContainer = BlynkSurfaceDark,
    onPrimaryContainer = BlynkBlue,
    background = BlynkBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = BlynkSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = BlynkSurfaceDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BlynkBorderDark
)

@Composable
fun WeFiTheme(
    darkTheme: Boolean = false, // Standar baku UI: Putih & Biru (#77ADF9) ala Blynk IoT
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
