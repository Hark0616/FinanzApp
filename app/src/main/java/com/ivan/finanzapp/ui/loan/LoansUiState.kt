package com.ivan.finanzapp.ui.loan

import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.LoanEntity

/**
 * Estado de la UI para la pantalla de Gestión de Créditos/Préstamos.
 */
data class LoansUiState(
    val isLoading: Boolean = true,
    val loans: List<LoanEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val totalDebt: Double = 0.0,
    val isAddDialogVisible: Boolean = false
)
