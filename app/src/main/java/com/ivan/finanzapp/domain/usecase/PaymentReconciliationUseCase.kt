package com.ivan.finanzapp.domain.usecase

import androidx.room.withTransaction
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DebtPaymentApplicationDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.LoanPaymentDao
import com.ivan.finanzapp.data.local.dao.PaymentMatchSuggestionDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.DebtPaymentApplicationEntity
import com.ivan.finanzapp.data.local.entity.DebtPaymentApplicationType
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import com.ivan.finanzapp.data.local.entity.PaymentMatchStatus
import com.ivan.finanzapp.data.local.entity.PaymentMatchSuggestionEntity
import com.ivan.finanzapp.data.local.entity.PaymentMatchTargetType
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.calculator.LoanCalculator
import com.ivan.finanzapp.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentReconciliationUseCase @Inject constructor(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val suggestionDao: PaymentMatchSuggestionDao,
    private val applicationDao: DebtPaymentApplicationDao,
    private val cardCalculator: CreditCardCalculator,
    private val loanCalculator: LoanCalculator,
    private val cloudSyncScheduler: CloudSyncScheduler
) {

    suspend fun generateSuggestionsForTransaction(transaction: TransactionEntity) {
        if (!transaction.isOutgoingDebtPaymentCandidate()) return
        if (applicationDao.getBySourceTransactionId(transaction.id) != null) return
        if (suggestionDao.getBySourceTransactionIdAndStatus(transaction.id, PaymentMatchStatus.PENDING).isNotEmpty()) return

        val evidenceText = "${transaction.merchant.orEmpty()} ${transaction.rawNotification}"
        val now = System.currentTimeMillis()

        val suggestions = buildList {
            addAll(buildCreditCardSuggestions(transaction, evidenceText, now))
            addAll(buildLoanSuggestions(transaction, evidenceText, now))
        }
            .filter { it.confidence >= MIN_CONFIDENCE }
            .sortedByDescending { it.confidence }
            .take(MAX_SUGGESTIONS_PER_TRANSACTION)

        for (suggestion in suggestions) {
            suggestionDao.insertIgnore(suggestion)
        }
    }

    suspend fun acceptSuggestion(suggestionId: String): Result<Unit> {
        return runCatching {
            database.withTransaction {
                val suggestion = suggestionDao.getById(suggestionId)
                    ?: error("La sugerencia ya no existe.")
                if (suggestion.status != PaymentMatchStatus.PENDING) {
                    error("La sugerencia ya fue procesada.")
                }
                if (applicationDao.getBySourceTransactionId(suggestion.sourceTransactionId) != null) {
                    error("Este movimiento ya fue aplicado a una deuda.")
                }
                val transaction = transactionDao.getById(suggestion.sourceTransactionId)
                    ?: error("El movimiento fuente ya no existe.")

                val applicationId = UUID.randomUUID().toString()
                val applicationType = when (suggestion.targetType) {
                    PaymentMatchTargetType.CREDIT_CARD ->
                        applyCreditCardPayment(suggestion, transaction)
                    PaymentMatchTargetType.LOAN ->
                        applyLoanPayment(applicationId, suggestion, transaction)
                }
                markSourceTransactionAsDebtTransfer(transaction)

                val application = DebtPaymentApplicationEntity(
                    id = applicationId,
                    sourceTransactionId = transaction.id,
                    suggestionId = suggestion.id,
                    targetType = suggestion.targetType,
                    targetId = suggestion.targetId,
                    targetName = suggestion.targetName,
                    amount = transaction.amount,
                    expectedAmount = suggestion.expectedAmount,
                    differenceAmount = suggestion.differenceAmount,
                    applicationType = applicationType
                )
                applicationDao.insert(application)

                val now = System.currentTimeMillis()
                suggestionDao.updateStatus(
                    id = suggestion.id,
                    status = PaymentMatchStatus.ACCEPTED,
                    updatedAt = now,
                    acceptedApplicationId = applicationId
                )
                suggestionDao.updateOtherPendingForTransaction(
                    transactionId = transaction.id,
                    acceptedSuggestionId = suggestion.id,
                    status = PaymentMatchStatus.REJECTED,
                    updatedAt = now
                )
            }
            cloudSyncScheduler.syncSoon()
        }
    }

    private suspend fun markSourceTransactionAsDebtTransfer(transaction: TransactionEntity) {
        if (transaction.type == TransactionType.GASTO) {
            transactionDao.update(transaction.copy(type = TransactionType.TRANSFERENCIA))
        }
    }

    suspend fun rejectSuggestion(suggestionId: String): Result<Unit> {
        return runCatching {
            suggestionDao.updateStatus(
                id = suggestionId,
                status = PaymentMatchStatus.REJECTED,
                updatedAt = System.currentTimeMillis()
            )
            cloudSyncScheduler.syncSoon()
        }
    }

    private suspend fun buildCreditCardSuggestions(
        transaction: TransactionEntity,
        evidenceText: String,
        now: Long
    ): List<PaymentMatchSuggestionEntity> {
        val accounts = accountDao.getAllAccountsSnapshot().associateBy { it.id }
        return creditCardDao.getAllSnapshot().flatMap { card ->
            val account = accounts[card.accountId] ?: return@flatMap emptyList()
            val targetName = account.name
            val purchases = deferredPurchaseDao.getByCardIdSnapshot(card.id)
            val billingCutoffDate = cardCalculator.billingCutoffDateForPayment(card, transaction.toLocalDate())
            val statementAmount = cardCalculator.minimumPayment(card, purchases, billingCutoffDate)
            val fullDebtAmount = card.currentDebt.coerceAtLeast(0.0)

            buildList {
                addSuggestionIfMatches(
                    transaction = transaction,
                    targetType = PaymentMatchTargetType.CREDIT_CARD,
                    targetId = card.id,
                    targetName = targetName,
                    expectedAmount = statementAmount,
                    evidenceText = evidenceText,
                    now = now,
                    reason = "Coincide con la cuota estimada del ciclo de la tarjeta $targetName"
                )?.let(::add)

                if (fullDebtAmount > 0.0 && fullDebtAmount != statementAmount) {
                    addSuggestionIfMatches(
                        transaction = transaction,
                        targetType = PaymentMatchTargetType.CREDIT_CARD,
                        targetId = card.id,
                        targetName = targetName,
                        expectedAmount = fullDebtAmount,
                        evidenceText = evidenceText,
                        now = now,
                        reason = "Coincide con la deuda total registrada de la tarjeta $targetName"
                    )?.let(::add)
                }
            }
        }
    }

    private suspend fun buildLoanSuggestions(
        transaction: TransactionEntity,
        evidenceText: String,
        now: Long
    ): List<PaymentMatchSuggestionEntity> {
        return loanDao.getAllLoansSnapshot()
            .filter { it.remainingAmount > 0.0 }
            .mapNotNull { loan ->
                val expectedAmount = loanCalculator.scheduledPaymentAmount(loan)
                addSuggestionIfMatches(
                    transaction = transaction,
                    targetType = PaymentMatchTargetType.LOAN,
                    targetId = loan.id,
                    targetName = loan.name,
                    expectedAmount = expectedAmount,
                    evidenceText = evidenceText,
                    now = now,
                    reason = "Coincide con la cuota programada del crédito ${loan.name}"
                )
            }
    }

    private fun addSuggestionIfMatches(
        transaction: TransactionEntity,
        targetType: PaymentMatchTargetType,
        targetId: String,
        targetName: String,
        expectedAmount: Double,
        evidenceText: String,
        now: Long,
        reason: String
    ): PaymentMatchSuggestionEntity? {
        val score = PaymentMatchScorer.score(
            actualAmount = transaction.amount,
            expectedAmount = expectedAmount,
            targetName = targetName,
            evidenceText = evidenceText
        ) ?: return null

        return PaymentMatchSuggestionEntity(
            id = "${transaction.id}:${targetType.name}:$targetId",
            sourceTransactionId = transaction.id,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            expectedAmount = expectedAmount,
            actualAmount = transaction.amount,
            differenceAmount = score.differenceAmount,
            confidence = score.confidence,
            reason = if (score.isExactAmount) reason else "$reason con diferencia menor al umbral permitido",
            createdAt = now,
            updatedAt = now
        )
    }

    private suspend fun applyCreditCardPayment(
        suggestion: PaymentMatchSuggestionEntity,
        transaction: TransactionEntity
    ): DebtPaymentApplicationType {
        val card = creditCardDao.getById(suggestion.targetId)
            ?: error("La tarjeta sugerida ya no existe.")
        val purchases = deferredPurchaseDao.getByCardIdSnapshot(card.id)

        val applicationType = cardApplicationType(card, purchases, transaction.amount, transaction.timestamp)
        if (purchases.isEmpty()) {
            val updatedDebt = (card.currentDebt - transaction.amount).coerceAtLeast(0.0)
            creditCardDao.update(card.copy(currentDebt = updatedDebt))
            return applicationType
        }

        val billingCutoffDate = cardCalculator.billingCutoffDateForPayment(card, transaction.toLocalDate())
        val result = if (applicationType == DebtPaymentApplicationType.CARD_STATEMENT_PAYMENT) {
            cardCalculator.distributeStatementPayment(
                paymentAmount = transaction.amount,
                card = card,
                purchases = purchases,
                billingCutoffDate = billingCutoffDate
            )
        } else {
            cardCalculator.distributePayment(transaction.amount, purchases)
        }
        for (updated in result.updatedPurchases) {
            deferredPurchaseDao.upsert(updated)
        }
        for (deletedId in result.deletedPurchaseIds) {
            deferredPurchaseDao.delete(deletedId)
        }
        recalculateCardDebt(card.id)
        return applicationType
    }

    private suspend fun applyLoanPayment(
        applicationId: String,
        suggestion: PaymentMatchSuggestionEntity,
        transaction: TransactionEntity
    ): DebtPaymentApplicationType {
        val loan = loanDao.getLoanById(suggestion.targetId)
            ?: error("El crédito sugerido ya no existe.")
        val paymentProgress = loanCalculator.applyInstallmentPayment(
            loan = loan,
            actualPaymentAmount = transaction.amount
        )
        if (paymentProgress.paymentAmount <= 0.0) {
            error("El pago no pudo aplicarse al crédito.")
        }

        val paymentDate = transaction.timestamp
        val loanPayment = LoanPaymentEntity(
            id = applicationId,
            loanId = loan.id,
            transactionId = transaction.id,
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
            paymentDate = paymentDate,
            createdAt = System.currentTimeMillis()
        )

        loanDao.updateLoanPaymentProgress(
            loanId = loan.id,
            remainingAmount = paymentProgress.remainingAmount,
            paidInstallments = paymentProgress.paidInstallments,
            nextPaymentDate = nextPaymentDateAfter(loan.nextPaymentDate)
        )
        loanPaymentDao.insert(loanPayment)
        return DebtPaymentApplicationType.LOAN_INSTALLMENT
    }

    private suspend fun recalculateCardDebt(cardId: String) {
        val purchases = deferredPurchaseDao.getByCardIdSnapshot(cardId)
        val card = creditCardDao.getById(cardId) ?: return
        creditCardDao.update(card.copy(currentDebt = cardCalculator.totalDeferredDebt(purchases)))
    }

    private fun cardApplicationType(
        card: CreditCardEntity,
        purchases: List<DeferredPurchaseEntity>,
        paymentAmount: Double,
        paymentDateMillis: Long
    ): DebtPaymentApplicationType {
        val statementCutoff = cardCalculator.billingCutoffDateForPayment(
            card = card,
            paymentDate = paymentDateMillis.toLocalDate()
        )
        val statementAmount = cardCalculator.minimumPayment(card, purchases, statementCutoff)
        val fullDebt = card.currentDebt.coerceAtLeast(0.0)
        return when {
            PaymentMatchScorer.score(paymentAmount, fullDebt, "", null) != null ->
                DebtPaymentApplicationType.CARD_FULL_PAYMENT
            PaymentMatchScorer.score(paymentAmount, statementAmount, "", null) != null ->
                DebtPaymentApplicationType.CARD_STATEMENT_PAYMENT
            else -> DebtPaymentApplicationType.CARD_EXTRA_PAYMENT
        }
    }

    private fun TransactionEntity.toLocalDate() = timestamp.toLocalDate()

    private fun Long.toLocalDate() = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    private fun nextPaymentDateAfter(currentPaymentDateMillis: Long): Long {
        return Instant.ofEpochMilli(currentPaymentDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusMonths(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun TransactionEntity.isOutgoingDebtPaymentCandidate(): Boolean =
        amount > 0.0 && (type == TransactionType.TRANSFERENCIA || type == TransactionType.GASTO)

    private companion object {
        const val MIN_CONFIDENCE = 0.82
        const val MAX_SUGGESTIONS_PER_TRANSACTION = 3
    }
}
