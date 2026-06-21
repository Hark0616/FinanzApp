package com.ivan.finanzapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.calculator.LoanCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loanDao: LoanDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val loanCalculator: LoanCalculator
) : ViewModel() {

    private val _isAddDialogVisible = MutableStateFlow(false)

    val uiState: StateFlow<LoansUiState> = combine(
        loanDao.observeAll(),
        accountDao.observeAccounts(),
        _isAddDialogVisible
    ) { loans, accounts, isAddDialogVisible ->
        val totalDebt = loans.sumOf { it.remainingAmount }
        val savingsAccounts = accounts.filter { it.type != AccountType.TARJETA_CREDITO }
        LoansUiState(
            isLoading = false,
            loans = loans,
            accounts = savingsAccounts,
            totalDebt = totalDebt,
            isAddDialogVisible = isAddDialogVisible
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LoansUiState()
    )

    fun toggleAddDialog(visible: Boolean) {
        _isAddDialogVisible.update { visible }
    }

    fun addLoan(
        name: String,
        totalAmount: Double,
        interestRate: Double,
        totalInstallments: Int,
        monthlyInstallment: Double,
        paymentDay: Int,
        linkedAccountId: String?
    ) {
        viewModelScope.launch {
            val today = LocalDate.now()
            var paymentDate = LocalDate.of(
                today.year,
                today.month,
                paymentDay.coerceAtMost(today.month.length(today.isLeapYear))
            )
            if (today.dayOfMonth >= paymentDay) {
                paymentDate = paymentDate.plusMonths(1)
            }
            val nextPaymentDateMillis = paymentDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            val loan = LoanEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                totalAmount = totalAmount,
                remainingAmount = totalAmount,
                monthlyInterestRate = interestRate,
                totalInstallments = totalInstallments,
                paidInstallments = 0,
                monthlyInstallmentAmount = monthlyInstallment,
                paymentDay = paymentDay,
                nextPaymentDate = nextPaymentDateMillis,
                linkedAccountId = linkedAccountId
            )
            loanDao.upsert(loan)
            toggleAddDialog(false)
        }
    }

    fun payInstallment(loan: LoanEntity) {
        viewModelScope.launch {
            val paymentProgress = loanCalculator.applyInstallmentPayment(loan)
            if (paymentProgress.paymentAmount <= 0.0) return@launch

            // Calcular siguiente fecha de pago (+1 mes)
            val currentPaymentDate = Instant.ofEpochMilli(loan.nextPaymentDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val nextPaymentDate = currentPaymentDate.plusMonths(1)
            val nextPaymentDateMillis = nextPaymentDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

            // 1. Actualizar el progreso del crédito
            loanDao.updateLoanPaymentProgress(
                loanId = loan.id,
                remainingAmount = paymentProgress.remainingAmount,
                paidInstallments = paymentProgress.paidInstallments,
                nextPaymentDate = nextPaymentDateMillis
            )

            // 2. Registrar el movimiento en el historial
            val transactionId = UUID.randomUUID().toString()
            val transaction = TransactionEntity(
                id = transactionId,
                accountId = loan.linkedAccountId,
                amount = paymentProgress.paymentAmount,
                type = TransactionType.GASTO,
                merchant = "Pago Cuota: ${loan.name}",
                categoryId = null, // Dejar sin categorizar o resuelto
                rawNotification = "Pago de cuota manual registrado en app",
                timestamp = System.currentTimeMillis(),
                confirmedByAI = false,
                needsReview = false
            )
            transactionDao.insertIfNotExists(transaction)

            // 3. Si hay una cuenta vinculada, debitar el saldo automáticamente
            loan.linkedAccountId?.let { accountId ->
                accountDao.adjustBalance(accountId, -paymentProgress.paymentAmount)
            }
        }
    }

    fun deleteLoan(loanId: String) {
        viewModelScope.launch {
            loanDao.delete(loanId)
        }
    }
}
