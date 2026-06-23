package com.ivan.finanzapp.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentMatchScorerTest {

    @Test
    fun exactAmountMatchGetsHighConfidence() {
        val score = PaymentMatchScorer.score(
            actualAmount = 959_000.0,
            expectedAmount = 959_000.0,
            targetName = "Prestamo Personal",
            evidenceText = "DAVIVIENDA Descuento Pago a Credito"
        )

        assertNotNull(score)
        assertEquals(0.0, score!!.differenceAmount, MONEY_DELTA)
        assertEquals(0.94, score.confidence, MONEY_DELTA)
        assertEquals(true, score.isExactAmount)
    }

    @Test
    fun targetNameOverlapAddsConfidenceWithoutChangingAmountDecision() {
        val score = PaymentMatchScorer.score(
            actualAmount = 3_667_203.0,
            expectedAmount = 3_667_203.0,
            targetName = "Credito Davivienda",
            evidenceText = "DAVIVIENDA Descuento Pago a Credito"
        )

        assertNotNull(score)
        assertEquals(0.98, score!!.confidence, MONEY_DELTA)
    }

    @Test
    fun nearAmountMatchUsesLowerConfidence() {
        val score = PaymentMatchScorer.score(
            actualAmount = 1_000_000.0,
            expectedAmount = 1_010_000.0,
            targetName = "Credito",
            evidenceText = null
        )

        assertNotNull(score)
        assertEquals(10_000.0, score!!.differenceAmount, MONEY_DELTA)
        assertEquals(0.82, score.confidence, MONEY_DELTA)
        assertEquals(false, score.isExactAmount)
    }

    @Test
    fun outsideToleranceDoesNotMatch() {
        val score = PaymentMatchScorer.score(
            actualAmount = 1_000_000.0,
            expectedAmount = 1_100_000.0,
            targetName = "Credito",
            evidenceText = null
        )

        assertNull(score)
    }

    private companion object {
        const val MONEY_DELTA = 0.0001
    }
}
