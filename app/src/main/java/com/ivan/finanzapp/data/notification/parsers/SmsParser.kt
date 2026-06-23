package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType

/**
 * Parser para mensajes SMS recibidos en aplicaciones de mensajería comunes.
 *
 * Soporta de manera local los patrones SMS típicos de Bancolombia y delega
 * otros mensajes o formatos no estructurados al fallback de IA de la app.
 */
class SmsParser : BankParser {

    override val source = BankSource.SMS

    override val packageNames: Set<String> = setOf(
        "com.google.android.apps.messaging", // Google Messages
        "com.samsung.android.messaging",     // Samsung Messages
        "com.android.mms"                    // MIUI/Xiaomi/Default MMS
    )

    // Regex para compras de Bancolombia vía SMS
    private val regexBancolombiaCompra = Regex(
        """Bancolombia(?: le informa)?:?\s+(?:Compra|Pago|Transaccion Debito)\s+por\s+\$?([\d.,]+)\s+en\s+(.+?)(?:\.|\s+Hora|\s+\d{2}/\d{2}|\s+\d{4}|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Regex para retiros de Bancolombia vía SMS
    private val regexBancolombiaRetiro = Regex(
        """Bancolombia(?: le informa)?:?\s+Retiro\s+por\s+\$?([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    // Regex para ingresos/transferencias recibidas en Bancolombia vía SMS
    private val regexBancolombiaIngreso = Regex(
        """Bancolombia(?: le informa)?:?\s+(?:Recepcion|Abono|Recepcion transferencia|Transferencia recibida|recepcion de transferencia)\s+(?:de\s+)?\$?([\d.,]+)\s*(?:de\s+(.+?))?(?:\.|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    private val regexAvVillasCompraTc = Regex(
        """AVVillas\.\s+\d{1,2}/\d{1,2}/\d{2,4}\s+\d{1,2}:\d{2}(?::\d{2})?\s+COMPRA\s+CON\s+TU\s+TARJETA\s+CREDITO\s+(\d{4})\s+POR\s+\$?\s*([\d.,]+)\s+EN\s+(.+?)\s*$""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(title: String, text: String): ParsedTransaction? {
        val fullText = "$title $text".trim()
        val isBancolombia = fullText.contains("Bancolombia", ignoreCase = true) || title.contains("890099")

        regexAvVillasCompraTc.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[2]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.GASTO_TC,
                amount = amount,
                merchant = ParserUtils.cleanMerchant(match.groupValues[3]),
                source = BankSource.AVVILLAS,
                confidence = 0.98
            )
        }

        DaviplataMessagePatterns.parse(title, text)?.let { parsed ->
            return parsed
        }

        val currentSource = if (isBancolombia) BankSource.BANCOLOMBIA else BankSource.SMS

        // 1. Bancolombia Compra
        regexBancolombiaCompra.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            val isTc = fullText.contains("tarjeta", ignoreCase = true) || fullText.contains("tc", ignoreCase = true)
            return ParsedTransaction(
                type = if (isTc) TransactionType.GASTO_TC else TransactionType.GASTO,
                amount = amount,
                merchant = match.groupValues[2].trim().trim('.', '-', ' '),
                source = currentSource,
                confidence = 0.95
            )
        }

        // 2. Bancolombia Retiro
        regexBancolombiaRetiro.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.GASTO,
                amount = amount,
                merchant = "Retiro Cajero",
                source = currentSource,
                confidence = 0.95
            )
        }

        // 3. Bancolombia Ingreso
        regexBancolombiaIngreso.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            val merchant = match.groupValues.getOrNull(2)?.trim()?.trim('.', '-', ' ')
            return ParsedTransaction(
                type = TransactionType.INGRESO,
                amount = amount,
                merchant = merchant ?: "Transferencia Recibida",
                source = currentSource,
                confidence = 0.95
            )
        }

        // Si es de Bancolombia pero no se ajustó a los regex específicos, extraemos monto aproximado
        // y lo dejamos con baja confianza para que la IA lo afine o pase a revisión manual.
        if (isBancolombia) {
            val amountRegex = Regex("""\$?\s*([\d.]+,\d{2})|([\d.,]+)""")
            val amountMatch = amountRegex.find(text)
            if (amountMatch != null) {
                val amount = ParserUtils.parseAmount(amountMatch.value)
                if (amount != null && amount > 0) {
                    val isIncome = text.contains("recibi", ignoreCase = true) || 
                            text.contains("abono", ignoreCase = true) || 
                            text.contains("consigna", ignoreCase = true)
                    return ParsedTransaction(
                        type = if (isIncome) TransactionType.INGRESO else TransactionType.GASTO,
                        amount = amount,
                        merchant = ParserUtils.extractMerchant(text) ?: "Movimiento Bancolombia",
                        source = BankSource.BANCOLOMBIA,
                        confidence = 0.5
                    )
                }
            }
        }

        return null
    }
}
