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

fun sanitizeMoneyInput(value: String, allowDecimalSeparators: Boolean = true): String {
    return value.filter { char ->
        char.isDigit() || (allowDecimalSeparators && (char == '.' || char == ','))
    }
}

fun parseMoneyInput(value: String, allowNegative: Boolean = false): Double? {
    val raw = value
        .trim()
        .replace("$", "")
        .replace(" ", "")
    if (raw.isBlank()) return null

    val isNegative = raw.startsWith("-")
    if (isNegative && !allowNegative) return null

    val unsignedRaw = raw.removePrefix("-")
    val commaCount = unsignedRaw.count { it == ',' }
    val dotCount = unsignedRaw.count { it == '.' }
    val cleaned = when {
        commaCount > 1 && dotCount == 0 -> unsignedRaw.replace(",", "")
        dotCount > 1 && commaCount == 0 -> unsignedRaw.replace(".", "")
        commaCount > 0 && dotCount > 0 -> {
            if (unsignedRaw.lastIndexOf(',') > unsignedRaw.lastIndexOf('.')) {
                unsignedRaw.replace(".", "").replace(",", ".")
            } else {
                unsignedRaw.replace(",", "")
            }
        }
        commaCount == 1 -> {
            val decimals = unsignedRaw.substringAfterLast(',').length
            if (decimals == 3) unsignedRaw.replace(",", "") else unsignedRaw.replace(",", ".")
        }
        dotCount == 1 -> {
            val decimals = unsignedRaw.substringAfterLast('.').length
            if (decimals == 3) unsignedRaw.replace(".", "") else unsignedRaw
        }
        else -> unsignedRaw
    }

    val signed = if (isNegative) "-$cleaned" else cleaned
    return signed.toDoubleOrNull()?.takeIf { allowNegative || it >= 0.0 }
}

fun formatEditableAmount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}
