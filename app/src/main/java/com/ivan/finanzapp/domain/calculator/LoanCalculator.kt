package com.ivan.finanzapp.domain.calculator

import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.domain.model.LoanAmortizationType
import com.ivan.finanzapp.domain.model.LoanInterestRateType
import kotlin.math.pow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculos financieros puros para creditos y prestamos.
 */
@Singleton
class LoanCalculator @Inject constructor() {

    fun normalizeMonthlyInterestRate(
        interestRateInputValue: Double,
        interestRateType: LoanInterestRateType
    ): Double {
        val rate = interestRateInputValue.coerceAtLeast(0.0)
        return when (interestRateType) {
            LoanInterestRateType.MONTHLY_EFFECTIVE -> rate
            LoanInterestRateType.EFFECTIVE_ANNUAL -> ((1.0 + rate / 100.0).pow(1.0 / 12.0) - 1.0) * 100.0
            LoanInterestRateType.NOMINAL_ANNUAL_MONTHLY -> rate / 12.0
        }
    }

    fun monthlyInterestAmount(remainingAmount: Double, monthlyInterestRate: Double): Double {
        if (remainingAmount <= 0.0) return 0.0
        val monthlyRate = monthlyInterestRate.coerceAtLeast(0.0) / 100.0
        return remainingAmount * monthlyRate
    }

    fun fixedPrincipalAmount(totalAmount: Double, totalInstallments: Int): Double {
        if (totalAmount <= 0.0 || totalInstallments <= 0) return 0.0
        return totalAmount / totalInstallments
    }

    fun principalPaid(
        remainingAmount: Double,
        monthlyInterestRate: Double,
        monthlyInstallmentAmount: Double,
        monthlyInsuranceAmount: Double = 0.0,
        monthlyFeeAmount: Double = 0.0
    ): Double {
        if (remainingAmount <= 0.0 || monthlyInstallmentAmount <= 0.0) return 0.0
        val interest = monthlyInterestAmount(remainingAmount, monthlyInterestRate)
        val fixedCharges = monthlyInsuranceAmount.coerceAtLeast(0.0) + monthlyFeeAmount.coerceAtLeast(0.0)
        return (monthlyInstallmentAmount - fixedCharges - interest)
            .coerceAtLeast(0.0)
            .coerceAtMost(remainingAmount)
    }

    fun scheduledPaymentAmount(loan: LoanEntity): Double {
        if (loan.remainingAmount <= 0.0) return 0.0

        val fixedCharges = loan.monthlyInsuranceAmount.coerceAtLeast(0.0) +
                loan.monthlyFeeAmount.coerceAtLeast(0.0)
        return when (loan.amortizationType) {
            LoanAmortizationType.FIXED_INSTALLMENT ->
                loan.monthlyInstallmentAmount.coerceAtLeast(0.0)

            LoanAmortizationType.FIXED_PRINCIPAL -> {
                val interest = monthlyInterestAmount(loan.remainingAmount, loan.monthlyInterestRate)
                val principal = loan.fixedPrincipalAmount.coerceAtLeast(0.0)
                    .coerceAtMost(loan.remainingAmount)
                fixedCharges + interest + principal
            }
        }
    }

