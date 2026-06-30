package com.ivan.finanzapp.ui.creditcard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.dashboard.CreditCardSummary
import com.ivan.finanzapp.ui.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreditCardsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val creditCardDao: CreditCardDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val calculator: CreditCardCalculator,
    private val cloudSyncScheduler: CloudSyncScheduler
) : ViewModel() {

    val uiState: StateFlow<CreditCardsUiState> = combine(
        combine(creditCardDao.observeAll(), deferredPurchaseDao.observeAll()) { cards, purchases ->
            cards to purchases
        },
        accountDao.observeAccounts()
    ) { cardsAndPurchases, accounts ->
        val (cards, allDeferredPurchases) = cardsAndPurchases
        val accountMap = accounts.associateBy { it.id }
        val purchasesByCard = allDeferredPurchases.groupBy { it.creditCardId }

        val cardSummaries = cards.mapNotNull { card ->
            val account = accountMap[card.accountId] ?: return@mapNotNull null
            val cardPurchases = purchasesByCard[card.id] ?: emptyList()
            CreditCardSummary(
                card = card,
                account = account,
                availableCredit = calculator.availableCredit(card),
                usagePercentage = calculator.usagePercentage(card),
                minimumPayment = calculator.minimumPayment(card, cardPurchases),
                daysUntilDue = calculator.daysUntilPaymentDue(card),
                usageLevel = calculator.usageTrafficLight(card).name,
                deferredPurchases = cardPurchases,
                totalMonthlyInstallments = calculator.totalMonthlyInstallments(cardPurchases, card.interestRateEA),
                activeDeferredCount = cardPurchases.count { calculator.remainingInstallments(it) > 0 }
            )
        }

        val savingsAccounts = accounts.filter { it.type != AccountType.TARJETA_CREDITO }

        CreditCardsUiState(
            isLoading = false,
            creditCards = cardSummaries,
            accounts = savingsAccounts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreditCardsUiState()
    )

    fun addDeferredPurchase(
        cardId: String,
        description: String,
        totalAmount: Double,
        totalInstallments: Int,
        paidInstallments: Int,
        purchaseDate: Long,
        interestRateEA: Double? = null
    ) {
        viewModelScope.launch {
            database.withTransaction {
                val purchase = DeferredPurchaseEntity(
                    id = UUID.randomUUID().toString(),
                    creditCardId = cardId,
                    description = description,
                    totalAmount = totalAmount,
                    totalInstallments = totalInstallments,
                    paidInstallments = paidInstallments,
                    purchaseDate = purchaseDate,
                    interestRateEA = interestRateEA
                )
                deferredPurchaseDao.upsert(purchase)
                recalculateCardDebt(cardId)
            }
            refreshWidgets()
            cloudSyncScheduler.syncSoon()
        }
    }

    fun deleteDeferredPurchase(purchaseId: String, cardId: String) {
        viewModelScope.launch {
            database.withTransaction {
                deferredPurchaseDao.delete(purchaseId)
                recalculateCardDebt(cardId)
            }
            refreshWidgets()
            cloudSyncScheduler.syncSoon()
        }
    }

    fun markInstallmentPaid(purchaseId: String, cardId: String) {
        viewModelScope.launch {
            database.withTransaction {
                deferredPurchaseDao.incrementPaidInstallment(purchaseId)
                deferredPurchaseDao.deleteIfFullyPaid(purchaseId)
                recalculateCardDebt(cardId)
            }
            refreshWidgets()
            cloudSyncScheduler.syncSoon()
        }
    }

    private suspend fun recalculateCardDebt(cardId: String) {
        val purchases = deferredPurchaseDao.getByCardIdSnapshot(cardId)
        val card = creditCardDao.getById(cardId) ?: return
        val newDebt = calculator.totalDeferredDebt(purchases)
        val updatedCard = card.copy(currentDebt = newDebt)
        creditCardDao.update(updatedCard)
    }


    fun payCreditCard(card: CreditCardEntity, paymentAmount: Double, fundingAccountId: String?) {
        viewModelScope.launch {
            database.withTransaction {
                // 1. Registrar el abono en la tarjeta de crédito
                val paymentTxId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val cardTx = TransactionEntity(
                    id = paymentTxId,
                    accountId = card.accountId,
                    amount = paymentAmount,
                    type = TransactionType.PAGO_TC,
                    merchant = "Abono Tarjeta",
                    categoryId = null,
                    rawNotification = "Abono manual registrado en app",
                    timestamp = now,
                    confirmedByAI = false,
                    needsReview = false
                )
                transactionDao.insertIfNotExists(cardTx)

                // Distribuir el abono entre las compras diferidas
                val purchases = deferredPurchaseDao.getByCardIdSnapshot(card.id)
                val result = calculator.distributePayment(paymentAmount, purchases)
                for (updated in result.updatedPurchases) {
                    deferredPurchaseDao.upsert(updated)
                }
                for (deletedId in result.deletedPurchaseIds) {
                    deferredPurchaseDao.delete(deletedId)
                }
                recalculateCardDebt(card.id)

                // 2. Si se usó una cuenta de fondos, es traslado de caja contra deuda,
                // no consumo nuevo. El gasto fue la compra original con TC.
                fundingAccountId?.let { accountId ->
                    val fundingTxId = UUID.randomUUID().toString()
                    val fundingTx = TransactionEntity(
                        id = fundingTxId,
                        accountId = accountId,
                        amount = paymentAmount,
                        type = TransactionType.TRANSFERENCIA,
                        merchant = "Pago Tarjeta de Crédito",
                        categoryId = null,
                        rawNotification = "Pago de tarjeta de crédito registrado en app",
                        timestamp = now,
                        confirmedByAI = false,
                        needsReview = false
                    )
                    transactionDao.insertIfNotExists(fundingTx)
                    accountDao.adjustBalance(accountId, -paymentAmount)
                }
            }
            refreshWidgets()
            cloudSyncScheduler.syncSoon()
        }
    }

    fun updateDeferredPurchase(
        purchaseId: String,
        cardId: String,
        description: String,
        totalAmount: Double,
        totalInstallments: Int,
        paidInstallments: Int,
        purchaseDate: Long,
        interestRateEA: Double?
    ) {
        viewModelScope.launch {
            database.withTransaction {
                val purchase = DeferredPurchaseEntity(
                    id = purchaseId,
                    creditCardId = cardId,
                    description = description,
                    totalAmount = totalAmount,
                    totalInstallments = totalInstallments,
                    paidInstallments = paidInstallments,
                    purchaseDate = purchaseDate,
                    interestRateEA = interestRateEA
                )
                deferredPurchaseDao.upsert(purchase)
                recalculateCardDebt(cardId)
            }
            refreshWidgets()
            cloudSyncScheduler.syncSoon()
        }
    }

    private fun refreshWidgets() {
        WidgetUpdater.updateAllWidgets(context, debounceMillis = 0L)
    }
}
