package com.ivan.finanzapp.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.domain.usecase.AddManualTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val addManualTransactionUseCase: AddManualTransactionUseCase
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = accountDao.observeAccounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveTransaction(
        amount: Double,
        merchant: String,
        typeStr: String,
        accountId: String?,
        categoryId: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            addManualTransactionUseCase(
                amount = amount,
                merchant = merchant,
                typeStr = typeStr,
                accountId = accountId,
                categoryId = categoryId
            )
            onSuccess()
        }
    }
}
