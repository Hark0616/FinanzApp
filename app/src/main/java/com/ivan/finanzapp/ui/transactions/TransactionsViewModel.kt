package com.ivan.finanzapp.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.PaymentMatchSuggestionDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.PaymentMatchStatus
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.domain.usecase.AddManualTransactionUseCase
import com.ivan.finanzapp.domain.usecase.PaymentReconciliationUseCase
import com.ivan.finanzapp.ui.dashboard.TransactionWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val paymentMatchSuggestionDao: PaymentMatchSuggestionDao,
    private val calculator: CreditCardCalculator,
    private val addManualTransactionUseCase: AddManualTransactionUseCase,
    private val paymentReconciliationUseCase: PaymentReconciliationUseCase,
    private val cloudSyncScheduler: CloudSyncScheduler
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionDao.observeAll(),
        categoryDao.observeAll(),
        accountDao.observeAccounts(),
        paymentMatchSuggestionDao.observeByStatus(PaymentMatchStatus.PENDING)
    ) { transactions, categories, accounts, paymentSuggestions ->
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        val paymentSuggestionsByTransaction = paymentSuggestions.groupBy { it.sourceTransactionId }

        val items = transactions.map { tx ->
            TransactionWithCategory(
                transaction = tx,
                category = tx.categoryId?.let { categoryMap[it] },
                accountName = tx.accountId?.let { accountMap[it]?.name }
            )
        }

        TransactionsUiState(
            isLoading = false,
            transactions = items,
            categories = categories,
            accounts = accounts,
            pendingPaymentSuggestions = paymentSuggestionsByTransaction
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState()
    )

    fun confirmTransaction(transactionId: String) {
        viewModelScope.launch {
            val tx = transactionDao.getById(transactionId) ?: return@launch
            transactionDao.update(tx.copy(needsReview = false))
            cloudSyncScheduler.syncSoon()
        }
    }

    fun updateTransactionCategory(transactionId: String, categoryId: String?) {
        viewModelScope.launch {
            val tx = transactionDao.getById(transactionId) ?: return@launch
            transactionDao.update(tx.copy(categoryId = categoryId, needsReview = false))
            cloudSyncScheduler.syncSoon()
        }
    }

    fun updateTransactionAccount(transactionId: String, newAccountId: String?) {
        viewModelScope.launch {
            database.withTransaction {
                val tx = transactionDao.getById(transactionId) ?: return@withTransaction
                val oldAccountId = tx.accountId

                if (oldAccountId == newAccountId) return@withTransaction

                // 1. Revertir el efecto en la cuenta anterior (si existía)
                if (oldAccountId != null) {
                    revertTransactionEffect(oldAccountId, tx.id, tx.type, tx.amount)
                }

                // 2. Determinar nuevo tipo de transacción y aplicar efecto en la nueva cuenta
                var newType = tx.type
                var resolvedNewAccountId: String? = null
                if (newAccountId != null) {
                    val newAccount = accountDao.getAccountById(newAccountId)
                    if (newAccount != null) {
                        resolvedNewAccountId = newAccountId
                        newType = if (newAccount.type == AccountType.TARJETA_CREDITO) {
                            if (tx.type == TransactionType.INGRESO || tx.type == TransactionType.PAGO_TC) {
                                TransactionType.PAGO_TC
                            } else {
                                TransactionType.GASTO_TC
                            }
                        } else if (tx.type == TransactionType.INGRESO || tx.type == TransactionType.PAGO_TC) {
                            TransactionType.INGRESO
                        } else {
                            TransactionType.GASTO
                        }

                        applyTransactionEffect(
                            accountId = newAccountId,
                            transactionId = tx.id,
                            type = newType,
                            amount = tx.amount,
                            merchant = tx.merchant ?: "Comercio clasificado",
                            timestamp = tx.timestamp
                        )
                    }
                }

                // 3. Actualizar la transacción
                transactionDao.update(tx.copy(accountId = resolvedNewAccountId, type = newType))
            }
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

    fun addManualTransaction(
        amount: Double,
        merchant: String,
        typeStr: String,
        accountId: String?,
        categoryId: String?
    ) {
        viewModelScope.launch {
            addManualTransactionUseCase(amount, merchant, typeStr, accountId, categoryId)
        }
    }

    fun acceptPaymentSuggestion(suggestionId: String) {
        viewModelScope.launch {
            paymentReconciliationUseCase.acceptSuggestion(suggestionId)
        }
    }

    fun rejectPaymentSuggestion(suggestionId: String) {
        viewModelScope.launch {
            paymentReconciliationUseCase.rejectSuggestion(suggestionId)
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            database.withTransaction {
                val tx = transactionDao.getById(transactionId)
                if (tx != null && tx.accountId != null) {
                    revertTransactionEffect(tx.accountId, tx.id, tx.type, tx.amount)
                }
                transactionDao.delete(transactionId)
            }
            cloudSyncScheduler.syncSoon()
        }
    }

    private suspend fun revertTransactionEffect(
        accountId: String,
        transactionId: String,
        type: TransactionType,
        amount: Double
    ) {
        val account = accountDao.getAccountById(accountId) ?: return
        if (account.type == AccountType.TARJETA_CREDITO) {
            val card = creditCardDao.getByAccountId(accountId) ?: return
            if (type == TransactionType.GASTO_TC) {
                deferredPurchaseDao.delete(transactionId)
                recalculateCardDebt(card.id)
            } else if (type == TransactionType.PAGO_TC) {
                creditCardDao.adjustDebt(card.id, amount)
            }
        } else if (type == TransactionType.INGRESO || type == TransactionType.PAGO_TC) {
            accountDao.adjustBalance(accountId, -amount)
        } else {
            accountDao.adjustBalance(accountId, +amount)
        }
    }

    private suspend fun applyTransactionEffect(
        accountId: String,
        transactionId: String,
        type: TransactionType,
        amount: Double,
        merchant: String,
        timestamp: Long
    ) {
        val account = accountDao.getAccountById(accountId) ?: return
        if (account.type == AccountType.TARJETA_CREDITO) {
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
        } else if (type == TransactionType.INGRESO) {
            accountDao.adjustBalance(accountId, +amount)
        } else {
            accountDao.adjustBalance(accountId, -amount)
        }
    }
}
