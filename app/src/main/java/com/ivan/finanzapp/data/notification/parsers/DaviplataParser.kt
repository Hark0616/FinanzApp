package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType

/**
 * Parser genérico para notificaciones de la app "Daviplata".
 *
 * Formatos esperados (ejemplos típicos, AJUSTAR con notificaciones reales):
 * - Compra: "Realizaste una compra por $30.000 en TIENDA XYZ. Saldo: $120.000"
 * - Transferencia enviada: "Enviaste $80.000 a Pedro Gómez. Saldo: $40.000"
 * - Transferencia/giro recibido: "Recibiste un giro por $250.000. Saldo: $290.000"
 * - Retiro: "Retiraste $50.000 en cajero. Saldo: $190.000"
 */
class DaviplataParser : BankParser {

    override val source = BankSource.DAVIPLATA

    override val packageNames: Set<String> = setOf(
        "com.todo1.mobile.davivienda.daviplata"
    )

    private val regexCompra = Regex(
        """compra\s+por\s*\$?\s*([\d.,]+)\s+en\s+(.+?)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexEnviaste = Regex(
        """enviaste\s*\$?\s*([\d.,]+)\s+a\s+(.+?)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexGiroRecibido = Regex(
        """recibiste\s+(?:un\s+)?(?:giro|transferencia)\s+por\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    private val regexRetiro = Regex(
        """retiraste\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    private val regexSaldo = Regex(
        """saldo\s*:?\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(title: String, text: String): ParsedTransaction? {
        val fullText = "$title $text"
        val saldo = regexSaldo.find(fullText)?.groupValues?.get(1)?.let { ParserUtils.parseAmount(it) }

        regexCompra.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.GASTO,
                amount = amount,
                merchant = match.groupValues.getOrNull(2)?.trim(),
                availableBalance = saldo,
                source = source,
                confidence = 0.85
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
                confidence = 0.85
            )
        }

        regexGiroRecibido.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.INGRESO,
                amount = amount,
                merchant = "Giro/Transferencia recibida",
                availableBalance = saldo,
                source = source,
                confidence = 0.85
            )
        }

        regexRetiro.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.GASTO,
                amount = amount,
                merchant = "Retiro en cajero",
                availableBalance = saldo,
                source = source,
                confidence = 0.85
            )
        }

        return null
    }
}
