package com.ivan.finanzapp.ui.dashboard

import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity

/**
 * Estado completo de la pantalla del Dashboard.
 * El ViewModel emite un nuevo [DashboardUiState] por cada cambio
 * reactivo en la base de datos.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,
    val isNotificationPermissionGranted: Boolean = false,
    val totalBalance: Double = 0.0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val creditCards: List<CreditCardSummary> = emptyList(),
    val recentTransactions: List<TransactionWithCategory> = emptyList(),
    val monthlySpendingByCategory: List<CategorySpendingItem> = emptyList(),
    val pendingReviewCount: Int = 0,
    val unclassifiedTransactionCount: Int = 0,
    val disposableCashFlow: Double = 0.0,
    val totalIncomesThisMonth: Double = 0.0,
    val totalDebtInstallmentsThisMonth: Double = 0.0,
    val totalCreditCardDebt: Double = 0.0,
    val totalLoanRemaining: Double = 0.0,
    val monthlySpendingTotal: Double = 0.0,
    val dominantSpendingCategoryName: String? = null,
    val dominantSpendingPercentage: Double = 0.0,
    val captureRecentCount: Int = 0,
    val captureQueuedCount: Int = 0,
    val captureFailedRecentCount: Int = 0,
    val latestParsedAt: Long? = null,
    val nextPaymentLabel: String? = null,
    val nextPaymentAmount: Double = 0.0,
    val nextPaymentDays: Int? = null,
    val nextPaymentTarget: NextPaymentTarget? = null,
    val debtLoadRatio: Double = 0.0
)

enum class NextPaymentTarget {
    CREDIT_CARD,
    LOAN
}

data class AccountWithBalance(
    val account: AccountEntity,
    /** Balance formateado para mostrar, calculado en el ViewModel */
    val displayBalance: Double
)

data class CreditCardSummary(
    val card: CreditCardEntity,
    val account: AccountEntity,
    val availableCredit: Double,
    val usagePercentage: Double,
    val minimumPayment: Double,
    val daysUntilDue: Int,
    /** "LOW" | "MEDIUM" | "HIGH" */
    val usageLevel: String,
    val deferredPurchases: List<DeferredPurchaseEntity> = emptyList(),
    val totalMonthlyInstallments: Double = 0.0,
    val activeDeferredCount: Int = 0
)

data class TransactionWithCategory(
    val transaction: TransactionEntity,
    val category: CategoryEntity?,
    val accountName: String?
)

data class CategorySpendingItem(
    val category: CategoryEntity?,
    val total: Double,
    val percentage: Double
)
