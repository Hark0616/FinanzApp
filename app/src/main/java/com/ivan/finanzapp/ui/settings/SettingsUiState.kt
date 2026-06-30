package com.ivan.finanzapp.ui.settings

import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CustomRuleEntity
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentEntity
import com.ivan.finanzapp.data.local.entity.NotificationSyncLedgerEntity

data class SettingsUiState(
    val isLoading: Boolean = true,
    val apiKey: String = "",
    val accounts: List<AccountEntity> = emptyList(),
    val creditCardsMap: Map<String, com.ivan.finanzapp.data.local.entity.CreditCardEntity> = emptyMap(),
    val customRules: List<CustomRuleEntity> = emptyList(),
    val isAddAccountDialogVisible: Boolean = false,
    val isProcessingDialogVisible: Boolean = false,
    val isCustomRulesDialogVisible: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val isLocalAiSupported: Boolean = false,
    val isLocalAiEnabled: Boolean = false,
    val processingMode: String = SecurePrefs.MODE_PARSER,
    val currentUserEmail: String? = null,
    val isSyncing: Boolean = false,
    val lastCloudSyncAt: Long = 0L,
    val syncStatusMessage: String? = null,
    val syncErrorMessage: String? = null,
    val isAppLockEnabled: Boolean = false,
    val isSecurityLabMode: Boolean = false,
    val useDynamicColor: Boolean = false,
    val latestLedgerEntry: NotificationSyncLedgerEntity? = null,
    val ledgerReceivedCount: Int = 0,
    val ledgerQueuedCount: Int = 0,
    val ledgerParsedCount: Int = 0,
    val ledgerDuplicateCount: Int = 0,
    val ledgerFailedCount: Int = 0,
    val ledgerIgnoredCount: Int = 0,
    val ledgerRecentCount: Int = 0,
    val ledgerRecentFailedCount: Int = 0,
    val latestParsedLedgerEntry: NotificationSyncLedgerEntity? = null,
    val isNotificationListenerEnabled: Boolean = false,
    val recentFinancialAdjustments: List<FinancialAdjustmentEntity> = emptyList()
)

val SettingsUiState.ledgerTotalCount: Int
    get() = ledgerReceivedCount + ledgerQueuedCount + ledgerParsedCount + ledgerDuplicateCount +
            ledgerFailedCount + ledgerIgnoredCount
