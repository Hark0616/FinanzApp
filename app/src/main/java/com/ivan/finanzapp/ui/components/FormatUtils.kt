package com.ivan.finanzapp.ui.components

import java.text.NumberFormat
import java.util.Locale

/**
 * Formatea un Double como moneda colombiana.
 * Ej: 1234567.89 -> "$1.234.568"
 *
 * Por defecto se omiten los centavos ya que las notificaciones bancarias
 * colombianas generalmente trabajan en pesos enteros.
 */
fun formatCOP(amount: Double, showCents: Boolean = false): String {
    val locale = Locale.forLanguageTag("es-CO")
    val format = NumberFormat.getCurrencyInstance(locale)
    format.maximumFractionDigits = if (showCents) 2 else 0
    format.minimumFractionDigits = 0
    return format.format(amount)
}

/**
 * Formatea un porcentaje con un decimal.
 * Ej: 22.5 -> "22.5%"
 */
fun formatPercentage(value: Double): String = "%.1f%%".format(value)
