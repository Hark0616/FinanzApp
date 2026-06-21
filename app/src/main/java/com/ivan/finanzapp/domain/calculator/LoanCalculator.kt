package com.ivan.finanzapp.domain.calculator

import com.ivan.finanzapp.data.local.entity.LoanEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculos financieros puros para creditos y prestamos.
 */
@Singleton
class LoanCalculator @Inject constructor() {

    fun monthlyInterestAmount(remainingAmount: Double, monthlyInterestRate: Double): Double {
        if (remainingAmount <= 0.0) return 0.0
        val monthlyRate = monthlyInterestRate.coerceAtLeast(0.0) / 100.0
        return remainingAmount * monthlyRate
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

    fun applyInstallmentPayment(loan: LoanEntity): LoanPaymentProgress {
        if (loan.remainingAmount <= 0.0 || loan.monthlyInstallmentAmount <= 0.0) {
            return LoanPaymentProgress(
                remainingAmount = loan.remainingAmount.coerceAtLeast(0.0),
                paidInstallments = loan.paidInstallments,
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
                paymentAmount = 0.0
            )
        }

        val scheduledInsurance = loan.monthlyInsuranceAmount.coerceAtLeast(0.0)
        val scheduledFee = loan.monthlyFeeAmount.coerceAtLeast(0.0)
        val availablePayment = loan.monthlyInstallmentAmount.coerceAtLeast(0.0)

        val insurancePaid = scheduledInsurance.coerceAtMost(availablePayment)
        val afterInsurance = (availablePayment - insurancePaid).coerceAtLeast(0.0)
        val feePaid = scheduledFee.coerceAtMost(afterInsurance)
        val afterFixedCharges = (afterInsurance - feePaid).coerceAtLeast(0.0)

        val interestAccrued = monthlyInterestAmount(loan.remainingAmount, loan.monthlyInterestRate)
        val interestPaid = interestAccrued.coerceAtMost(afterFixedCharges)
        val principalPaid = principalPaid(
            remainingAmount = loan.remainingAmount,
            monthlyInterestRate = loan.monthlyInterestRate,
            monthlyInstallmentAmount = loan.monthlyInstallmentAmount,
            monthlyInsuranceAmount = scheduledInsurance,
            monthlyFeeAmount = scheduledFee
        )

        return LoanPaymentProgress(
            remainingAmount = (loan.remainingAmount - principalPaid).coerceAtLeast(0.0),
            paidInstallments = loan.paidInstallments + 1,
            interestAccrued = interestAccrued,
            scheduledInsurance = scheduledInsurance,
            insurancePaid = insurancePaid,
            unpaidInsurance = (scheduledInsurance - insurancePaid).coerceAtLeast(0.0),
            scheduledFee = scheduledFee,
            feePaid = feePaid,
            unpaidFee = (scheduledFee - feePaid).coerceAtLeast(0.0),
            interestPaid = interestPaid,
            unpaidInterest = (interestAccrued - interestPaid).coerceAtLeast(0.0),
            principalPaid = principalPaid,
            paymentAmount = insurancePaid + feePaid + interestPaid + principalPaid
        )
    }
}

data class LoanPaymentProgress(
    val remainingAmount: Double,
    val paidInstallments: Int,
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
    val paymentAmount: Double
)
