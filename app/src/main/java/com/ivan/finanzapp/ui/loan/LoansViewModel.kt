package com.ivan.finanzapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.dao.LoanPaymentDao
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import com.ivan.finanzapp.domain.calculator.LoanCalculator
import com.ivan.finanzapp.domain.model.LoanAmortizationType
import com.ivan.finanzapp.domain.model.LoanInterestRateType
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
    private val loanPaymentRegistrar: LoanPaymentRegistrar,
    private val loanCalculator: LoanCalculator,
    private val cloudSyncScheduler: CloudSyncScheduler
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
        interestRateType: LoanInterestRateType,
        amortizationType: LoanAmortizationType,
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
            val normalizedMonthlyRate = loanCalculator.normalizeMonthlyInterestRate(
                interestRateInputValue = interestRate,
                interestRateType = interestRateType
            )
            val normalizedInsurance = monthlyInsurance.coerceAtLeast(0.0)
            val normalizedFee = monthlyFee.coerceAtLeast(0.0)
            val fixedPrincipalAmount = when (amortizationType) {
                LoanAmortizationType.FIXED_INSTALLMENT -> 0.0
                LoanAmortizationType.FIXED_PRINCIPAL ->
                    loanCalculator.fixedPrincipalAmount(totalAmount, normalizedInstallments)
            }
            val firstScheduledPaymentAmount = when (amortizationType) {
                LoanAmortizationType.FIXED_INSTALLMENT -> monthlyInstallment.coerceAtLeast(0.0)
                LoanAmortizationType.FIXED_PRINCIPAL ->
                    normalizedInsurance +
                            normalizedFee +
                            loanCalculator.monthlyInterestAmount(totalAmount, normalizedMonthlyRate) +
                            fixedPrincipalAmount
            }

            val loan = LoanEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                totalAmount = totalAmount,
                remainingAmount = totalAmount,
                monthlyInterestRate = normalizedMonthlyRate,
                interestRateInputValue = interestRate,
                interestRateType = interestRateType,
                amortizationType = amortizationType,
                totalInstallments = normalizedInstallments,
                paidInstallments = 0,
                monthlyInstallmentAmount = firstScheduledPaymentAmount,
                fixedPrincipalAmount = fixedPrincipalAmount,
                monthlyInsuranceAmount = normalizedInsurance,
                monthlyFeeAmount = normalizedFee,
                paymentDay = normalizedPaymentDay,
                nextPaymentDate = nextPaymentDateMillis,
                linkedAccountId = linkedAccountId
            )
            loanDao.upsert(loan)
            toggleAddDialog(false)
            cloudSyncScheduler.syncSoon()
        }
    }

    fun payInstallment(
        loan: LoanEntity,
        actualPaymentAmount: Double? = null
    ) {
        viewModelScope.launch {
            loanPaymentRegistrar.registerInstallmentPayment(
                loanId = loan.id,
                actualPaymentAmount = actualPaymentAmount
            )
            cloudSyncScheduler.syncSoon()
        }
    }

    fun deleteLoan(loanId: String) {
        viewModelScope.launch {
            loanDao.delete(loanId)
            cloudSyncScheduler.syncSoon()
        }
    }
}

private data class LoanPaymentAudit(
    val latestPaymentsByLoanId: Map<String, LoanPaymentEntity>,
    val totalUnpaidInterest: Double,
    val totalUnpaidCharges: Double
)
