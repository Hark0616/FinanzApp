package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Información específica de una tarjeta de crédito, vinculada 1:1 a una
 * cuenta de tipo TARJETA_CREDITO en [AccountEntity].
 */
@Entity(
    tableName = "credit_cards",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class CreditCardEntity(
    @PrimaryKey
    val id: String,
    val accountId: String,
    val creditLimit: Double,
    val currentDebt: Double,
    /** Día del mes en que cierra el ciclo de facturación (1-31). */
    val cutoffDay: Int,
    /** Día del mes en que vence el pago (1-31). */
    val paymentDueDay: Int,
    /** Porcentaje mínimo de pago sobre la deuda (ej. 5.0 = 5%). */
    val minPaymentPercentage: Double = 5.0,
    /** Valor mínimo legal en pesos colombianos para el pago mínimo. */
    val minPaymentFloor: Double = 0.0,
    /** Tasa efectiva anual, opcional, para proyecciones de interés. */
    val interestRateEA: Double? = null
)
