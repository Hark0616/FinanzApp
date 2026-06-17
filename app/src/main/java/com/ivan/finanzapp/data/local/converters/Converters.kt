package com.ivan.finanzapp.data.local.converters

import androidx.room.TypeConverter
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.data.local.entity.AssetType

/**
 * Convierte enums a String y viceversa para que Room pueda persistirlos.
 */
class Converters {

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType =
        AccountType.entries.firstOrNull { it.name == value } ?: AccountType.OTRO

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        TransactionType.entries.firstOrNull { it.name == value } ?: TransactionType.GASTO

    @TypeConverter
    fun fromAssetType(value: AssetType): String = value.name

    @TypeConverter
    fun toAssetType(value: String): AssetType =
        AssetType.entries.firstOrNull { it.name == value } ?: AssetType.OTRO
}
