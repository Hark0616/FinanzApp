package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa una compra diferida activa en una tarjeta de crédito.
 */
@Entity(
    tableName = "deferred_purchases",
    foreignKeys = [
        ForeignKey(
            entity = CreditCardEntity::class,
            parentColumns = ["id"],
            childColumns = ["creditCardId"],
            onDelete = ForeignKey.CASCADE  // Si se borra la tarjeta, se borran sus compras diferidas
        )
    ],
    indices = [Index("creditCardId")]
)
data class DeferredPurchaseEntity(
    @PrimaryKey
    val id: String,                      // UUID generado
    val creditCardId: String,            // FK → credit_cards.id
    val description: String,             // Ej: "Televisor Samsung 65'"
    val totalAmount: Double,             // Monto total de la compra (ej: 3,600,000)
    val totalInstallments: Int,          // Cuotas totales pactadas (ej: 36)
    val paidInstallments: Int,           // Cuotas ya pagadas (ej: 12)
    val createdAt: Long = System.currentTimeMillis()
)
