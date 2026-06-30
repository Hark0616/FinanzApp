package com.ivan.finanzapp.ui.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

object FinanzWidgetColors {
    val BackgroundColor = Color(0xFF0E0F11)
    val SurfaceColor = Color(0xFF18191C)
    val OutlineColor = Color(0xFF3F3A31)
    val TextPrimaryColor = Color(0xFFE8E3D8)
    val TextSecondaryColor = Color(0xFFC8C0B3)
    val TextMutedColor = Color(0xFF8F877A)
    val PrimaryColor = Color(0xFFC9A45B)
    val OnPrimaryColor = Color(0xFF1D1A14)
    val SuccessColor = Color(0xFF5F9473)
    val WarningColor = Color(0xFFC9A45B)
    val ErrorColor = Color(0xFFC06464)

    val Background = ColorProvider(BackgroundColor)
    val Surface = ColorProvider(SurfaceColor)
    val Outline = ColorProvider(OutlineColor)
    val TextPrimary = ColorProvider(TextPrimaryColor)
    val TextSecondary = ColorProvider(TextSecondaryColor)
    val TextMuted = ColorProvider(TextMutedColor)
    val Primary = ColorProvider(PrimaryColor)
    val OnPrimary = ColorProvider(OnPrimaryColor)
    val Success = ColorProvider(SuccessColor)
    val Warning = ColorProvider(WarningColor)
    val Error = ColorProvider(ErrorColor)
}
