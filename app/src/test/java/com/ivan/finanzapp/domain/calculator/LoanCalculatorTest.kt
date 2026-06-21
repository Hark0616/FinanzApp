package com.ivan.finanzapp.domain.calculator

import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.domain.model.LoanAmortizationType
import com.ivan.finanzapp.domain.model.LoanInterestRateType
import org.junit.Assert.assertEquals
import org.junit.Test

class LoanCalculatorTest {

    private val calculator = LoanCalculator()

    @Test
    fun normalizeMonthlyInterestRateKeepsMonthlyEffectiveRate() {
        val monthlyRate = calculator.normalizeMonthlyInterestRate(
            interestRateInputValue = 2.0,
            interestRateType = LoanInterestRateType.MONTHLY_EFFECTIVE
        )

        assertEquals(2.0, monthlyRate, MONEY_DELTA)
    }

    @Test
    fun normalizeMonthlyInterestRateConvertsEffectiveAnnualRate() {
        val monthlyRate = calculator.normalizeMonthlyInterestRate(
            interestRateInputValue = 26.824179456,
            interestRateType = LoanInterestRateType.EFFECTIVE_ANNUAL
        )

        assertEquals(2.0, monthlyRate, RATE_DELTA)
    }

    @Test
    fun normalizeMonthlyInterestRateConvertsNominalAnnualMonthlyRate() {
        val monthlyRate = calculator.normalizeMonthlyInterestRate(
            interestRateInputValue = 24.0,
            interestRateType = LoanInterestRateType.NOMINAL_ANNUAL_MONTHLY
        )

        assertEquals(2.0, monthlyRate, MONEY_DELTA)
    }

    @Test
    fun monthlyInterestAmountUsesRemainingDebtAndMonthlyRate() {
        val interest = calculator.monthlyInterestAmount(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0
        )

        assertEquals(20_000.0, interest, MONEY_DELTA)
    }

    @Test
    fun monthlyInterestAmountIsZeroForClosedLoan() {
        val interest = calculator.monthlyInterestAmount(
            remainingAmount = 0.0,
            monthlyInterestRate = 2.0
        )

        assertEquals(0.0, interest, MONEY_DELTA)
    }

    @Test
    fun fixedPrincipalAmountDividesDebtAcrossInstallments() {
        val principal = calculator.fixedPrincipalAmount(
            totalAmount = 1_200_000.0,
            totalInstallments = 12
        )

        assertEquals(100_000.0, principal, MONEY_DELTA)
    }

    @Test
    fun fixedPrincipalAmountIsZeroForInvalidTerms() {
        assertEquals(0.0, calculator.fixedPrincipalAmount(1_200_000.0, 0), MONEY_DELTA)
        assertEquals(0.0, calculator.fixedPrincipalAmount(0.0, 12), MONEY_DELTA)
    }

    @Test
    fun principalPaidSubtractsInterestFromInstallment() {
        val principal = calculator.principalPaid(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 100_000.0
        )

        assertEquals(80_000.0, principal, MONEY_DELTA)
    }

    @Test
    fun principalPaidSubtractsInsuranceAndFeesBeforePrincipal() {
        val principal = calculator.principalPaid(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 100_000.0,
            monthlyInsuranceAmount = 10_000.0,
            monthlyFeeAmount = 5_000.0
        )

        assertEquals(65_000.0, principal, MONEY_DELTA)
    }

    @Test
    fun principalPaidUsesFullInstallmentWhenInterestRateIsZero() {
        val principal = calculator.principalPaid(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 0.0,
            monthlyInstallmentAmount = 100_000.0
        )

        assertEquals(100_000.0, principal, MONEY_DELTA)
    }

    @Test
    fun principalPaidNeverExceedsRemainingDebt() {
        val principal = calculator.principalPaid(
            remainingAmount = 50_000.0,
            monthlyInterestRate = 0.0,
            monthlyInstallmentAmount = 100_000.0
        )

        assertEquals(50_000.0, principal, MONEY_DELTA)
    }

