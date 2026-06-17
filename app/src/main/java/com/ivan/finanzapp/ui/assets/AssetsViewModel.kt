package com.ivan.finanzapp.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.data.local.dao.AssetDao
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.entity.AssetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AssetsUiState(
    val isLoading: Boolean = true,
    val assets: List<AssetEntity> = emptyList(),
    val liquidCash: Double = 0.0,
    val totalAssets: Double = 0.0
)

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val assetDao: AssetDao,
    private val accountDao: AccountDao
) : ViewModel() {

    val uiState: StateFlow<AssetsUiState> = combine(
        assetDao.observeAll(),
        accountDao.observeAccounts()
    ) { assets, accounts ->
        val savingsAccounts = accounts.filter { it.type != com.ivan.finanzapp.domain.model.AccountType.TARJETA_CREDITO }
        val liquidCash = savingsAccounts.sumOf { it.currentBalance }
        val customAssetsTotal = assets.sumOf { it.amount }
        val totalAssets = liquidCash + customAssetsTotal

        AssetsUiState(
            isLoading = false,
            assets = assets,
            liquidCash = liquidCash,
            totalAssets = totalAssets
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssetsUiState()
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
}
