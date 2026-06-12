package com.enship.travel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val EnerShipColors = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = NauticalBackground,
    primaryContainer = CyanPrimaryDark,
    onPrimaryContainer = TextPrimary,
    secondary = StatusInfo,
    onSecondary = NauticalBackground,
    background = NauticalBackground,
    onBackground = TextPrimary,
    surface = NauticalSurface,
    onSurface = TextPrimary,
    surfaceVariant = NauticalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NauticalOutline,
    error = StatusDanger,
    onError = NauticalBackground,
)

private val Color0E1726 = NauticalBackground

/**
 * Theme global de l'application : Material3, dark nautique, toujours sombre
 * (un poste de pilotage embarque doit rester lisible de nuit).
 */
@Composable
fun EnerShipTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = NauticalSurface.toArgb()
            window.navigationBarColor = NauticalSurface.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = EnerShipColors,
        typography = EnerShipTypography,
        content = content,
    )
}
