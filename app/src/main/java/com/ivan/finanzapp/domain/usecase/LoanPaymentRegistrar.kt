package com.ivan.finanzapp.domain.usecase

import androidx.room.withTransaction
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.LoanPaymentDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.calculator.LoanCalculator
import com.ivan.finanzapp.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoanPaymentRegistrar @Inject constructor(
    private val database: AppDatabase,
    private val loanDao: LoanDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val loanCalculator: LoanCalculator
) {

    suspend fun registerInstallmentPayment(
        loanId: String,
        paymentDateMillis: Long = System.currentTimeMillis(),
        actualPaymentAmount: Double? = null
    ): LoanPaymentRegistration? {
        return database.withTransaction {
            val loan = loanDao.getLoanById(loanId) ?: return@withTransaction null
            val paymentProgress = loanCalculator.applyInstallmentPayment(
                loan = loan,
                actualPaymentAmount = actualPaymentAmount
            )
            if (paymentProgress.paymentAmount <= 0.0) return@withTransaction null

            val nextPaymentDateMillis = nextPaymentDateAfter(loan.nextPaymentDate)
            val transactionId = UUID.randomUUID().toString()
            val paymentId = UUID.randomUUID().toString()

            val transaction = TransactionEntity(
                id = transactionId,
                accountId = loan.linkedAccountId,
                amount = paymentProgress.paymentAmount,
                type = TransactionType.GASTO,
                merchant = "Pago Cuota: ${loan.name}",
                categoryId = null,
                rawNotification = "Pago de cuota manual registrado en app",
                timestamp = paymentDateMillis,
                confirmedByAI = false,
                needsReview = false
            )

            val loanPayment = LoanPaymentEntity(
                id = paymentId,
                loanId = loan.id,
                transactionId = transactionId,
                installmentNumber = paymentProgress.paidInstallments,
                scheduledPaymentAmount = paymentProgress.scheduledPaymentAmount,
                actualPaymentAmount = paymentProgress.paymentAmount,
                scheduledInsuranceAmount = paymentProgress.scheduledInsurance,
                insurancePaidAmount = paymentProgress.insurancePaid,
                unpaidInsuranceAmount = paymentProgress.unpaidInsurance,
                scheduledFeeAmount = paymentProgress.scheduledFee,
                feePaidAmount = paymentProgress.feePaid,
                unpaidFeeAmount = paymentProgress.unpaidFee,
                interestAccruedAmount = paymentProgress.interestAccrued,
                interestPaidAmount = paymentProgress.interestPaid,
                unpaidInterestAmount = paymentProgress.unpaidInterest,
                principalAmount = paymentProgress.principalPaid,
                extraPrincipalAmount = paymentProgress.extraPrincipalAmount,
                unappliedPaymentAmount = paymentProgress.unappliedPaymentAmount,
                remainingAmountBefore = loan.remainingAmount,
                remainingAmountAfter = paymentProgress.remainingAmount,
                paymentDate = paymentDateMillis,
                createdAt = paymentDateMillis
            )

            loanDao.updateLoanPaymentProgress(
                loanId = loan.id,
                remainingAmount = paymentProgress.remainingAmount,
                paidInstallments = paymentProgress.paidInstallments,
                nextPaymentDate = nextPaymentDateMillis
            )
            transactionDao.insert(transaction)
            loanPaymentDao.insert(loanPayment)
            loan.linkedAccountId?.let { accountId ->
                accountDao.adjustBalance(accountId, -paymentProgress.paymentAmount)
            }

            LoanPaymentRegistration(
                payment = loanPayment,
                transaction = transaction
            )
        }
    }

    private fun nextPaymentDateAfter(currentPaymentDateMillis: Long): Long {
        return Instant.ofEpochMilli(currentPaymentDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusMonths(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}

data class LoanPaymentRegistration(
    val payment: LoanPaymentEntity,
    val transaction: TransactionEntity
)
