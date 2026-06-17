package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AssetType {
    SUELDO,
    INVERSION,
    INMUEBLE,
    VEHICULO,
    OTRO
}

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val amount: Double,
    val type: AssetType,
    val createdAt: Long = System.currentTimeMillis()
)
