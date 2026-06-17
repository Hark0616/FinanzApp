package com.ivan.finanzapp.ui.creditcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.dashboard.CreditCardSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreditCardsViewModel @Inject constructor(
    private val creditCardDao: CreditCardDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val calculator: CreditCardCalculator
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
                totalMonthlyInstallments = calculator.totalMonthlyInstallments(cardPurchases),
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
        purchaseDate: Long
    ) {
        viewModelScope.launch {
            val purchase = DeferredPurchaseEntity(
                id = UUID.randomUUID().toString(),
                creditCardId = cardId,
                description = description,
                totalAmount = totalAmount,
                totalInstallments = totalInstallments,
                paidInstallments = paidInstallments,
                purchaseDate = purchaseDate
            )
            deferredPurchaseDao.upsert(purchase)
            recalculateCardDebt(cardId)
        }
    }

    fun deleteDeferredPurchase(purchaseId: String, cardId: String) {
        viewModelScope.launch {
            deferredPurchaseDao.delete(purchaseId)
            recalculateCardDebt(cardId)
        }
    }

    fun markInstallmentPaid(purchaseId: String, cardId: String) {
        viewModelScope.launch {
            deferredPurchaseDao.incrementPaidInstallment(purchaseId)
            deferredPurchaseDao.deleteIfFullyPaid(purchaseId)
            recalculateCardDebt(cardId)
        }
    }

    private suspend fun recalculateCardDebt(cardId: String) {
        val purchases = deferredPurchaseDao.observeByCardId(cardId).first()
        val card = creditCardDao.observeAll().first().find { it.id == cardId } ?: return
        val newDebt = calculator.totalDeferredDebt(purchases)
        val updatedCard = card.copy(currentDebt = newDebt)
        creditCardDao.update(updatedCard)
    }


    fun payCreditCard(card: CreditCardEntity, paymentAmount: Double, fundingAccountId: String?) {
        viewModelScope.launch {
            // 1. Registrar el abono en la tarjeta de crédito
            val paymentTxId = UUID.randomUUID().toString()
            val cardTx = TransactionEntity(
                id = paymentTxId,
                accountId = card.accountId,
                amount = paymentAmount,
                type = TransactionType.PAGO_TC,
                merchant = "Abono Tarjeta",
                categoryId = null,
                rawNotification = "Abono manual registrado en app",
                timestamp = System.currentTimeMillis(),
                confirmedByAI = false,
                needsReview = false
            )
            transactionDao.insertIfNotExists(cardTx)

            // Distribuir el abono entre las compras diferidas
            val purchases = deferredPurchaseDao.observeByCardId(card.id).first()
            val result = calculator.distributePayment(paymentAmount, purchases)
            for (updated in result.updatedPurchases) {
                deferredPurchaseDao.upsert(updated)
            }
            for (deletedId in result.deletedPurchaseIds) {
                deferredPurchaseDao.delete(deletedId)
            }
            recalculateCardDebt(card.id)

            // 2. Si se usó una cuenta de fondos (ahorros), debitar saldo y registrar transacción
            fundingAccountId?.let { accountId ->
                val fundingTxId = UUID.randomUUID().toString()
                val fundingTx = TransactionEntity(
                    id = fundingTxId,
                    accountId = accountId,
                    amount = paymentAmount,
                    type = TransactionType.GASTO,
                    merchant = "Pago Tarjeta de Crédito",
                    categoryId = null,
                    rawNotification = "Pago de tarjeta de crédito registrado en app",
                    timestamp = System.currentTimeMillis(),
                    confirmedByAI = false,
                    needsReview = false
                )
                transactionDao.insertIfNotExists(fundingTx)
                accountDao.adjustBalance(accountId, -paymentAmount)
            }
        }
    }

    fun updateDeferredPurchase(
        purchaseId: String,
        cardId: String,
        description: String,
        totalAmount: Double,
        totalInstallments: Int,
        paidInstallments: Int,
        purchaseDate: Long
    ) {
        viewModelScope.launch {
            val purchase = DeferredPurchaseEntity(
                id = purchaseId,
                creditCardId = cardId,
                description = description,
                totalAmount = totalAmount,
                totalInstallments = totalInstallments,
                paidInstallments = paidInstallments,
                purchaseDate = purchaseDate
            )
            deferredPurchaseDao.upsert(purchase)
            recalculateCardDebt(cardId)
        }
    }
}
