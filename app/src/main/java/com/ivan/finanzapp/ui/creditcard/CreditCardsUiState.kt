package com.ivan.finanzapp.ui.creditcard

import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.ui.dashboard.CreditCardSummary

data class CreditCardsUiState(
    val isLoading: Boolean = true,
    val creditCards: List<CreditCardSummary> = emptyList(),
    val accounts: List<AccountEntity> = emptyList()
)
