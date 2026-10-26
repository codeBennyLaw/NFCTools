package com.benny.nfctools.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object NfcBrand {
    val scanStart = Color(0xFF2869EE)
    val scanEnd = Color(0xFF194AC5)
    val blue = Color(0xFF2563EB)
    val onScan = Color.White
}

private val Light = lightColorScheme(
    primary = Color(0xFF2563EB), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF0FF), onPrimaryContainer = Color(0xFF194BB2),
    secondary = Color(0xFF52627B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDF1F7), onSecondaryContainer = Color(0xFF364863),
    tertiary = Color(0xFF12785C), tertiaryContainer = Color(0xFFE4F4ED), onTertiaryContainer = Color(0xFF146347),
    background = Color(0xFFF6F8FC), onBackground = Color(0xFF17243D),
    surface = Color.White, onSurface = Color(0xFF17243D),
    surfaceVariant = Color(0xFFEDF1F7), onSurfaceVariant = Color(0xFF53627B),
    outline = Color(0xFF78859B), outlineVariant = Color(0xFFDFE5F0),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFCE9E7), onErrorContainer = Color(0xFF8A1C16),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA8C7FF), onPrimary = Color(0xFF092F78),
    primaryContainer = Color(0xFF183966), onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFBAC7DD), secondaryContainer = Color(0xFF273448), onSecondaryContainer = Color(0xFFDBE5F8),
    tertiary = Color(0xFF8AD8B6), tertiaryContainer = Color(0xFF153E32), onTertiaryContainer = Color(0xFFB1F0D1),
    background = Color(0xFF0F1623), onBackground = Color(0xFFE7EDFA),
    surface = Color(0xFF182233), onSurface = Color(0xFFE7EDFA),
    surfaceVariant = Color(0xFF253247), onSurfaceVariant = Color(0xFFB6C3D9),
    outline = Color(0xFF8D9CB3), outlineVariant = Color(0xFF35445C),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF572922), onErrorContainer = Color(0xFFFFDAD5),
)

@Composable
fun NfcTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography(), content = content)
}
