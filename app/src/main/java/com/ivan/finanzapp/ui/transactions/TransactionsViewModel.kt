package com.ivan.finanzapp.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
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
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao
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
            categories = categories
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

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            transactionDao.delete(transactionId)
        }
    }
}
