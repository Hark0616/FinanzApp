package com.ivan.finanzapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.LoanPaymentDao
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.usecase.LoanPaymentRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val loanDao: LoanDao,
    private val accountDao: AccountDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val loanPaymentRegistrar: LoanPaymentRegistrar
) : ViewModel() {

    private val _isAddDialogVisible = MutableStateFlow(false)

    private val paymentAudit = combine(
        loanPaymentDao.observeLatestByLoan(),
        loanPaymentDao.observeTotalUnpaidInterest(),
        loanPaymentDao.observeTotalUnpaidCharges()
    ) { loanPayments, totalUnpaidInterest, totalUnpaidCharges ->
        LoanPaymentAudit(
            latestPaymentsByLoanId = loanPayments.associateBy { it.loanId },
            totalUnpaidInterest = totalUnpaidInterest,
            totalUnpaidCharges = totalUnpaidCharges
        )
    }

    val uiState: StateFlow<LoansUiState> = combine(
        loanDao.observeAll(),
        accountDao.observeAccounts(),
        paymentAudit,
        _isAddDialogVisible
    ) { loans, accounts, paymentAudit, isAddDialogVisible ->
        val totalDebt = loans.sumOf { it.remainingAmount }
        val savingsAccounts = accounts.filter { it.type != AccountType.TARJETA_CREDITO }
        LoansUiState(
            isLoading = false,
            loans = loans,
            accounts = savingsAccounts,
            latestPaymentsByLoanId = paymentAudit.latestPaymentsByLoanId,
            totalDebt = totalDebt,
            totalUnpaidInterest = paymentAudit.totalUnpaidInterest,
            totalUnpaidCharges = paymentAudit.totalUnpaidCharges,
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
        monthlyInsurance: Double,
        monthlyFee: Double,
        paymentDay: Int,
        linkedAccountId: String?
    ) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val normalizedPaymentDay = paymentDay.coerceIn(1, 31)
            val normalizedInstallments = totalInstallments.coerceAtLeast(1)
            var paymentDate = LocalDate.of(
                today.year,
                today.month,
                normalizedPaymentDay.coerceAtMost(today.month.length(today.isLeapYear))
            )
            if (today.dayOfMonth >= normalizedPaymentDay) {
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
                totalInstallments = normalizedInstallments,
                paidInstallments = 0,
                monthlyInstallmentAmount = monthlyInstallment,
                monthlyInsuranceAmount = monthlyInsurance.coerceAtLeast(0.0),
                monthlyFeeAmount = monthlyFee.coerceAtLeast(0.0),
                paymentDay = normalizedPaymentDay,
                nextPaymentDate = nextPaymentDateMillis,
                linkedAccountId = linkedAccountId
            )
            loanDao.upsert(loan)
            toggleAddDialog(false)
        }
    }

    fun payInstallment(loan: LoanEntity) {
        viewModelScope.launch {
            loanPaymentRegistrar.registerInstallmentPayment(loan.id)
        }
    }

    fun deleteLoan(loanId: String) {
        viewModelScope.launch {
            loanDao.delete(loanId)
        }
    }
}

private data class LoanPaymentAudit(
    val latestPaymentsByLoanId: Map<String, LoanPaymentEntity>,
    val totalUnpaidInterest: Double,
    val totalUnpaidCharges: Double
)
