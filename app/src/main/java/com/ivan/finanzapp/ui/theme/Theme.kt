package com.ivan.finanzapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Green40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Green80,
    secondary = Blue40,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = Blue80,
    background = Surface,
    surface = androidx.compose.ui.graphics.Color.White,
    error = TrafficRed
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = GreenDark,
    primaryContainer = Green40,
    secondary = Blue80,
    onSecondary = Blue40,
    background = SurfaceDark,
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    error = TrafficRed
)

@Composable
fun FinanzAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Colores dinámicos de Android 12+ (Material You); desactívalo si
    // quieres que siempre se use tu paleta definida arriba.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
