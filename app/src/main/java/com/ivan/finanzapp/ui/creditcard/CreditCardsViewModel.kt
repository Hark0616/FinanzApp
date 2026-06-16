package com.ivan.finanzapp.ui.creditcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.dashboard.CreditCardSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreditCardsViewModel @Inject constructor(
    private val creditCardDao: CreditCardDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val calculator: CreditCardCalculator
) : ViewModel() {

    val uiState: StateFlow<CreditCardsUiState> = combine(
        creditCardDao.observeAll(),
        accountDao.observeAccounts()
    ) { cards, accounts ->
        val accountMap = accounts.associateBy { it.id }

        val cardSummaries = cards.mapNotNull { card ->
            val account = accountMap[card.accountId] ?: return@mapNotNull null
            CreditCardSummary(
                card = card,
                account = account,
                availableCredit = calculator.availableCredit(card),
                usagePercentage = calculator.usagePercentage(card),
                minimumPayment = calculator.minimumPayment(card),
                daysUntilDue = calculator.daysUntilPaymentDue(card),
                usageLevel = calculator.usageTrafficLight(card).name
            )
        }

        val savingsAccounts = accounts.filter { it.type != AccountType.TARJETA_CREDITO }

        CreditCardsUiState(
            isLoading = false,
            creditCards = cardSummaries,
            accounts = savingsAccounts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreditCardsUiState()
    )

    fun payCreditCard(card: CreditCardEntity, paymentAmount: Double, fundingAccountId: String?) {
        viewModelScope.launch {
            // 1. Registrar el abono en la tarjeta de crédito
            val paymentTxId = UUID.randomUUID().toString()
            val cardTx = TransactionEntity(
                id = paymentTxId,
                accountId = card.accountId,
                amount = paymentAmount,
                type = TransactionType.PAGO_TC,
                merchant = "Abono Tarjeta",
                categoryId = null,
                rawNotification = "Abono manual registrado en app",
                timestamp = System.currentTimeMillis(),
                confirmedByAI = false,
                needsReview = false
            )
            transactionDao.insertIfNotExists(cardTx)
            creditCardDao.adjustDebt(card.id, -paymentAmount)

            // 2. Si se usó una cuenta de fondos (ahorros), debitar saldo y registrar transacción
            fundingAccountId?.let { accountId ->
                val fundingTxId = UUID.randomUUID().toString()
                val fundingTx = TransactionEntity(
                    id = fundingTxId,
                    accountId = accountId,
                    amount = paymentAmount,
                    type = TransactionType.GASTO,
                    merchant = "Pago Tarjeta de Crédito",
                    categoryId = null,
                    rawNotification = "Pago de tarjeta de crédito registrado en app",
                    timestamp = System.currentTimeMillis(),
                    confirmedByAI = false,
                    needsReview = false
                )
                transactionDao.insertIfNotExists(fundingTx)
                accountDao.adjustBalance(accountId, -paymentAmount)
            }
        }
    }
}
