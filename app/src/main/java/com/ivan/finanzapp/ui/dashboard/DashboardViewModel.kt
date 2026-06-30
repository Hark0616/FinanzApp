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
import com.ivan.finanzapp.data.local.dao.NotificationSyncLedgerDao
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.NotificationProcessingStatus
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val loanDao: LoanDao,
    private val notificationSyncLedgerDao: NotificationSyncLedgerDao,
    private val calculator: CreditCardCalculator
) : ViewModel() {

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

    private val _permissionCheckTrigger = MutableStateFlow(0)

    private val recentCaptureStartMillis = System.currentTimeMillis() - RECENT_CAPTURE_WINDOW_MILLIS

    private val notificationInsightsFlow = combine(
        notificationSyncLedgerDao.observeCountSince(recentCaptureStartMillis),
        notificationSyncLedgerDao.observeCountByStatus(NotificationProcessingStatus.QUEUED),
        notificationSyncLedgerDao.observeCountByStatusSince(
            NotificationProcessingStatus.FAILED,
            recentCaptureStartMillis
        ),
        notificationSyncLedgerDao.observeLatestByStatus(NotificationProcessingStatus.PARSED)
    ) { recentCount, queuedCount, failedRecentCount, latestParsed ->
        NotificationInsights(
            recentCount = recentCount,
            queuedCount = queuedCount,
            failedRecentCount = failedRecentCount,
            latestParsedAt = latestParsed?.processedAtMillis ?: latestParsed?.receivedAtMillis
        )
    }

    private val coreDashboardFlow = combine(
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
        val unclassifiedCount = transactions.count { it.accountId == null }

        // Calcular flujo de caja disponible de este mes
        val totalIncomesThisMonth = transactions
            .filter { it.type == TransactionType.INGRESO && it.timestamp in monthStart until monthEnd }
            .sumOf { it.amount }

        val totalCreditCardInstallments = cards.sumOf { card ->
            val cardPurchases = purchasesByCard[card.id] ?: emptyList()
            calculator.totalMonthlyInstallments(cardPurchases, card.interestRateEA)
        }

        val totalLoanInstallments = loans.filter { it.remainingAmount > 0 }.sumOf { it.monthlyInstallmentAmount }
        val totalCreditCardDebt = cards.sumOf { it.currentDebt }
        val totalLoanRemaining = loans.filter { it.remainingAmount > 0 }.sumOf { it.remainingAmount }
        val dominantSpending = spendingItems.firstOrNull()
        val totalDebtInstallmentsThisMonth = totalCreditCardInstallments + totalLoanInstallments
        val disposableCashFlow = totalIncomesThisMonth - totalDebtInstallmentsThisMonth
        val debtLoadRatio = when {
            totalIncomesThisMonth > 0.0 -> totalDebtInstallmentsThisMonth / totalIncomesThisMonth
            totalDebtInstallmentsThisMonth > 0.0 -> 1.0
            else -> 0.0
        }

        val nextPayment = (
            cardSummaries
                .filter { it.card.currentDebt > 0.0 || it.minimumPayment > 0.0 || it.totalMonthlyInstallments > 0.0 }
                .map {
                    PaymentCandidate(
                        label = it.account.name,
                        amount = maxOf(it.minimumPayment, it.totalMonthlyInstallments),
                        days = it.daysUntilDue,
                        target = NextPaymentTarget.CREDIT_CARD
                    )
                } + loans
                .filter { it.remainingAmount > 0.0 }
                .map {
                    PaymentCandidate(
                        label = it.name,
                        amount = it.monthlyInstallmentAmount,
                        days = daysUntil(it.nextPaymentDate),
                        target = NextPaymentTarget.LOAN
                    )
                }
        ).minByOrNull { it.days }

        DashboardUiState(
            isLoading = false,
            isNotificationPermissionGranted = false,
            totalBalance = totalBalance,
            accounts = accountSummaries,
            creditCards = cardSummaries,
            recentTransactions = recentTransactions,
            monthlySpendingByCategory = spendingItems,
            pendingReviewCount = pendingCount,
            unclassifiedTransactionCount = unclassifiedCount,
            disposableCashFlow = disposableCashFlow,
            totalIncomesThisMonth = totalIncomesThisMonth,
            totalDebtInstallmentsThisMonth = totalDebtInstallmentsThisMonth,
            totalCreditCardDebt = totalCreditCardDebt,
            totalLoanRemaining = totalLoanRemaining,
            monthlySpendingTotal = totalSpent,
            dominantSpendingCategoryName = dominantSpending?.let { it.category?.name ?: "Sin categoría" },
            dominantSpendingPercentage = dominantSpending?.percentage ?: 0.0,
            nextPaymentLabel = nextPayment?.label,
            nextPaymentAmount = nextPayment?.amount ?: 0.0,
            nextPaymentDays = nextPayment?.days,
            nextPaymentTarget = nextPayment?.target,
            debtLoadRatio = debtLoadRatio
        )
    }

    private val _dbDataFlow = combine(
        coreDashboardFlow,
        notificationInsightsFlow
    ) { coreState, insights ->
        coreState.copy(
            captureRecentCount = insights.recentCount,
            captureQueuedCount = insights.queuedCount,
            captureFailedRecentCount = insights.failedRecentCount,
            latestParsedAt = insights.latestParsedAt
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _dbDataFlow,
        _permissionCheckTrigger
    ) { dbState, _ ->
        dbState.copy(
            isNotificationPermissionGranted = isNotificationListenerEnabled()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun checkNotificationPermission() {
        _permissionCheckTrigger.update { it + 1 }
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

    private fun daysUntil(timestampMillis: Long): Int {
        val today = LocalDate.now()
        val targetDate = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return ChronoUnit.DAYS.between(today, targetDate).toInt()
    }

    private data class PaymentCandidate(
        val label: String,
        val amount: Double,
        val days: Int,
        val target: NextPaymentTarget
    )

    private data class NotificationInsights(
        val recentCount: Int,
        val queuedCount: Int,
        val failedRecentCount: Int,
        val latestParsedAt: Long?
    )

    private companion object {
        private const val RECENT_CAPTURE_WINDOW_MILLIS = 24L * 60L * 60L * 1000L
    }
}