    fun applyInstallmentPayment(
        loan: LoanEntity,
        actualPaymentAmount: Double? = null
    ): LoanPaymentProgress {
        val scheduledPaymentAmount = scheduledPaymentAmount(loan)
        val requestedPaymentAmount = actualPaymentAmount
            ?.coerceAtLeast(0.0)
            ?: scheduledPaymentAmount
        val hasValidPaymentTerms = when (loan.amortizationType) {
            LoanAmortizationType.FIXED_INSTALLMENT -> loan.monthlyInstallmentAmount > 0.0
            LoanAmortizationType.FIXED_PRINCIPAL -> loan.fixedPrincipalAmount > 0.0
        }
        if (
            loan.remainingAmount <= 0.0 ||
            scheduledPaymentAmount <= 0.0 ||
            requestedPaymentAmount <= 0.0 ||
            !hasValidPaymentTerms
        ) {
            return LoanPaymentProgress(
                remainingAmount = loan.remainingAmount.coerceAtLeast(0.0),
                paidInstallments = loan.paidInstallments,
                scheduledPaymentAmount = 0.0,
                interestAccrued = 0.0,
                scheduledInsurance = 0.0,
                insurancePaid = 0.0,
                unpaidInsurance = 0.0,
                scheduledFee = 0.0,
                feePaid = 0.0,
                unpaidFee = 0.0,
                interestPaid = 0.0,
                unpaidInterest = 0.0,
                principalPaid = 0.0,
                extraPrincipalAmount = 0.0,
                unappliedPaymentAmount = 0.0,
                paymentAmount = 0.0
            )
        }

        val scheduledAllocation = allocatePayment(loan, scheduledPaymentAmount)
        val actualAllocation = allocatePayment(loan, requestedPaymentAmount)
        val extraPrincipalAmount = (actualAllocation.principalPaid - scheduledAllocation.principalPaid)
            .coerceAtLeast(0.0)

        return LoanPaymentProgress(
            remainingAmount = (loan.remainingAmount - actualAllocation.principalPaid).coerceAtLeast(0.0),
            paidInstallments = loan.paidInstallments + 1,
            scheduledPaymentAmount = scheduledPaymentAmount,
            interestAccrued = actualAllocation.interestAccrued,
            scheduledInsurance = actualAllocation.scheduledInsurance,
            insurancePaid = actualAllocation.insurancePaid,
            unpaidInsurance = actualAllocation.unpaidInsurance,
            scheduledFee = actualAllocation.scheduledFee,
            feePaid = actualAllocation.feePaid,
            unpaidFee = actualAllocation.unpaidFee,
            interestPaid = actualAllocation.interestPaid,
            unpaidInterest = actualAllocation.unpaidInterest,
            principalPaid = actualAllocation.principalPaid,
            extraPrincipalAmount = extraPrincipalAmount,
            unappliedPaymentAmount = actualAllocation.unappliedPaymentAmount,
            paymentAmount = actualAllocation.paymentAmount
        )
    }

    private fun allocatePayment(
        loan: LoanEntity,
        paymentAmount: Double
    ): LoanPaymentAllocation {
        val scheduledInsurance = loan.monthlyInsuranceAmount.coerceAtLeast(0.0)
        val scheduledFee = loan.monthlyFeeAmount.coerceAtLeast(0.0)
        val availablePayment = paymentAmount.coerceAtLeast(0.0)

        val insurancePaid = scheduledInsurance.coerceAtMost(availablePayment)
        val afterInsurance = (availablePayment - insurancePaid).coerceAtLeast(0.0)
        val feePaid = scheduledFee.coerceAtMost(afterInsurance)
        val afterFixedCharges = (afterInsurance - feePaid).coerceAtLeast(0.0)

        val interestAccrued = monthlyInterestAmount(loan.remainingAmount, loan.monthlyInterestRate)
        val interestPaid = interestAccrued.coerceAtMost(afterFixedCharges)
        val afterInterest = (afterFixedCharges - interestPaid).coerceAtLeast(0.0)
        val principalPaid = afterInterest.coerceAtMost(loan.remainingAmount)
        val appliedPaymentAmount = insurancePaid + feePaid + interestPaid + principalPaid

        return LoanPaymentAllocation(
            scheduledInsurance = scheduledInsurance,
            insurancePaid = insurancePaid,
            unpaidInsurance = (scheduledInsurance - insurancePaid).coerceAtLeast(0.0),
            scheduledFee = scheduledFee,
            feePaid = feePaid,
            unpaidFee = (scheduledFee - feePaid).coerceAtLeast(0.0),
            interestAccrued = interestAccrued,
            interestPaid = interestPaid,
            unpaidInterest = (interestAccrued - interestPaid).coerceAtLeast(0.0),
            principalPaid = principalPaid,
            unappliedPaymentAmount = (availablePayment - appliedPaymentAmount).coerceAtLeast(0.0),
            paymentAmount = appliedPaymentAmount
        )
    }
}

private data class LoanPaymentAllocation(
    val scheduledInsurance: Double,
    val insurancePaid: Double,
    val unpaidInsurance: Double,
    val scheduledFee: Double,
    val feePaid: Double,
    val unpaidFee: Double,
    val interestAccrued: Double,
    val interestPaid: Double,
    val unpaidInterest: Double,
    val principalPaid: Double,
    val unappliedPaymentAmount: Double,
    val paymentAmount: Double
)

data class LoanPaymentProgress(
    val remainingAmount: Double,
    val paidInstallments: Int,
    val scheduledPaymentAmount: Double,
    val interestAccrued: Double,
    val scheduledInsurance: Double,
    val insurancePaid: Double,
    val unpaidInsurance: Double,
    val scheduledFee: Double,
    val feePaid: Double,
    val unpaidFee: Double,
    val interestPaid: Double,
    val unpaidInterest: Double,
    val principalPaid: Double,
    val extraPrincipalAmount: Double,
    val unappliedPaymentAmount: Double,
    val paymentAmount: Double
)
