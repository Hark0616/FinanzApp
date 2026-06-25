package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FinancialAdjustmentTargetType {
    ACCOUNT_BALANCE,
    CREDIT_CARD_LIMIT,
    CREDIT_CARD_DEBT
}

/**
 * Registro auditable de una correccion manual.
 *
 * No representa una compra, pago o transferencia. Solo documenta un ajuste
 * excepcional hecho desde la UI para mantener trazabilidad financiera.
 */
@Entity(
    tableName = "financial_adjustments",
    indices = [
        Index("targetId"),
        Index("targetType"),
        Index("createdAt")
    ]
)
data class FinancialAdjustmentEntity(
    @PrimaryKey
    val id: String,
    val targetType: FinancialAdjustmentTargetType,
    val targetId: String,
    val targetName: String,
    val previousValue: Double,
    val newValue: Double,
    val delta: Double,
    val reason: String,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
