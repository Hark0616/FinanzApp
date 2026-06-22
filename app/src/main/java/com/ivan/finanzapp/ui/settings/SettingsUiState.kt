package com.ivan.finanzapp.ui.settings

import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CustomRuleEntity

data class SettingsUiState(
    val isLoading: Boolean = true,
    val apiKey: String = "",
    val accounts: List<AccountEntity> = emptyList(),
    val customRules: List<CustomRuleEntity> = emptyList(),
    val isAddAccountDialogVisible: Boolean = false,
    val isProcessingDialogVisible: Boolean = false,
    val isCustomRulesDialogVisible: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val isLocalAiSupported: Boolean = false,
    val isLocalAiEnabled: Boolean = false,
    val processingMode: String = SecurePrefs.MODE_PARSER,
    val currentUserEmail: String? = null,
    val isSyncing: Boolean = false
)
