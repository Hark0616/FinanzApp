package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType

/**
 * Parser genérico para notificaciones de la app "Nequi".
 *
 * Formatos esperados (ejemplos típicos, AJUSTAR con notificaciones reales):
 * - Pago/compra: "Pagaste $25.000 en RAPPI COLOMBIA. Tu saldo es $150.000"
 * - Envío: "Le enviaste $50.000 a Juan Pérez. Tu saldo es $100.000"
 * - Recibido: "Recibiste $200.000 de María López. Tu saldo es $300.000"
 * - Recarga/ingreso: "Recargaste $100.000 a tu Nequi. Tu saldo es $400.000"
 */
class NequiParser : BankParser {

    override val source = BankSource.NEQUI

    override val packageNames: Set<String> = setOf(
        "com.nequi.MobileApp"
    )

    private val regexPagaste = Regex(
        """pagaste\s*\$?\s*([\d.,]+)\s+en\s+(.+?)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexEnviaste = Regex(
        """(?:le\s+)?enviaste\s*\$?\s*([\d.,]+)\s+a\s+(.+?)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexRecibiste = Regex(
        """recibiste\s*\$?\s*([\d.,]+)(?:\s+de\s+(.+?))?\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexRecargaste = Regex(
        """recargaste\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    private val regexSaldo = Regex(
        """(?:tu\s+)?saldo\s+(?:es|disponible)\s*:?\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(title: String, text: String): ParsedTransaction? {
        val fullText = "$title $text"
        val saldo = regexSaldo.find(fullText)?.groupValues?.get(1)?.let { ParserUtils.parseAmount(it) }

        regexPagaste.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.GASTO,
                amount = amount,
                merchant = match.groupValues.getOrNull(2)?.trim(),
                availableBalance = saldo,
                source = source,
                confidence = 0.9
            )
        }

        regexEnviaste.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.TRANSFERENCIA,
                amount = amount,
                merchant = match.groupValues.getOrNull(2)?.trim(),
                availableBalance = saldo,
                source = source,
                confidence = 0.9
            )
        }

        regexRecibiste.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.INGRESO,
                amount = amount,
                merchant = match.groupValues.getOrNull(2)?.trim()?.ifBlank { null },
                availableBalance = saldo,
                source = source,
                confidence = 0.9
            )
        }

        regexRecargaste.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.INGRESO,
                amount = amount,
                merchant = "Recarga Nequi",
                availableBalance = saldo,
                source = source,
                confidence = 0.8
            )
        }

        return null
    }
}
