package com.ivan.finanzapp.data.notification.parsers

/**
 * Utilidades compartidas por todos los parsers de bancos colombianos.
 */
object ParserUtils {

    /**
     * Convierte un monto en formato colombiano típico
     * (ej. "$1.234.567,89" o "$1.234.567" o "1234567.89") a Double.
     *
     * Las notificaciones bancarias colombianas casi siempre usan punto
     * como separador de miles y coma como decimal, pero algunos bancos
     * (o el formato en inglés del sistema) usan el formato inverso.
     * Esta función intenta ser robusta ante ambos casos.
     */
    fun parseAmount(raw: String): Double? {
        // Extrae solo el primer número con posibles separadores
        val regex = Regex("""[\d.,]+""")
        val match = regex.find(raw.replace("$", "")) ?: return null
        var numberStr = match.value

        val lastDot = numberStr.lastIndexOf('.')
        val lastComma = numberStr.lastIndexOf(',')

        numberStr = when {
            lastDot == -1 && lastComma == -1 -> numberStr
            lastDot == -1 -> normalizeSingleSeparatorAmount(numberStr, ',')
            lastComma == -1 -> normalizeSingleSeparatorAmount(numberStr, '.')
            lastComma > lastDot -> {
                // Formato: 1.234.567,89  -> punto = miles, coma = decimal
                numberStr.replace(".", "").replace(",", ".")
            }
            lastDot > lastComma -> {
                // Formato: 1,234,567.89 -> coma = miles, punto = decimal
                numberStr.replace(",", "")
            }
            else -> numberStr.replace(",", "").replace(".", "")
        }

        return numberStr.toDoubleOrNull()
    }

    private fun normalizeSingleSeparatorAmount(raw: String, separator: Char): String {
        val parts = raw.split(separator)
        if (parts.size > 2) {
            return parts.joinToString("")
        }

        val decimalsOrThousands = parts.getOrNull(1).orEmpty()
        return when (decimalsOrThousands.length) {
            3 -> parts.joinToString("")
            2 -> if (separator == ',') raw.replace(",", ".") else raw
            else -> parts.joinToString("")
        }
    }

    fun cleanMerchant(raw: String?): String? {
        val trimmed = raw
            ?.replace(Regex("""\[(.+?)]\((.+?)\)""")) { it.groupValues[1] }
            ?.trim()
            ?.trim('.', '-', ' ', '"')
            ?.replace(Regex("""\s+"""), " ")

        return trimmed?.ifBlank { null }
    }

    /**
     * Extrae el nombre del comercio/destino a partir de palabras clave
     * comunes en notificaciones bancarias colombianas, ej:
     * "Compra por $50.000 en EXITO BARRANQUILLA aprobada"
     * -> "EXITO BARRANQUILLA"
     */
    fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Regex("""en\s+([A-Za-zÁÉÍÓÚÑáéíóúñ0-9 .*\-]{3,40})(?:\s+(?:aprobada|por|el|a las|hora))""", RegexOption.IGNORE_CASE),
            Regex("""en\s+([A-Za-zÁÉÍÓÚÑáéíóúñ0-9 .*\-]{3,40})$""", RegexOption.IGNORE_CASE),
            Regex("""a\s+([A-Za-zÁÉÍÓÚÑáéíóúñ0-9 .*\-]{3,40})(?:\s+(?:desde|el|por))""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return cleanMerchant(match.groupValues[1])
            }
        }
        return null
    }
}
