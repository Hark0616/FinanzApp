package com.ivan.finanzapp.domain.calculator

import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCardCalculatorTest {

    private val calculator = CreditCardCalculator()

    @Test
    fun statementPaymentWithInterestAdvancesInstallmentsWithoutReducingPrincipalByInterest() {
        val card = creditCard(currentDebt = 1_200_000.0, interestRateEA = 26.824179456)
        val purchase = deferredPurchase(
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 0,
            purchaseDate = localDateMillis(2026, 5, 1)
        )
        val billingCutoff = LocalDate.of(2026, 6, 10)

        val statementAmount = calculator.minimumPayment(card, listOf(purchase), billingCutoff)
        val result = calculator.distributeStatementPayment(
            paymentAmount = statementAmount,
            card = card,
            purchases = listOf(purchase),
            billingCutoffDate = billingCutoff
        )

        assertEquals(246_000.0, statementAmount, MONEY_DELTA)
        assertTrue(result.deletedPurchaseIds.isEmpty())
        assertEquals(1, result.updatedPurchases.size)
        assertEquals(2, result.updatedPurchases.single().paidInstallments)
        assertEquals(1_200_000.0, result.updatedPurchases.single().totalAmount, MONEY_DELTA)
        assertEquals(1_000_000.0, calculator.totalDeferredDebt(result.updatedPurchases), MONEY_DELTA)
    }

    @Test
    fun billingCutoffDateForPaymentUsesPaymentDateInsteadOfDeviceToday() {
        val card = creditCard(cutoffDay = 10, paymentDueDay = 25)

        val cutoffBeforeDue = calculator.billingCutoffDateForPayment(
            card = card,
            paymentDate = LocalDate.of(2026, 6, 20)
        )
        val cutoffAfterDue = calculator.billingCutoffDateForPayment(
            card = card,
            paymentDate = LocalDate.of(2026, 6, 26)
        )

        assertEquals(LocalDate.of(2026, 6, 10), cutoffBeforeDue)
        assertEquals(LocalDate.of(2026, 7, 10), cutoffAfterDue)
    }

    private fun creditCard(
        currentDebt: Double = 0.0,
        cutoffDay: Int = 10,
        paymentDueDay: Int = 25,
        interestRateEA: Double? = null
    ) = CreditCardEntity(
        id = "card",
        accountId = "account",
        creditLimit = 5_000_000.0,
        currentDebt = currentDebt,
        cutoffDay = cutoffDay,
        paymentDueDay = paymentDueDay,
        interestRateEA = interestRateEA
    )

    private fun deferredPurchase(
        totalAmount: Double,
        totalInstallments: Int,
        paidInstallments: Int,
        purchaseDate: Long
    ) = DeferredPurchaseEntity(
        id = "purchase",
        creditCardId = "card",
        description = "Celular",
        totalAmount = totalAmount,
        totalInstallments = totalInstallments,
        paidInstallments = paidInstallments,
        purchaseDate = purchaseDate,
        createdAt = purchaseDate
    )

    private fun localDateMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private companion object {
        const val MONEY_DELTA = 0.01
    }
}