    @Test
    fun principalPaidIsZeroWhenInstallmentDoesNotCoverInterest() {
        val principal = calculator.principalPaid(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 15.0,
            monthlyInstallmentAmount = 100_000.0
        )

        assertEquals(0.0, principal, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentReducesRemainingDebtByPrincipalOnly() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 100_000.0,
            paidInstallments = 3
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(920_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(4, progress.paidInstallments)
        assertEquals(20_000.0, progress.interestAccrued, MONEY_DELTA)
        assertEquals(20_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidInterest, MONEY_DELTA)
        assertEquals(80_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(100_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentKeepsInsuranceAndFeesOutOfPrincipal() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 100_000.0,
            monthlyInsuranceAmount = 10_000.0,
            monthlyFeeAmount = 5_000.0
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(935_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(10_000.0, progress.scheduledInsurance, MONEY_DELTA)
        assertEquals(10_000.0, progress.insurancePaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidInsurance, MONEY_DELTA)
        assertEquals(5_000.0, progress.scheduledFee, MONEY_DELTA)
        assertEquals(5_000.0, progress.feePaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidFee, MONEY_DELTA)
        assertEquals(20_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(65_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(100_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentAppliesAmountAboveInstallmentToExtraPrincipal() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 100_000.0
        )

        val progress = calculator.applyInstallmentPayment(
            loan = loan,
            actualPaymentAmount = 110_000.0
        )

        assertEquals(910_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(100_000.0, progress.scheduledPaymentAmount, MONEY_DELTA)
        assertEquals(110_000.0, progress.paymentAmount, MONEY_DELTA)
        assertEquals(20_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(90_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(10_000.0, progress.extraPrincipalAmount, MONEY_DELTA)
        assertEquals(0.0, progress.unappliedPaymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentTracksUnappliedAmountWhenPaymentExceedsClosure() {
        val loan = loan(
            remainingAmount = 50_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 40_000.0
        )

        val progress = calculator.applyInstallmentPayment(
            loan = loan,
            actualPaymentAmount = 80_000.0
        )

        assertEquals(0.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(40_000.0, progress.scheduledPaymentAmount, MONEY_DELTA)
        assertEquals(51_000.0, progress.paymentAmount, MONEY_DELTA)
        assertEquals(1_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(50_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(11_000.0, progress.extraPrincipalAmount, MONEY_DELTA)
        assertEquals(29_000.0, progress.unappliedPaymentAmount, MONEY_DELTA)
    }

    @Test
    fun scheduledPaymentAmountForFixedPrincipalIncludesChargesInterestAndPrincipal() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 0.0,
            fixedPrincipalAmount = 80_000.0,
            monthlyInsuranceAmount = 10_000.0,
            monthlyFeeAmount = 5_000.0,
            amortizationType = LoanAmortizationType.FIXED_PRINCIPAL
        )

        val paymentAmount = calculator.scheduledPaymentAmount(loan)

        assertEquals(115_000.0, paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentForFixedPrincipalReducesPrincipalByFixedAmount() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 0.0,
            fixedPrincipalAmount = 80_000.0,
            monthlyInsuranceAmount = 10_000.0,
            monthlyFeeAmount = 5_000.0,
            amortizationType = LoanAmortizationType.FIXED_PRINCIPAL
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(920_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(115_000.0, progress.scheduledPaymentAmount, MONEY_DELTA)
        assertEquals(10_000.0, progress.insurancePaid, MONEY_DELTA)
        assertEquals(5_000.0, progress.feePaid, MONEY_DELTA)
        assertEquals(20_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(80_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(115_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentForFixedPrincipalCapsFinalPrincipalPayment() {
        val loan = loan(
            remainingAmount = 50_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 0.0,
            fixedPrincipalAmount = 80_000.0,
            amortizationType = LoanAmortizationType.FIXED_PRINCIPAL
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(0.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(51_000.0, progress.scheduledPaymentAmount, MONEY_DELTA)
        assertEquals(1_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(50_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(51_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentForFixedPrincipalDoesNotAdvanceWithoutPrincipalAmount() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 0.0,
            fixedPrincipalAmount = 0.0,
            amortizationType = LoanAmortizationType.FIXED_PRINCIPAL,
            paidInstallments = 4
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(1_000_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(4, progress.paidInstallments)
        assertEquals(0.0, progress.scheduledPaymentAmount, MONEY_DELTA)
        assertEquals(0.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentTracksUnpaidFixedChargesBeforeInterest() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 12_000.0,
            monthlyInsuranceAmount = 10_000.0,
            monthlyFeeAmount = 5_000.0
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(1_000_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(10_000.0, progress.insurancePaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidInsurance, MONEY_DELTA)
        assertEquals(2_000.0, progress.feePaid, MONEY_DELTA)
        assertEquals(3_000.0, progress.unpaidFee, MONEY_DELTA)
        assertEquals(20_000.0, progress.interestAccrued, MONEY_DELTA)
        assertEquals(0.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(20_000.0, progress.unpaidInterest, MONEY_DELTA)
        assertEquals(0.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(12_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentClosesLoanAndCapsPaymentAmountWhenPrincipalCoversRemainingDebt() {
        val loan = loan(
            remainingAmount = 50_000.0,
            monthlyInterestRate = 0.0,
            monthlyInstallmentAmount = 100_000.0
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(0.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(1, progress.paidInstallments)
        assertEquals(0.0, progress.interestAccrued, MONEY_DELTA)
        assertEquals(0.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidInterest, MONEY_DELTA)
        assertEquals(50_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(50_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentCapsFinalPaymentAmountAfterInterest() {
        val loan = loan(
            remainingAmount = 50_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 100_000.0
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(0.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(1_000.0, progress.interestAccrued, MONEY_DELTA)
        assertEquals(1_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidInterest, MONEY_DELTA)
        assertEquals(50_000.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(51_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentTracksUnpaidInterestWhenInstallmentIsTooLow() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 15.0,
            monthlyInstallmentAmount = 100_000.0
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(1_000_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(150_000.0, progress.interestAccrued, MONEY_DELTA)
        assertEquals(100_000.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(50_000.0, progress.unpaidInterest, MONEY_DELTA)
        assertEquals(0.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(100_000.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentDoesNotAdvanceClosedLoan() {
        val loan = loan(
            remainingAmount = 0.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 100_000.0,
            paidInstallments = 12
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(0.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(12, progress.paidInstallments)
        assertEquals(0.0, progress.interestAccrued, MONEY_DELTA)
        assertEquals(0.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidInterest, MONEY_DELTA)
        assertEquals(0.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(0.0, progress.paymentAmount, MONEY_DELTA)
    }

    @Test
    fun applyInstallmentPaymentDoesNotAdvanceWhenInstallmentIsInvalid() {
        val loan = loan(
            remainingAmount = 1_000_000.0,
            monthlyInterestRate = 2.0,
            monthlyInstallmentAmount = 0.0,
            paidInstallments = 3
        )

        val progress = calculator.applyInstallmentPayment(loan)

        assertEquals(1_000_000.0, progress.remainingAmount, MONEY_DELTA)
        assertEquals(3, progress.paidInstallments)
        assertEquals(0.0, progress.interestAccrued, MONEY_DELTA)
        assertEquals(0.0, progress.interestPaid, MONEY_DELTA)
        assertEquals(0.0, progress.unpaidInterest, MONEY_DELTA)
        assertEquals(0.0, progress.principalPaid, MONEY_DELTA)
        assertEquals(0.0, progress.paymentAmount, MONEY_DELTA)
    }

    private fun loan(
        remainingAmount: Double,
        monthlyInterestRate: Double,
        monthlyInstallmentAmount: Double,
        fixedPrincipalAmount: Double = 0.0,
        monthlyInsuranceAmount: Double = 0.0,
        monthlyFeeAmount: Double = 0.0,
        amortizationType: LoanAmortizationType = LoanAmortizationType.FIXED_INSTALLMENT,
        paidInstallments: Int = 0
    ) = LoanEntity(
        id = "loan",
        name = "Loan",
        totalAmount = 1_000_000.0,
        remainingAmount = remainingAmount,
        monthlyInterestRate = monthlyInterestRate,
        amortizationType = amortizationType,
        totalInstallments = 12,
        paidInstallments = paidInstallments,
        monthlyInstallmentAmount = monthlyInstallmentAmount,
        fixedPrincipalAmount = fixedPrincipalAmount,
        monthlyInsuranceAmount = monthlyInsuranceAmount,
        monthlyFeeAmount = monthlyFeeAmount,
        paymentDay = 15,
        nextPaymentDate = 0L
    )

    private companion object {
        const val MONEY_DELTA = 0.0001
        const val RATE_DELTA = 0.000001
    }
}
