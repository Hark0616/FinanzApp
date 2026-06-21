package com.ivan.finanzapp.ui.loan

import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity

/**
 * Estado de la UI para la pantalla de Gestión de Créditos/Préstamos.
 */
data class LoansUiState(
    val isLoading: Boolean = true,
    val loans: List<LoanEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val latestPaymentsByLoanId: Map<String, LoanPaymentEntity> = emptyMap(),
    val totalDebt: Double = 0.0,
    val totalUnpaidInterest: Double = 0.0,
    val totalUnpaidCharges: Double = 0.0,
    val isAddDialogVisible: Boolean = false
)
