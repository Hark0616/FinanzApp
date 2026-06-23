package com.ivan.finanzapp.domain.usecase

import kotlin.math.abs

object PaymentMatchScorer {
    private const val EXACT_RATIO = 0.005
    private const val NEAR_RATIO = 0.02
    private const val EXACT_FLOOR_COP = 1_000.0
    private const val NEAR_FLOOR_COP = 5_000.0

    fun score(
        actualAmount: Double,
        expectedAmount: Double,
        targetName: String,
        evidenceText: String?
    ): PaymentMatchScore? {
        if (actualAmount <= 0.0 || expectedAmount <= 0.0) return null

        val difference = abs(actualAmount - expectedAmount)
        val exactTolerance = maxOf(EXACT_FLOOR_COP, expectedAmount * EXACT_RATIO)
        val nearTolerance = maxOf(NEAR_FLOOR_COP, expectedAmount * NEAR_RATIO)

        val baseConfidence = when {
            difference <= exactTolerance -> 0.94
            difference <= nearTolerance -> 0.82
            else -> return null
        }

        val hintBonus = if (hasMeaningfulNameOverlap(targetName, evidenceText.orEmpty())) 0.04 else 0.0
        val confidence = (baseConfidence + hintBonus).coerceAtMost(0.99)

        return PaymentMatchScore(
            differenceAmount = difference,
            confidence = confidence,
            isExactAmount = difference <= exactTolerance
        )
    }

    private fun hasMeaningfulNameOverlap(targetName: String, evidenceText: String): Boolean {
        val evidence = normalize(evidenceText)
        if (evidence.isBlank()) return false

        return normalize(targetName)
            .split(" ")
            .filter { it.length >= 3 }
            .any { token -> evidence.contains(token) }
    }

    private fun normalize(value: String): String =
        value.uppercase()
            .replace(Regex("""[^A-Z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}

data class PaymentMatchScore(
    val differenceAmount: Double,
    val confidence: Double,
    val isExactAmount: Boolean
)
