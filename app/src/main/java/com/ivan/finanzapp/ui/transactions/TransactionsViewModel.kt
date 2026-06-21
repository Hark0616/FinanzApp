package com.ivan.finanzapp.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.dashboard.TransactionWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val calculator: CreditCardCalculator
) : ViewModel() {

    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionDao.observeAll(),
        categoryDao.observeAll(),
        accountDao.observeAccounts()
    ) { transactions, categories, accounts ->
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

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
            accounts = accounts
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
        }
    }

    fun updateTransactionCategory(transactionId: String, categoryId: String?) {
        viewModelScope.launch {
            val tx = transactionDao.getById(transactionId) ?: return@launch
            transactionDao.update(tx.copy(categoryId = categoryId, needsReview = false))
        }
    }

    fun updateTransactionAccount(transactionId: String, newAccountId: String?) {
        viewModelScope.launch {
            val tx = transactionDao.getById(transactionId) ?: return@launch
            val oldAccountId = tx.accountId

            if (oldAccountId == newAccountId) return@launch

            // 1. Revertir el efecto en la cuenta anterior (si existía)
            if (oldAccountId != null) {
                val oldAccount = accountDao.getAccountById(oldAccountId)
                if (oldAccount != null) {
                    if (oldAccount.type == AccountType.TARJETA_CREDITO) {
                        val card = creditCardDao.getByAccountId(oldAccountId)
                        if (card != null) {
                            if (tx.type == TransactionType.GASTO_TC) {
                                deferredPurchaseDao.delete(tx.id)
                                recalculateCardDebt(card.id)
                            } else if (tx.type == TransactionType.PAGO_TC) {
                                creditCardDao.adjustDebt(card.id, tx.amount)
                            }
                        }
                    } else {
                        // Cuenta normal
                        if (tx.type == TransactionType.INGRESO || tx.type == TransactionType.PAGO_TC) {
                            accountDao.adjustBalance(oldAccountId, -tx.amount)
                        } else {
                            accountDao.adjustBalance(oldAccountId, +tx.amount)
                        }
                    }
                }
            }

            // 2. Determinar nuevo tipo de transacción y aplicar efecto en la nueva cuenta
            var newType = tx.type
            if (newAccountId != null) {
                val newAccount = accountDao.getAccountById(newAccountId)
                if (newAccount != null) {
                    if (newAccount.type == AccountType.TARJETA_CREDITO) {
                        newType = if (tx.type == TransactionType.INGRESO || tx.type == TransactionType.PAGO_TC) {
                            TransactionType.PAGO_TC
                        } else {
                            TransactionType.GASTO_TC
                        }

                        val card = creditCardDao.getByAccountId(newAccountId)
                        if (card != null) {
                            if (newType == TransactionType.GASTO_TC) {
                                val purchase = DeferredPurchaseEntity(
                                    id = tx.id,
                                    creditCardId = card.id,
                                    description = tx.merchant ?: "Comercio clasificado",
                                    totalAmount = tx.amount,
                                    totalInstallments = 1,
                                    paidInstallments = 0,
                                    purchaseDate = tx.timestamp
                                )
                                deferredPurchaseDao.upsert(purchase)
                                recalculateCardDebt(card.id)
                            } else {
                                val purchases = deferredPurchaseDao.observeByCardId(card.id).first()
                                val result = calculator.distributePayment(tx.amount, purchases)
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
                        newType = if (tx.type == TransactionType.INGRESO || tx.type == TransactionType.PAGO_TC) {
                            TransactionType.INGRESO
                        } else {
                            TransactionType.GASTO
                        }

                        if (newType == TransactionType.INGRESO) {
                            accountDao.adjustBalance(newAccountId, +tx.amount)
                        } else {
                            accountDao.adjustBalance(newAccountId, -tx.amount)
                        }
                    }
                }
            }

            // 3. Actualizar la transacción
            transactionDao.update(tx.copy(accountId = newAccountId, type = newType))
        }
    }

    private suspend fun recalculateCardDebt(cardId: String) {
        val purchases = deferredPurchaseDao.observeByCardId(cardId).first()
        val card = creditCardDao.observeAll().first().find { it.id == cardId } ?: return
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
            val id = java.util.UUID.randomUUID().toString()
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

            val entity = com.ivan.finanzapp.data.local.entity.TransactionEntity(
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
            if (inserted == 0L) return@launch

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
                        // Cuenta normal (Ahorros, Efectivo, Nequi, etc.)
                        if (type == TransactionType.INGRESO) {
                            accountDao.adjustBalance(accountId, +amount)
                        } else {
                            accountDao.adjustBalance(accountId, -amount)
                        }
                    }
                }
            }
        }
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            val tx = transactionDao.getById(transactionId)
            if (tx != null && tx.accountId != null) {
                val oldAccountId = tx.accountId
                val oldAccount = accountDao.getAccountById(oldAccountId)
                if (oldAccount != null) {
                    if (oldAccount.type == AccountType.TARJETA_CREDITO) {
                        val card = creditCardDao.getByAccountId(oldAccountId)
                        if (card != null) {
                            if (tx.type == TransactionType.GASTO_TC) {
                                deferredPurchaseDao.delete(tx.id)
                                recalculateCardDebt(card.id)
                            } else if (tx.type == TransactionType.PAGO_TC) {
                                creditCardDao.adjustDebt(card.id, tx.amount)
                            }
                        }
                    } else {
                        if (tx.type == TransactionType.INGRESO || tx.type == TransactionType.PAGO_TC) {
                            accountDao.adjustBalance(oldAccountId, -tx.amount)
                        } else {
                            accountDao.adjustBalance(oldAccountId, +tx.amount)
                        }
                    }
                }
            }
            transactionDao.delete(transactionId)
        }
    }
}

