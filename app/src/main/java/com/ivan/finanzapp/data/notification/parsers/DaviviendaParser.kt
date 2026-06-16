package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType

/**
 * Parser genérico para notificaciones de la app "Davivienda Móvil".
 *
 * IMPORTANTE: estos son patrones GENÉRICOS basados en formatos típicos de
 * notificaciones bancarias colombianas. Deben ajustarse con ejemplos
 * reales en cuanto Iván empiece a recibir notificaciones de su S26 Ultra.
 *
 * Formatos esperados (ejemplos típicos):
 * - Compra TC: "Davivienda te informa Compra por $50.000 en EXITO aprobada el 15/06/2026 03:45 PM"
 * - Transferencia recibida: "Davivienda te informa Transferencia recibida por $200.000 de JUAN PEREZ"
 * - Transferencia enviada: "Davivienda te informa Transferencia enviada por $100.000 a MARIA LOPEZ"
 * - Pago TC: "Davivienda te informa Pago de tarjeta de credito por $300.000 realizado"
 */
class DaviviendaParser : BankParser {

    override val source = BankSource.DAVIVIENDA

    override val packageNames: Set<String> = setOf(
        "com.davivienda.daviviendamovil",
        "com.davivienda.banca_movil" // nombre alternativo conocido del paquete
    )

    // Compra con tarjeta de crédito o débito
    private val regexCompra = Regex(
        """compra.*?por\s*\$?\s*([\d.,]+)\s*en\s+(.+?)(?:\s+aprobada|\s+el\s|\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Pago de tarjeta de crédito (abono a deuda)
    private val regexPagoTc = Regex(
        """pago.*?tarjeta.*?(?:de\s+)?cr[ée]dito.*?por\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    // Transferencia recibida (ingreso)
    private val regexTransferenciaRecibida = Regex(
        """transferencia\s+recibida.*?por\s*\$?\s*([\d.,]+)(?:\s+de\s+(.+?))?(?:\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Transferencia enviada (egreso)
    private val regexTransferenciaEnviada = Regex(
        """transferencia\s+enviada.*?por\s*\$?\s*([\d.,]+)(?:\s+a\s+(.+?))?(?:\s*$)""",
        RegexOption.IGNORE_CASE
    )

    // Saldo disponible (si la notificación lo incluye)
    private val regexSaldo = Regex(
        """saldo\s+disponible\s*:?\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    override fun parse(title: String, text: String): ParsedTransaction? {
        val fullText = "$title $text"
        val saldo = regexSaldo.find(fullText)?.groupValues?.get(1)?.let { ParserUtils.parseAmount(it) }

        regexCompra.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.GASTO_TC,
                amount = amount,
                merchant = match.groupValues.getOrNull(2)?.trim(),
                availableBalance = saldo,
                source = source,
                confidence = 0.85
            )
        }

        regexPagoTc.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.PAGO_TC,
                amount = amount,
                merchant = "Pago Tarjeta de Crédito",
                availableBalance = saldo,
                source = source,
                confidence = 0.9
            )
        }

        regexTransferenciaRecibida.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.INGRESO,
                amount = amount,
                merchant = match.groupValues.getOrNull(2)?.trim()?.ifBlank { null },
                availableBalance = saldo,
                source = source,
                confidence = 0.85
            )
        }

        regexTransferenciaEnviada.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return ParsedTransaction(
                type = TransactionType.TRANSFERENCIA,
                amount = amount,
                merchant = match.groupValues.getOrNull(2)?.trim()?.ifBlank { null },
                availableBalance = saldo,
                source = source,
                confidence = 0.85
            )
        }

        return null
    }
}
