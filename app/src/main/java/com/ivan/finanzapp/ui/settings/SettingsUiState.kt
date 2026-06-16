package com.ivan.finanzapp.ui.settings

import com.ivan.finanzapp.data.local.entity.AccountEntity

data class SettingsUiState(
    val isLoading: Boolean = true,
    val apiKey: String = "",
    val accounts: List<AccountEntity> = emptyList(),
    val isAddAccountDialogVisible: Boolean = false,
    val isSavedSuccess: Boolean = false,
    val isLocalAiSupported: Boolean = false,
    val isLocalAiEnabled: Boolean = false
)
