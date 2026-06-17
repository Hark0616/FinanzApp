package com.ivan.finanzapp.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.*
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.entity.AssetType
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.dashboard.TransactionWithCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class BalanceUiState(
    val isLoading: Boolean = true,
    // Patrimonio (Activos)
    val assets: List<AssetEntity> = emptyList(),
    val liquidCash: Double = 0.0,
    val totalAssets: Double = 0.0,
    
    // Flujo de Caja (Ingresos y Egresos Comprometidos)
    val monthlyIncomes: List<TransactionWithCategory> = emptyList(),
    val totalIncomesThisMonth: Double = 0.0,
    val totalCreditCardInstallments: Double = 0.0,
    val totalLoanInstallments: Double = 0.0,
    val disposableCashFlow: Double = 0.0,
    
    // Auxiliares para formularios
    val accounts: List<com.ivan.finanzapp.data.local.entity.AccountEntity> = emptyList(),
    val categories: List<com.ivan.finanzapp.data.local.entity.CategoryEntity> = emptyList()
)

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val loanDao: LoanDao,
    private val creditCardDao: CreditCardDao,
    private val deferredPurchaseDao: DeferredPurchaseDao,
    private val calculator: CreditCardCalculator
) : ViewModel() {

    val uiState: StateFlow<BalanceUiState> = combine(
        assetDao.observeAll(),
        accountDao.observeAccounts(),
        transactionDao.observeAll(),
        categoryDao.observeAll(),
        combine(
            loanDao.observeAll(),
            creditCardDao.observeAll(),
            deferredPurchaseDao.observeAll()
        ) { loans, cards, purchases ->
            Triple(loans, cards, purchases)
        }
    ) { assets, accounts, transactions, categories, debtData ->
        val (loans, cards, allDeferredPurchases) = debtData
        
        // 1. Patrimonio (Activos)
        val savingsAccounts = accounts.filter { it.type != com.ivan.finanzapp.domain.model.AccountType.TARJETA_CREDITO }
        val liquidCash = savingsAccounts.sumOf { it.currentBalance }
        val customAssetsTotal = assets.sumOf { it.amount }
        val totalAssets = liquidCash + customAssetsTotal

        // 2. Ingresos de este mes (Filtrados del historial de movimientos)
        val localDate = LocalDate.now()
        val startOfMonth = localDate.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = localDate.withDayOfMonth(localDate.lengthOfMonth()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }

        val monthlyIncomes = transactions
            .filter { it.type == TransactionType.INGRESO && it.timestamp in startOfMonth until endOfMonth }
            .map { tx ->
                TransactionWithCategory(
                    transaction = tx,
                    category = tx.categoryId?.let { categoryMap[it] },
                    accountName = tx.accountId?.let { accountMap[it]?.name }
                )
            }
        val totalIncomesThisMonth = monthlyIncomes.sumOf { it.transaction.amount }

        // 3. Egresos comprometidos de este mes
        // A. Tarjetas de crédito
        val purchasesByCard = allDeferredPurchases.groupBy { it.creditCardId }
        val totalCreditCardInstallments = cards.sumOf { card ->
            val cardPurchases = purchasesByCard[card.id] ?: emptyList()
            calculator.totalMonthlyInstallments(cardPurchases, card.interestRateEA)
        }

        // B. Créditos
        val totalLoanInstallments = loans.filter { it.remainingAmount > 0 }.sumOf { it.monthlyInstallmentAmount }

        val disposableCashFlow = totalIncomesThisMonth - (totalCreditCardInstallments + totalLoanInstallments)

        BalanceUiState(
            isLoading = false,
            assets = assets,
            liquidCash = liquidCash,
            totalAssets = totalAssets,
            monthlyIncomes = monthlyIncomes,
            totalIncomesThisMonth = totalIncomesThisMonth,
            totalCreditCardInstallments = totalCreditCardInstallments,
            totalLoanInstallments = totalLoanInstallments,
            disposableCashFlow = disposableCashFlow,
            accounts = accounts,
            categories = categories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BalanceUiState()
    )

    fun addAsset(name: String, amount: Double, type: AssetType) {
        viewModelScope.launch {
            val asset = AssetEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                amount = amount,
                type = type
            )
            assetDao.upsert(asset)
        }
    }

    fun updateAsset(id: String, name: String, amount: Double, type: AssetType) {
        viewModelScope.launch {
            val asset = AssetEntity(
                id = id,
                name = name,
                amount = amount,
                type = type
            )
            assetDao.upsert(asset)
        }
    }

    fun deleteAsset(id: String) {
        viewModelScope.launch {
            assetDao.delete(id)
        }
    }

    fun addIncomeTransaction(
        merchant: String,
        amount: Double,
        accountId: String,
        categoryId: String?
    ) {
        viewModelScope.launch {
            val txId = UUID.randomUUID().toString()
            val transaction = TransactionEntity(
                id = txId,
                accountId = accountId,
                amount = amount,
                type = TransactionType.INGRESO,
                merchant = merchant,
                categoryId = categoryId,
                rawNotification = "Registro manual de ingreso en app",
                timestamp = System.currentTimeMillis(),
                confirmedByAI = false,
                needsReview = false
            )
            transactionDao.insertIfNotExists(transaction)
            accountDao.adjustBalance(accountId, +amount)
        }
    }
}
