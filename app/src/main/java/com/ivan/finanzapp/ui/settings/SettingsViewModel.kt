package com.ivan.finanzapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.remote.LocalAiClassifier
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val localAiClassifier: LocalAiClassifier
) : ViewModel() {

    private val _isAddAccountDialogVisible = MutableStateFlow(false)
    private val _isProcessingDialogVisible = MutableStateFlow(false)
    private val _isSavedSuccess = MutableStateFlow(false)
    private val _isLocalAiPrefChanged = MutableStateFlow(false)
    private val _processingModeChanged = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        accountDao.observeAccounts(),
        _isAddAccountDialogVisible,
        _isProcessingDialogVisible,
        _isSavedSuccess,
        _isLocalAiPrefChanged,
        _processingModeChanged
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val accounts = flows[0] as List<AccountEntity>
        val isAddDialogVisible = flows[1] as Boolean
        val isProcDialogVisible = flows[2] as Boolean
        val isSavedSuccess = flows[3] as Boolean
        val apiKey = securePrefs.getOpenRouterApiKey() ?: ""
        SettingsUiState(
            isLoading = false,
            apiKey = apiKey,
            accounts = accounts,
            isAddAccountDialogVisible = isAddDialogVisible,
            isProcessingDialogVisible = isProcDialogVisible,
            isSavedSuccess = isSavedSuccess,
            isLocalAiSupported = localAiClassifier.isLocalAiSupported(),
            isLocalAiEnabled = securePrefs.isLocalAiEnabled(),
            processingMode = securePrefs.getNotificationProcessingMode()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun toggleProcessingDialog(visible: Boolean) {
        _isProcessingDialogVisible.update { visible }
    }

    fun setNotificationProcessingMode(mode: String) {
        securePrefs.setNotificationProcessingMode(mode)
        _processingModeChanged.update { !it }
    }

    fun setLocalAiEnabled(enabled: Boolean) {
        securePrefs.setLocalAiEnabled(enabled)
        _isLocalAiPrefChanged.update { !it }
    }

    fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                securePrefs.clearOpenRouterApiKey()
            } else {
                securePrefs.setOpenRouterApiKey(apiKey.trim())
            }
            _isSavedSuccess.update { true }
            // Reset state
            kotlinx.coroutines.delay(2000)
            _isSavedSuccess.update { false }
        }
    }

    fun toggleAddAccountDialog(visible: Boolean) {
        _isAddAccountDialogVisible.update { visible }
    }

    fun addAccount(
        name: String,
        type: AccountType,
        initialBalance: Double,
        // Campos adicionales para Tarjeta de Crédito
        creditLimit: Double = 0.0,
        cutoffDay: Int = 15,
        paymentDueDay: Int = 30,
        interestRateEA: Double? = null
    ) {
        viewModelScope.launch {
            val accountId = UUID.randomUUID().toString()
            val account = AccountEntity(
                id = accountId,
                name = name,
                type = type,
                currentBalance = if (type == AccountType.TARJETA_CREDITO) 0.0 else initialBalance,
                isManualBalance = true
            )
            accountDao.upsert(account)

            // Si es tarjeta de crédito, insertar también en su tabla específica.
            // La deuda siempre inicia en $0 — se construye desde las Compras Diferidas.
            if (type == AccountType.TARJETA_CREDITO) {
                val card = CreditCardEntity(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    creditLimit = creditLimit,
                    currentDebt = 0.0,
                    cutoffDay = cutoffDay,
                    paymentDueDay = paymentDueDay,
                    minPaymentPercentage = 5.0,
                    minPaymentFloor = 0.0,
                    interestRateEA = interestRateEA
                )
                creditCardDao.upsert(card)
            }
            toggleAddAccountDialog(false)
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            accountDao.delete(accountId)
        }
    }
}
