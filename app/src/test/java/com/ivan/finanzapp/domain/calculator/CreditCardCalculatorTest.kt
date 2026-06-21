package com.ivan.finanzapp.domain.calculator

import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCardCalculatorTest {

    private val calculator = CreditCardCalculator()

    @Test
    fun availableCreditNeverGoesBelowZero() {
        val card = creditCard(creditLimit = 1_000_000.0, currentDebt = 1_250_000.0)

        assertEquals(0.0, calculator.availableCredit(card), MONEY_DELTA)
    }

    @Test
    fun usagePercentageIsClampedBetweenZeroAndOneHundred() {
        val zeroLimitCard = creditCard(creditLimit = 0.0, currentDebt = 500_000.0)
        val overLimitCard = creditCard(creditLimit = 1_000_000.0, currentDebt = 1_500_000.0)

        assertEquals(0.0, calculator.usagePercentage(zeroLimitCard), MONEY_DELTA)
        assertEquals(100.0, calculator.usagePercentage(overLimitCard), MONEY_DELTA)
    }

    @Test
    fun usageTrafficLightUsesCurrentUsageThresholds() {
        assertEquals(CreditCardCalculator.UsageLevel.LOW, calculator.usageTrafficLight(creditCard(currentDebt = 299_999.0)))
        assertEquals(CreditCardCalculator.UsageLevel.MEDIUM, calculator.usageTrafficLight(creditCard(currentDebt = 300_000.0)))
        assertEquals(CreditCardCalculator.UsageLevel.HIGH, calculator.usageTrafficLight(creditCard(currentDebt = 700_000.0)))
    }

    @Test
    fun installmentAmountUsesLinearCapitalAmortization() {
        val purchase = deferredPurchase(totalAmount = 1_200_000.0, totalInstallments = 12)

        assertEquals(100_000.0, calculator.installmentAmount(purchase), MONEY_DELTA)
    }

    @Test
    fun installmentAmountReturnsZeroWhenInstallmentCountIsInvalid() {
        val purchase = deferredPurchase(totalAmount = 1_200_000.0, totalInstallments = 0)

        assertEquals(0.0, calculator.installmentAmount(purchase), MONEY_DELTA)
    }

    @Test
    fun remainingInstallmentsAndDebtDoNotGoNegative() {
        val overpaidPurchase = deferredPurchase(
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 15
        )

        assertEquals(0, calculator.remainingInstallments(overpaidPurchase))
        assertEquals(0.0, calculator.remainingDebt(overpaidPurchase), MONEY_DELTA)
    }

    @Test
    fun installmentAmountWithInterestUsesPurchaseRateBeforeCardRate() {
        val purchase = deferredPurchase(
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 2,
            interestRateEA = 12.0
        )
        val monthlyRate = Math.pow(1.0 + 12.0 / 100.0, 1.0 / 12.0) - 1.0
        val expected = 100_000.0 + (1_000_000.0 * monthlyRate)

        assertEquals(expected, calculator.installmentAmount(purchase, cardInterestRateEA = 30.0), MONEY_DELTA)
    }

    @Test
    fun totalMonthlyInstallmentsIgnoresFullyPaidPurchases() {
        val active = deferredPurchase(
            id = "active",
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 11
        )
        val paid = deferredPurchase(
            id = "paid",
            totalAmount = 600_000.0,
            totalInstallments = 6,
            paidInstallments = 6
        )

        assertEquals(100_000.0, calculator.totalMonthlyInstallments(listOf(active, paid), null), MONEY_DELTA)
    }

    @Test
    fun totalDeferredDebtSumsRemainingCapitalOnly() {
        val first = deferredPurchase(id = "first", totalAmount = 1_200_000.0, totalInstallments = 12, paidInstallments = 2)
        val second = deferredPurchase(id = "second", totalAmount = 600_000.0, totalInstallments = 6, paidInstallments = 1)

        assertEquals(1_500_000.0, calculator.totalDeferredDebt(listOf(first, second)), MONEY_DELTA)
    }

    @Test
    fun firstCutoffAfterKeepsCurrentMonthWhenDateIsOnCutoff() {
        val cutoff = calculator.firstCutoffAfter(LocalDate.of(2026, 6, 15), cutoffDay = 15)

        assertEquals(LocalDate.of(2026, 6, 15), cutoff)
    }

    @Test
    fun firstCutoffAfterMovesToNextMonthWhenDateIsAfterCutoff() {
        val cutoff = calculator.firstCutoffAfter(LocalDate.of(2026, 6, 16), cutoffDay = 15)

        assertEquals(LocalDate.of(2026, 7, 15), cutoff)
    }

    @Test
    fun firstCutoffAfterUsesLastDayForShortMonths() {
        val cutoff = calculator.firstCutoffAfter(LocalDate.of(2026, 2, 10), cutoffDay = 31)

        assertEquals(LocalDate.of(2026, 2, 28), cutoff)
    }

    @Test
    fun billedInstallmentsCountsCutoffsThroughTargetDate() {
        val billed = calculator.billedInstallments(
            purchaseDate = LocalDate.of(2026, 1, 10),
            cutoffDay = 15,
            targetDate = LocalDate.of(2026, 3, 15),
            totalInstallments = 12
        )

        assertEquals(3, billed)
    }

    @Test
    fun billedInstallmentsReturnsZeroForFuturePurchase() {
        val billed = calculator.billedInstallments(
            purchaseDate = LocalDate.of(2026, 4, 1),
            cutoffDay = 15,
            targetDate = LocalDate.of(2026, 3, 15),
            totalInstallments = 12
        )

        assertEquals(0, billed)
    }

    @Test
    fun billedInstallmentsIsCappedByTotalInstallments() {
        val billed = calculator.billedInstallments(
            purchaseDate = LocalDate.of(2026, 1, 10),
            cutoffDay = 15,
            targetDate = LocalDate.of(2027, 1, 15),
            totalInstallments = 3
        )

        assertEquals(3, billed)
    }

    @Test
    fun installmentsDueSubtractsPaidInstallmentsFromBilledInstallments() {
        val purchase = deferredPurchase(
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 1,
            purchaseDate = LocalDate.of(2026, 1, 10).toEpochMillis()
        )

        val due = calculator.installmentsDue(purchase, cutoffDay = 15, targetDate = LocalDate.of(2026, 3, 15))

        assertEquals(2, due)
    }

    @Test
    fun amountDueMultipliesDueInstallmentsByCurrentInstallmentAmount() {
        val purchase = deferredPurchase(
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 1,
            purchaseDate = LocalDate.of(2026, 1, 10).toEpochMillis()
        )

        val amountDue = calculator.amountDue(
            purchase = purchase,
            cardInterestRateEA = null,
            cutoffDay = 15,
            targetDate = LocalDate.of(2026, 3, 15)
        )

        assertEquals(200_000.0, amountDue, MONEY_DELTA)
    }

    @Test
    fun minimumPaymentReturnsZeroWhenCardHasNoDebt() {
        val card = creditCard(currentDebt = 0.0)

        assertEquals(0.0, calculator.minimumPayment(card), MONEY_DELTA)
    }

    @Test
    fun minimumPaymentUsesConfiguredPercentageOfCurrentDebt() {
        val card = creditCard(
            currentDebt = 1_000_000.0,
            minPaymentPercentage = 10.0,
            minPaymentFloor = 0.0
        )

        assertEquals(100_000.0, calculator.minimumPayment(card), MONEY_DELTA)
    }

    @Test
    fun minimumPaymentUsesConfiguredFloorWhenPercentageIsLower() {
        val card = creditCard(
            currentDebt = 100_000.0,
            minPaymentPercentage = 5.0,
            minPaymentFloor = 20_000.0
        )

        assertEquals(20_000.0, calculator.minimumPayment(card), MONEY_DELTA)
    }

    @Test
    fun minimumPaymentNeverExceedsCurrentDebt() {
        val card = creditCard(
            currentDebt = 12_000.0,
            minPaymentPercentage = 5.0,
            minPaymentFloor = 20_000.0
        )

        assertEquals(12_000.0, calculator.minimumPayment(card), MONEY_DELTA)
    }

    @Test
    fun projectedMonthlyInterestReturnsNullWhenCardHasNoRate() {
        val card = creditCard(interestRateEA = null)

        assertNull(calculator.projectedMonthlyInterest(card))
    }

    @Test
    fun projectedMonthlyInterestUsesEffectiveAnnualRate() {
        val card = creditCard(currentDebt = 1_000_000.0, interestRateEA = 12.0)
        val expected = 1_000_000.0 * (Math.pow(1.0 + 12.0 / 100.0, 1.0 / 12.0) - 1.0)

        assertEquals(expected, calculator.projectedMonthlyInterest(card) ?: 0.0, MONEY_DELTA)
    }

    @Test
    fun distributePaymentPaysOldestPurchasesFirst() {
        val newer = deferredPurchase(id = "newer", totalAmount = 600_000.0, totalInstallments = 6, createdAt = 2_000L)
        val older = deferredPurchase(id = "older", totalAmount = 1_200_000.0, totalInstallments = 12, createdAt = 1_000L)

        val result = calculator.distributePayment(paymentAmount = 100_000.0, purchases = listOf(newer, older))

        val updatedOlder = result.updatedPurchases.first { it.id == "older" }
        val updatedNewer = result.updatedPurchases.first { it.id == "newer" }

        assertEquals(1, updatedOlder.paidInstallments)
        assertEquals(0, updatedNewer.paidInstallments)
        assertTrue(result.deletedPurchaseIds.isEmpty())
    }

    @Test
    fun distributePaymentAppliesFractionalRemainderWithoutChangingDebtReduction() {
        val purchase = deferredPurchase(
            id = "purchase",
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 0
        )

        val result = calculator.distributePayment(paymentAmount = 250_000.0, purchases = listOf(purchase))

        val updatedPurchase = result.updatedPurchases.single()

        assertEquals(2, updatedPurchase.paidInstallments)
        assertEquals(1_140_000.0, updatedPurchase.totalAmount, MONEY_DELTA)
        assertEquals(950_000.0, calculator.remainingDebt(updatedPurchase), MONEY_DELTA)
        assertTrue(result.deletedPurchaseIds.isEmpty())
    }

    @Test
    fun distributePaymentDeletesFullyPaidPurchases() {
        val purchase = deferredPurchase(
            id = "purchase",
            totalAmount = 1_200_000.0,
            totalInstallments = 12,
            paidInstallments = 0
        )

        val result = calculator.distributePayment(paymentAmount = 1_200_000.0, purchases = listOf(purchase))

        assertTrue(result.updatedPurchases.isEmpty())
        assertEquals(listOf("purchase"), result.deletedPurchaseIds)
    }

    @Test
    fun distributePaymentKeepsActivePurchasesWhenPaymentIsExhausted() {
        val first = deferredPurchase(id = "first", totalAmount = 1_200_000.0, totalInstallments = 12, createdAt = 1_000L)
        val second = deferredPurchase(id = "second", totalAmount = 600_000.0, totalInstallments = 6, createdAt = 2_000L)

        val result = calculator.distributePayment(paymentAmount = 100_000.0, purchases = listOf(first, second))

        assertEquals(setOf("first", "second"), result.updatedPurchases.map { it.id }.toSet())
        assertTrue(result.deletedPurchaseIds.isEmpty())
    }

    private fun creditCard(
        id: String = "card",
        accountId: String = "account",
        creditLimit: Double = 1_000_000.0,
        currentDebt: Double = 0.0,
        cutoffDay: Int = 15,
        paymentDueDay: Int = 30,
        minPaymentPercentage: Double = 5.0,
        minPaymentFloor: Double = 0.0,
        interestRateEA: Double? = null
    ) = CreditCardEntity(
        id = id,
        accountId = accountId,
        creditLimit = creditLimit,
        currentDebt = currentDebt,
        cutoffDay = cutoffDay,
        paymentDueDay = paymentDueDay,
        minPaymentPercentage = minPaymentPercentage,
        minPaymentFloor = minPaymentFloor,
        interestRateEA = interestRateEA
    )

    private fun deferredPurchase(
        id: String = "purchase",
        creditCardId: String = "card",
        description: String = "Purchase",
        totalAmount: Double = 1_200_000.0,
        totalInstallments: Int = 12,
        paidInstallments: Int = 0,
        purchaseDate: Long = LocalDate.of(2026, 1, 10).toEpochMillis(),
        interestRateEA: Double? = null,
        createdAt: Long = 1_000L
    ) = DeferredPurchaseEntity(
        id = id,
        creditCardId = creditCardId,
        description = description,
        totalAmount = totalAmount,
        totalInstallments = totalInstallments,
        paidInstallments = paidInstallments,
        purchaseDate = purchaseDate,
        interestRateEA = interestRateEA,
        createdAt = createdAt
    )

    private fun LocalDate.toEpochMillis(): Long =
        atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val MONEY_DELTA = 0.0001
    }
}
