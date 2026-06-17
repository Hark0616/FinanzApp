package com.ivan.finanzapp.ui.dashboard

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CategoryDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.DeferredPurchaseDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.dao.LoanDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val loanDao: LoanDao,
    private val calculator: CreditCardCalculator
) : ViewModel() {

    private val _isAccountsExpanded = MutableStateFlow(true)

    /** Rango del mes actual en milisegundos, para filtrar gastos. */
    private val monthStart: Long
        get() {
            val today = LocalDate.now()
            return LocalDate.of(today.year, today.month, 1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

    private val monthEnd: Long
        get() = LocalDate.now().plusMonths(1)
            .withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val uiState: StateFlow<DashboardUiState> = combine(
        accountDao.observeAccounts(),
        combine(creditCardDao.observeAll(), deferredPurchaseDao.observeAll()) { cards, purchases ->
            cards to purchases
        },
        categoryDao.observeAll(),
        transactionDao.observeAll(),
        combine(loanDao.observeAll(), transactionDao.observeSpendingByCategory(monthStart, monthEnd)) { loans, spending ->
            loans to spending
        }
    ) { accounts, cardsAndPurchases, categories, transactions, loansAndSpending ->

        val (cards, allDeferredPurchases) = cardsAndPurchases
        val (loans, spending) = loansAndSpending
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        val purchasesByCard = allDeferredPurchases.groupBy { it.creditCardId }

        // Balance total: suma de cuentas de ahorros/billeteras (no TC)
        val totalBalance = accounts
            .filter { it.type != AccountType.TARJETA_CREDITO }
            .sumOf { it.currentBalance }

        // Resumen de cuentas
        val accountSummaries = accounts
            .filter { it.type != AccountType.TARJETA_CREDITO }
            .map { AccountWithBalance(it, it.currentBalance) }

        // Resumen de tarjetas de crédito
        val cardSummaries = cards.mapNotNull { card ->
            val account = accountMap[card.accountId] ?: return@mapNotNull null
            val cardPurchases = purchasesByCard[card.id] ?: emptyList()
            CreditCardSummary(
                card = card,
                account = account,
                availableCredit = calculator.availableCredit(card),
                usagePercentage = calculator.usagePercentage(card),
                minimumPayment = calculator.minimumPayment(card, cardPurchases),
                daysUntilDue = calculator.daysUntilPaymentDue(card),
                usageLevel = calculator.usageTrafficLight(card).name,
                deferredPurchases = cardPurchases,
                totalMonthlyInstallments = calculator.totalMonthlyInstallments(cardPurchases, card.interestRateEA),
                activeDeferredCount = cardPurchases.count { calculator.remainingInstallments(it) > 0 }
            )
        }

        // Últimas 5 transacciones
        val recentTransactions = transactions.take(5).map { tx ->
            TransactionWithCategory(
                transaction = tx,
                category = tx.categoryId?.let { categoryMap[it] },
                accountName = tx.accountId?.let { accountMap[it]?.name }
            )
        }

        // Gastos por categoría del mes (para el gráfico)
        val totalSpent = spending.sumOf { it.total }
        val spendingItems = spending.map { item ->
            CategorySpendingItem(
                category = item.categoryId?.let { categoryMap[it] },
                total = item.total,
                percentage = if (totalSpent > 0) (item.total / totalSpent * 100) else 0.0
            )
        }

        // Transacciones pendientes de revisión
        val pendingCount = transactions.count { it.needsReview }

        // Calcular flujo de caja disponible de este mes
        val totalIncomesThisMonth = transactions
            .filter { it.type == TransactionType.INGRESO && it.timestamp in monthStart until monthEnd }
            .sumOf { it.amount }

        val totalCreditCardInstallments = cards.sumOf { card ->
            val cardPurchases = purchasesByCard[card.id] ?: emptyList()
            calculator.totalMonthlyInstallments(cardPurchases, card.interestRateEA)
        }

        val totalLoanInstallments = loans.filter { it.remainingAmount > 0 }.sumOf { it.monthlyInstallmentAmount }
        val totalDebtInstallmentsThisMonth = totalCreditCardInstallments + totalLoanInstallments
        val disposableCashFlow = totalIncomesThisMonth - totalDebtInstallmentsThisMonth

        DashboardUiState(
            isLoading = false,
            isNotificationPermissionGranted = isNotificationListenerEnabled(),
            totalBalance = totalBalance,
            accounts = accountSummaries,
            creditCards = cardSummaries,
            recentTransactions = recentTransactions,
            monthlySpendingByCategory = spendingItems,
            pendingReviewCount = pendingCount,
            isAccountsExpanded = _isAccountsExpanded.value,
            disposableCashFlow = disposableCashFlow,
            totalIncomesThisMonth = totalIncomesThisMonth,
            totalDebtInstallmentsThisMonth = totalDebtInstallmentsThisMonth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun toggleAccountsExpanded() {
        _isAccountsExpanded.update { !it }
    }

    /**
     * Verifica si FinanzApp tiene el permiso de acceso a notificaciones
     * habilitado en Configuración → Apps especiales.
     */
    fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }
}
