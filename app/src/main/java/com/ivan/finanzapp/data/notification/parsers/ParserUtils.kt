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

        // Si tiene tanto '.' como ',', el último símbolo es el decimal
        val lastDot = numberStr.lastIndexOf('.')
        val lastComma = numberStr.lastIndexOf(',')

        numberStr = when {
            lastDot == -1 && lastComma == -1 -> numberStr
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
                return match.groupValues[1].trim().trim('.', '-', ' ')
            }
        }
        return null
    }
}
