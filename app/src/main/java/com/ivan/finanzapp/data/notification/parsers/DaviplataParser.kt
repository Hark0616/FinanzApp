package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType

/**
 * Parser para notificaciones emitidas directamente por la app DaviPlata.
 *
 * Los SMS de DaviPlata llegan desde la app de mensajes, por eso [SmsParser]
 * reutiliza [DaviplataMessagePatterns] en lugar de depender del packageName
 * de la app DaviPlata.
 */
class DaviplataParser : BankParser {

    override val source = BankSource.DAVIPLATA

    override val packageNames: Set<String> = setOf(
        "com.davivienda.daviplataapp",
        "com.davivienda.daviplata",
        "com.todo1.mobile.davivienda.daviplata"
    )

    override fun parse(title: String, text: String): ParsedTransaction? =
        DaviplataMessagePatterns.parse(title, text)
}

/**
 * Reglas de DaviPlata basadas en mensajes reales. Se comparten entre el
 * parser de la app y el parser SMS porque el origen tecnico puede variar:
 * algunas alertas salen como notificacion propia de DaviPlata y otras como
 * SMS dentro de Google/Samsung Messages.
 */
internal object DaviplataMessagePatterns {

    private val regexBreBReceived = Regex(
        """DaviPlata:\s*Recibiste\s+plata\s+por\s+Bre-?B\s+por\s+valor\s+de\s*\$?\s*([\d.,]+)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexReceivedFromDavivienda = Regex(
        """DaviPlata:\s*recibiste\s*\$?\s*([\d.,]+)\s+desde\s+una\s+cuenta\s+Davivienda\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexSimpleReceived = Regex(
        """(?:DaviPlata:\s*)?Recibiste\s*\$?\s*([\d.,]+)\.\s*Para\s+saber\s+m[aá]s,\s+consulta\s+tus\s+movimientos\.?""",
        RegexOption.IGNORE_CASE
    )

    private val regexPsePurchase = Regex(
        """DaviPlata:\s*acabas\s+de\s+hacer\s+una\s+compra\s+por\s+PSE\s+de\s*\$?\s*([\d.,]+)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexQrPurchase = Regex(
        """DaviPlata:\s*tu\s+compra\s+de\s*\$?\s*([\d.,]+)\s+por\s+c[oó]digo\s+QR\s+fue\s+exitosa\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexSuccessfulPayment = Regex(
        """DaviPlata:\s*Pago\s+exitoso\s+por\s*\$?\s*([\d.,]+)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexDiscount = Regex(
        """Se\s+realiz[oó]\s+un\s+descuento\s+de\s*\$?\s*([\d.,]+)\s+en\s+tu\s+DaviPlata(?:\s+o\s+App\s+Civica)?\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexCashOut = Regex(
        """DaviPlata:\s*acabas\s+de\s+Sacar\s*\$?\s*([\d.,]+)\s+de\s+tu\s+DaviPlata\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexTransferToOtherBank = Regex(
        """DaviPlata:\s*Pasaste\s+plata\s+a\s+otro\s+banco\s+por\s+valor\s+de\s*\$?\s*([\d.,]+)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexGenericPurchase = Regex(
        """compra\s+por\s*\$?\s*([\d.,]+)\s+en\s+(.+?)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexGenericSent = Regex(
        """enviaste\s*\$?\s*([\d.,]+)\s+a\s+(.+?)\.""",
        RegexOption.IGNORE_CASE
    )

    private val regexGenericReceived = Regex(
        """recibiste\s+(?:un\s+)?(?:giro|transferencia)\s+por\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    private val regexGenericCashOut = Regex(
        """retiraste\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    private val regexSaldo = Regex(
        """saldo\s*:?\s*\$?\s*([\d.,]+)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(title: String, text: String): ParsedTransaction? {
        val fullText = "$title $text".trim()
        val saldo = regexSaldo.find(fullText)?.groupValues?.get(1)?.let { ParserUtils.parseAmount(it) }

        regexBreBReceived.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return income(amount, "BreB", saldo, 0.98)
        }

        regexReceivedFromDavivienda.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return income(amount, "Cuenta Davivienda", saldo, 0.98)
        }

        regexSimpleReceived.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return income(amount, "Transferencia recibida", saldo, 0.96)
        }

        regexPsePurchase.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return expense(amount, "PSE", saldo, 0.98)
        }

        regexQrPurchase.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return expense(amount, "Compra QR", saldo, 0.98)
        }

        regexSuccessfulPayment.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return expense(amount, "Pago DaviPlata", saldo, 0.96)
        }

        regexDiscount.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return expense(amount, "Descuento DaviPlata", saldo, 0.96)
        }

        regexCashOut.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return transfer(amount, "Retiro DaviPlata", saldo, 0.96)
        }

        regexTransferToOtherBank.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return transfer(amount, "Otro banco", saldo, 0.96)
        }

        regexGenericPurchase.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return expense(amount, ParserUtils.cleanMerchant(match.groupValues.getOrNull(2)), saldo, 0.85)
        }

        regexGenericSent.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return transfer(amount, ParserUtils.cleanMerchant(match.groupValues.getOrNull(2)), saldo, 0.85)
        }

        regexGenericReceived.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return income(amount, "Giro/Transferencia recibida", saldo, 0.85)
        }

        regexGenericCashOut.find(fullText)?.let { match ->
            val amount = ParserUtils.parseAmount(match.groupValues[1]) ?: return@let
            return transfer(amount, "Retiro DaviPlata", saldo, 0.85)
        }

        return null
    }

    private fun income(
        amount: Double,
        merchant: String,
        availableBalance: Double?,
        confidence: Double
    ) = ParsedTransaction(
        type = TransactionType.INGRESO,
        amount = amount,
        merchant = merchant,
        availableBalance = availableBalance,
        source = BankSource.DAVIPLATA,
        confidence = confidence
    )

    private fun expense(
        amount: Double,
        merchant: String?,
        availableBalance: Double?,
        confidence: Double
    ) = ParsedTransaction(
        type = TransactionType.GASTO,
        amount = amount,
        merchant = merchant,
        availableBalance = availableBalance,
        source = BankSource.DAVIPLATA,
        confidence = confidence
    )

    private fun transfer(
        amount: Double,
        merchant: String?,
        availableBalance: Double?,
        confidence: Double
    ) = ParsedTransaction(
        type = TransactionType.TRANSFERENCIA,
        amount = amount,
        merchant = merchant,
        availableBalance = availableBalance,
        source = BankSource.DAVIPLATA,
        confidence = confidence
    )
}
