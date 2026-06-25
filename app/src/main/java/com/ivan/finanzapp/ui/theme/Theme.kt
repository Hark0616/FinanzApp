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
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE8D9A8),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF241A06),
    secondary = Blue40,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE5E7EB),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF1D2430),
    background = Surface,
    onBackground = androidx.compose.ui.graphics.Color(0xFF171717),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFCF6),
    onSurface = androidx.compose.ui.graphics.Color(0xFF171717),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDE8DD),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF625D52),
    outline = androidx.compose.ui.graphics.Color(0xFFC9BFAE),
    error = TrafficRed
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    onPrimary = GreenDark,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF3B3018),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFF3E4AE),
    secondary = Blue80,
    onSecondary = Blue40,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF202631),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFD8DEE9),
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = androidx.compose.ui.graphics.Color(0xFF18191C),
    onSurface = OnSurfaceDark,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF25262A),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC8C0B3),
    outline = androidx.compose.ui.graphics.Color(0xFF3F3A31),
    error = TrafficRed
)

@Composable
fun FinanzAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
