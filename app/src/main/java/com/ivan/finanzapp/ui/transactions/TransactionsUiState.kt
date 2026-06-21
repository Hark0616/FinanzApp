package com.ivan.finanzapp.ui.transactions

import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.ui.dashboard.TransactionWithCategory

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val transactions: List<TransactionWithCategory> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList()
)

