package com.ivan.finanzapp.domain.usecase

import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.ivan.finanzapp.ui.widget.FinanzAppWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class AddManualTransactionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val calculator: CreditCardCalculator
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

        // 1. Determinar el tipo de transacción correcto según el tipo de cuenta
        val type = if (accountId != null) {
            val account = accountDao.getAccountById(accountId)
            if (account?.type == AccountType.TARJETA_CREDITO) {
                if (typeStr == "Ingreso") TransactionType.PAGO_TC else TransactionType.GASTO_TC
            } else {
                if (typeStr == "Ingreso") TransactionType.INGRESO else TransactionType.GASTO
            }
        } else {
            if (typeStr == "Ingreso") TransactionType.INGRESO else TransactionType.GASTO
        }

        val entity = TransactionEntity(
            id = id,
            accountId = accountId,
            amount = amount,
            type = type,
            merchant = merchant,
            categoryId = categoryId,
            rawNotification = "[Manual] Ingreso manual",
            timestamp = timestamp,
            confirmedByAI = false,
            needsReview = false
        )

        val inserted = transactionDao.insertIfNotExists(entity)
        if (inserted == 0L) return

        // 2. Aplicar el impacto financiero de inmediato
        if (accountId != null) {
            val account = accountDao.getAccountById(accountId)
            if (account != null) {
                if (account.type == AccountType.TARJETA_CREDITO) {
                    val card = creditCardDao.getByAccountId(accountId)
                    if (card != null) {
                        if (type == TransactionType.GASTO_TC) {
                            val purchase = DeferredPurchaseEntity(
                                id = id,
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
                            val purchases = deferredPurchaseDao.observeByCardId(card.id).first()
                            val result = calculator.distributePayment(amount, purchases)
                            for (updated in result.updatedPurchases) {
                                deferredPurchaseDao.upsert(updated)
                            }
                            for (deletedId in result.deletedPurchaseIds) {
                                deferredPurchaseDao.delete(deletedId)
                            }
                            recalculateCardDebt(card.id)
                        }
                    }
                } else {
                    // Cuenta normal
                    if (type == TransactionType.INGRESO) {
                        accountDao.adjustBalance(accountId, +amount)
                    } else {
                        accountDao.adjustBalance(accountId, -amount)
                    }
                }
            }
        }
        
        try {
            FinanzAppWidget().updateAll(context)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private suspend fun recalculateCardDebt(cardId: String) {
        val purchases = deferredPurchaseDao.observeByCardId(cardId).first()
        val card = creditCardDao.observeAll().first().find { it.id == cardId } ?: return
        val newDebt = calculator.totalDeferredDebt(purchases)
        val updatedCard = card.copy(currentDebt = newDebt)
        creditCardDao.update(updatedCard)
    }
}
