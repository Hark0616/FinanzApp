package com.ivan.finanzapp.domain.usecase

import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.security.SecureLog
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import android.content.Context
import androidx.room.withTransaction
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import androidx.glance.appwidget.updateAll
import com.ivan.finanzapp.ui.widget.FinanzAppWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

class AddManualTransactionUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val calculator: CreditCardCalculator,
    private val cloudSyncScheduler: CloudSyncScheduler
) {
    suspend operator fun invoke(
        amount: Double,
        merchant: String,
        typeStr: String,
        accountId: String?,
        categoryId: String?
    ) {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        database.withTransaction {
            val account = accountId?.let { accountDao.getAccountById(it) }
            val resolvedAccountId = account?.id
            val type = when {
                account?.type == AccountType.TARJETA_CREDITO ->
                    if (typeStr == "Ingreso") TransactionType.PAGO_TC else TransactionType.GASTO_TC
                typeStr == "Ingreso" -> TransactionType.INGRESO
                else -> TransactionType.GASTO
            }

            val entity = TransactionEntity(
                id = id,
                accountId = resolvedAccountId,
                amount = amount,
                type = type,
                merchant = merchant,
                categoryId = categoryId,
                rawNotification = "[Manual] $typeStr manual: $merchant",
                timestamp = timestamp,
                confirmedByAI = false,
                needsReview = false
            )

            val inserted = transactionDao.insertIfNotExists(entity)
            if (inserted != 0L) {
                account?.let { accountForSideEffects ->
                    applyFinancialSideEffects(
                        transactionId = id,
                        accountId = accountForSideEffects.id,
                        accountType = accountForSideEffects.type,
                        amount = amount,
                        merchant = merchant,
                        type = type,
                        timestamp = timestamp
                    )
                }
            }
        }
        
        try {
            FinanzAppWidget().updateAll(context)
        } catch (e: Throwable) {
            SecureLog.w("AddManualTransaction", "Widget update failed after manual transaction.", e)
        }

        cloudSyncScheduler.syncSoon()
    }

    private suspend fun applyFinancialSideEffects(
        transactionId: String,
        accountId: String,
        accountType: AccountType,
        amount: Double,
        merchant: String,
        type: TransactionType,
        timestamp: Long
    ) {
        if (accountType == AccountType.TARJETA_CREDITO) {
            val card = creditCardDao.getByAccountId(accountId) ?: return
            if (type == TransactionType.GASTO_TC) {
                val purchase = DeferredPurchaseEntity(
                    id = transactionId,
                    creditCardId = card.id,
                    description = merchant,
                    totalAmount = amount,
                    totalInstallments = 1,
                    paidInstallments = 0,
                    purchaseDate = timestamp
                )
                deferredPurchaseDao.upsert(purchase)
                recalculateCardDebt(card.id)
            } else if (type == TransactionType.PAGO_TC) {
                val purchases = deferredPurchaseDao.getByCardIdSnapshot(card.id)
                val result = calculator.distributePayment(amount, purchases)
                for (updated in result.updatedPurchases) {
                    deferredPurchaseDao.upsert(updated)
                }
                for (deletedId in result.deletedPurchaseIds) {
                    deferredPurchaseDao.delete(deletedId)
                }
                recalculateCardDebt(card.id)
            }
        } else {
            if (type == TransactionType.INGRESO) {
                accountDao.adjustBalance(accountId, +amount)
            } else {
                accountDao.adjustBalance(accountId, -amount)
            }
        }
    }

    private suspend fun recalculateCardDebt(cardId: String) {
        val purchases = deferredPurchaseDao.getByCardIdSnapshot(cardId)
        val card = creditCardDao.getById(cardId) ?: return
        val newDebt = calculator.totalDeferredDebt(purchases)
        val updatedCard = card.copy(currentDebt = newDebt)
        creditCardDao.update(updatedCard)
    }
}
