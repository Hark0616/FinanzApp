package com.ivan.finanzapp.data.notification

import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationAiGate @Inject constructor() {

    fun evaluate(packageName: String, title: String, text: String): AiGateDecision {
        val normalized = normalize("$title $text")
        if (normalized.isBlank()) {
            return AiGateDecision(false, "empty_notification")
        }

        val hasSecurityIntent = SECURITY_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasPromoIntent = PROMO_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasStatementIntent = STATEMENT_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasTransactionAction = TRANSACTION_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasMoneySignal = MONEY_PATTERNS.any { it.containsMatchIn(normalized) }
        val hasAmountLikeNumber = AMOUNT_PATTERN.containsMatchIn(normalized)

        if (hasSecurityIntent) {
            return AiGateDecision(false, "security_or_otp_notification")
        }

        if (hasPromoIntent) {
            return AiGateDecision(false, "promotional_notification")
        }

        if (hasStatementIntent) {
            return AiGateDecision(false, "statement_or_summary_notification")
        }

        if (hasTransactionAction && (hasMoneySignal || hasAmountLikeNumber)) {
            return AiGateDecision(true, "transaction_action_with_amount")
        }

        return AiGateDecision(false, "not_transaction_candidate")
    }

    private fun normalize(raw: String): String {
        val decomposed = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    private companion object {
        val SECURITY_PATTERNS = listOf(
            Regex("""\b(codigo|clave|otp|pin|token|verificacion|verifica|autenticacion)\b"""),
            Regex("""\b(inicio de sesion|sesion iniciada|login|acceso|dispositivo nuevo|aprobar)\b""")
        )

        val PROMO_PATTERNS = listOf(
            Regex("""\b(promocion|oferta|gana|participa|referido|beneficio|publicidad|campana)\b""")
        )

        val STATEMENT_PATTERNS = listOf(
            Regex("""\b(extracto|estado de cuenta|resumen|saldo disponible|saldo es|saldo actual)\b""")
        )

        val TRANSACTION_PATTERNS = listOf(
            Regex("""\b(compra|compraste|pagaste|pago|pagado|cargo|cobro|debito|debitaron|descontaron|retiro|retiraste|gastaste|consumiste)\b"""),
            Regex("""\b(transferencia|transferiste|enviaste|envio|enviado|recibiste|recibido|abono)\b"""),
            Regex("""\b(consignacion|deposito|recarga|movimiento|transaccion|aprobada|rechazada)\b""")
        )

        val MONEY_PATTERNS = listOf(
            Regex("""\$"""),
            Regex("""\b(cop|pesos?|usd|dolares?|valor|monto|por)\b""")
        )

        val AMOUNT_PATTERN = Regex(
            """(?<!\d)(?:\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{2})?|\d{4,})(?!\d)"""
        )

    }
}

data class AiGateDecision(
    val shouldAnalyze: Boolean,
    val reason: String
)
