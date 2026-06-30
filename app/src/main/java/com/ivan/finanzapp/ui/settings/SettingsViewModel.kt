package com.ivan.finanzapp.ui.settings

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ivan.finanzapp.BuildConfig
import com.ivan.finanzapp.data.local.AppDatabase
import com.ivan.finanzapp.data.local.AppearancePrefs
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.remote.LocalAiClassifier
import com.ivan.finanzapp.data.remote.CloudSyncScheduler
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.CreditCardDao
import com.ivan.finanzapp.data.local.dao.CustomRuleDao
import com.ivan.finanzapp.data.local.dao.FinancialAdjustmentDao
import com.ivan.finanzapp.data.local.dao.NotificationSyncLedgerDao
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.CustomRuleEntity
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentEntity
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentTargetType
import com.ivan.finanzapp.data.local.entity.NotificationProcessingStatus
import com.ivan.finanzapp.data.local.entity.NotificationSyncLedgerEntity
import com.ivan.finanzapp.domain.model.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @param:ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val appearancePrefs: AppearancePrefs,
    private val securePrefs: SecurePrefs,
    private val accountDao: AccountDao,
    private val creditCardDao: CreditCardDao,
    private val customRuleDao: CustomRuleDao,
    private val notificationSyncLedgerDao: NotificationSyncLedgerDao,
    private val financialAdjustmentDao: FinancialAdjustmentDao,
    private val localAiClassifier: LocalAiClassifier,
    private val authManager: com.ivan.finanzapp.data.remote.SupabaseAuthManager,
    private val syncManager: com.ivan.finanzapp.data.remote.SupabaseSyncManager,
    private val cloudSyncScheduler: CloudSyncScheduler
) : ViewModel() {

    private val _isAddAccountDialogVisible = MutableStateFlow(false)
    private val _isProcessingDialogVisible = MutableStateFlow(false)
    private val _isCustomRulesDialogVisible = MutableStateFlow(false)
    private val _isSavedSuccess = MutableStateFlow(false)
    private val _isLocalAiPrefChanged = MutableStateFlow(false)
    private val _processingModeChanged = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    private val _syncStateVersion = MutableStateFlow(0)
    private val _appLockChanged = MutableStateFlow(false)
    private val _notificationPermissionCheckTrigger = MutableStateFlow(0)
    private val recentLedgerWindowStart = System.currentTimeMillis() - RECENT_CAPTURE_WINDOW_MILLIS

    val uiState: StateFlow<SettingsUiState> = combine(
        accountDao.observeAccounts(),
        customRuleDao.observeAll(),
        authManager.currentUserFlow,
        _isAddAccountDialogVisible,
        _isProcessingDialogVisible,
        _isCustomRulesDialogVisible,
        _isSavedSuccess,
        _isLocalAiPrefChanged,
        _processingModeChanged,
        _isSyncing,
        _syncStatusMessage,
        _syncErrorMessage,
        _syncStateVersion,
        _appLockChanged,
        creditCardDao.observeAll(),
        notificationSyncLedgerDao.observeRecent(limit = 1),
        notificationSyncLedgerDao.observeCountByStatus(NotificationProcessingStatus.RECEIVED),
        notificationSyncLedgerDao.observeCountByStatus(NotificationProcessingStatus.QUEUED),
        notificationSyncLedgerDao.observeCountByStatus(NotificationProcessingStatus.PARSED),
        notificationSyncLedgerDao.observeCountByStatus(NotificationProcessingStatus.DUPLICATE),
        notificationSyncLedgerDao.observeCountByStatus(NotificationProcessingStatus.FAILED),
        notificationSyncLedgerDao.observeCountByStatus(NotificationProcessingStatus.IGNORED),
        notificationSyncLedgerDao.observeCountSince(recentLedgerWindowStart),
        notificationSyncLedgerDao.observeCountByStatusSince(NotificationProcessingStatus.FAILED, recentLedgerWindowStart),
        notificationSyncLedgerDao.observeLatestByStatus(NotificationProcessingStatus.PARSED),
        financialAdjustmentDao.observeRecent(limit = 8),
        _notificationPermissionCheckTrigger,
        appearancePrefs.useDynamicColor
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val accounts = flows[0] as List<AccountEntity>
        @Suppress("UNCHECKED_CAST")
        val customRules = flows[1] as List<CustomRuleEntity>
        val currentUser = flows[2] as io.github.jan.supabase.auth.user.UserInfo?
        val isAddDialogVisible = flows[3] as Boolean
        val isProcDialogVisible = flows[4] as Boolean
        val isCustomRulesVisible = flows[5] as Boolean
        val isSavedSuccess = flows[6] as Boolean
        val isSyncing = flows[9] as Boolean
        val syncStatusMessage = flows[10] as String?
        val syncErrorMessage = flows[11] as String?
        val apiKey = securePrefs.getOpenRouterApiKey() ?: ""
        @Suppress("UNCHECKED_CAST")
        val creditCards = flows[14] as List<CreditCardEntity>
        val creditCardsMap = creditCards.associateBy { it.accountId }
        @Suppress("UNCHECKED_CAST")
        val latestLedger = (flows[15] as List<NotificationSyncLedgerEntity>).firstOrNull()
        val receivedCount = flows[16] as Int
        val queuedCount = flows[17] as Int
        val parsedCount = flows[18] as Int
        val duplicateCount = flows[19] as Int
        val failedCount = flows[20] as Int
        val ignoredCount = flows[21] as Int
        val recentCount = flows[22] as Int
        val recentFailedCount = flows[23] as Int
        val latestParsedLedger = flows[24] as NotificationSyncLedgerEntity?
        @Suppress("UNCHECKED_CAST")
        val recentAdjustments = flows[25] as List<FinancialAdjustmentEntity>
        val useDynamicColor = flows[27] as Boolean

        SettingsUiState(
            isLoading = false,
            apiKey = apiKey,
            accounts = accounts,
            creditCardsMap = creditCardsMap,
            customRules = customRules,
            isAddAccountDialogVisible = isAddDialogVisible,
            isProcessingDialogVisible = isProcDialogVisible,
            isCustomRulesDialogVisible = isCustomRulesVisible,
            isSavedSuccess = isSavedSuccess,
            isLocalAiSupported = localAiClassifier.isLocalAiSupported(),
            isLocalAiEnabled = securePrefs.isLocalAiEnabled(),
            processingMode = securePrefs.getNotificationProcessingMode(),
            currentUserEmail = currentUser?.email,
            isSyncing = isSyncing,
            lastCloudSyncAt = securePrefs.getLastCloudSyncAt(),
            syncStatusMessage = syncStatusMessage,
            syncErrorMessage = syncErrorMessage,
            isAppLockEnabled = securePrefs.isAppLockEnabled(),
            isSecurityLabMode = BuildConfig.SECURITY_LAB_MODE,
            useDynamicColor = useDynamicColor,
            latestLedgerEntry = latestLedger,
            ledgerReceivedCount = receivedCount,
            ledgerQueuedCount = queuedCount,
            ledgerParsedCount = parsedCount,
            ledgerDuplicateCount = duplicateCount,
            ledgerFailedCount = failedCount,
            ledgerIgnoredCount = ignoredCount,
            ledgerRecentCount = recentCount,
            ledgerRecentFailedCount = recentFailedCount,
            latestParsedLedgerEntry = latestParsedLedger,
            isNotificationListenerEnabled = isNotificationListenerEnabled(),
            recentFinancialAdjustments = recentAdjustments
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun toggleProcessingDialog(visible: Boolean) {
        _isProcessingDialogVisible.update { visible }
    }

    fun toggleCustomRulesDialog(visible: Boolean) {
        _isCustomRulesDialogVisible.update { visible }
    }

    fun saveCustomRule(rule: CustomRuleEntity) {
        viewModelScope.launch {
            customRuleDao.upsert(rule)
            cloudSyncScheduler.syncSoon()
        }
    }

    fun deleteCustomRule(id: String) {
        viewModelScope.launch {
            customRuleDao.delete(id)
            cloudSyncScheduler.syncSoon()
        }
    }

    fun setNotificationProcessingMode(mode: String) {
        securePrefs.setNotificationProcessingMode(mode)
        securePrefs.setLocalAiEnabled(
            mode == SecurePrefs.MODE_PARSER || mode == SecurePrefs.MODE_LOCAL_AI
        )
        _processingModeChanged.update { !it }
    }

    fun setLocalAiEnabled(enabled: Boolean) {
        securePrefs.setLocalAiEnabled(enabled)
        _isLocalAiPrefChanged.update { !it }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        securePrefs.setAppLockEnabled(enabled)
        _appLockChanged.update { !it }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            appearancePrefs.setUseDynamicColor(enabled)
        }
    }

    fun refreshNotificationPermission() {
        _notificationPermissionCheckTrigger.update { it + 1 }
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
        interestRateEA: Double? = null,
        lastFourDigits: String? = null
    ) {
        viewModelScope.launch {
            val accountId = UUID.randomUUID().toString()
            val account = AccountEntity(
                id = accountId,
                name = name,
                type = type,
                currentBalance = if (type == AccountType.TARJETA_CREDITO) 0.0 else initialBalance,
                isManualBalance = true,
                lastFourDigits = lastFourDigits
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
            cloudSyncScheduler.syncSoon()
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            accountDao.delete(accountId)
            cloudSyncScheduler.syncSoon()
        }
    }

    fun recordAccountBalanceCorrection(accountId: String, newBalance: Double, reason: String) {
        viewModelScope.launch {
            val cleanReason = reason.trim()
            if (cleanReason.isBlank()) return@launch
            var shouldSync = false
            database.withTransaction {
                val account = accountDao.getAccountById(accountId) ?: return@withTransaction
                if (account.currentBalance == newBalance) return@withTransaction
                financialAdjustmentDao.upsert(
                    financialAdjustment(
                        targetType = FinancialAdjustmentTargetType.ACCOUNT_BALANCE,
                        targetId = account.id,
                        targetName = account.name,
                        previousValue = account.currentBalance,
                        newValue = newBalance,
                        reason = cleanReason
                    )
                )
                accountDao.upsert(account.copy(currentBalance = newBalance, isManualBalance = true))
                shouldSync = true
            }
            if (shouldSync) cloudSyncScheduler.syncSoon()
        }
    }

    fun recordCreditCardCorrection(accountId: String, limit: Double, debt: Double, reason: String) {
        viewModelScope.launch {
            val cleanReason = reason.trim()
            if (cleanReason.isBlank()) return@launch
            var shouldSync = false
            database.withTransaction {
                val account = accountDao.getAccountById(accountId) ?: return@withTransaction
                val card = creditCardDao.getByAccountId(accountId) ?: return@withTransaction
                var updatedCard = card

                if (card.creditLimit != limit) {
                    financialAdjustmentDao.upsert(
                        financialAdjustment(
                            targetType = FinancialAdjustmentTargetType.CREDIT_CARD_LIMIT,
                            targetId = card.id,
                            targetName = account.name,
                            previousValue = card.creditLimit,
                            newValue = limit,
                            reason = cleanReason
                        )
                    )
                    updatedCard = updatedCard.copy(creditLimit = limit)
                }

                if (card.currentDebt != debt) {
                    financialAdjustmentDao.upsert(
                        financialAdjustment(
                            targetType = FinancialAdjustmentTargetType.CREDIT_CARD_DEBT,
                            targetId = card.id,
                            targetName = account.name,
                            previousValue = card.currentDebt,
                            newValue = debt,
                            reason = cleanReason,
                            note = "No registra pago ni compra; solo corrige la deuda visible."
                        )
                    )
                    updatedCard = updatedCard.copy(currentDebt = debt)
                }

                if (updatedCard != card) {
                    creditCardDao.upsert(updatedCard)
                    shouldSync = true
                }
            }
            if (shouldSync) cloudSyncScheduler.syncSoon()
        }
    }

    private fun financialAdjustment(
        targetType: FinancialAdjustmentTargetType,
        targetId: String,
        targetName: String,
        previousValue: Double,
        newValue: Double,
        reason: String,
        note: String? = null
    ): FinancialAdjustmentEntity {
        return FinancialAdjustmentEntity(
            id = UUID.randomUUID().toString(),
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            previousValue = previousValue,
            newValue = newValue,
            delta = newValue - previousValue,
            reason = reason,
            note = note
        )
    }

    fun syncNow() {
        viewModelScope.launch {
            if (_isSyncing.value) return@launch
            _isSyncing.value = true
            _syncStatusMessage.value = null
            _syncErrorMessage.value = null
            syncManager.sync()
                .onSuccess { summary ->
                    _syncStatusMessage.value = if (summary.totalRowsTouched > 0) {
                        if (summary.remoteDeletes > 0) {
                            "Copia actualizada. Se aplicaron ${summary.remoteDeletes} borrados reales y tus datos quedaron alineados."
                        } else {
                            "Copia actualizada. Tus datos locales y la nube quedaron alineados."
                        }
                    } else {
                        "Tu copia ya estaba al día."
                    }
                    _syncStateVersion.update { it + 1 }
                }
                .onFailure { error ->
                    _syncErrorMessage.value = error.message ?: "No se pudo sincronizar. Revisa tu conexión."
                }
            _isSyncing.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            _syncStatusMessage.value = null
            _syncErrorMessage.value = null
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName)
    }

    private companion object {
        const val RECENT_CAPTURE_WINDOW_MILLIS = 24L * 60L * 60L * 1000L
    }
}
