package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PaymentMatchTargetType {
    CREDIT_CARD,
    LOAN
}

enum class PaymentMatchStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED
}

/**
 * Candidato auditable para conciliar una salida bancaria con una deuda.
 *
 * Una sugerencia nunca modifica saldos ni deuda por si sola. Solo describe
 * una posible relacion entre una transaccion fuente y una tarjeta/credito.
 */
@Entity(
    tableName = "payment_match_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sourceTransactionId"),
        Index("status"),
        Index(value = ["sourceTransactionId", "targetType", "targetId"], unique = true)
    ]
)
data class PaymentMatchSuggestionEntity(
    @PrimaryKey
    val id: String,
    val sourceTransactionId: String,
    val targetType: PaymentMatchTargetType,
    val targetId: String,
    val targetName: String,
    val expectedAmount: Double,
    val actualAmount: Double,
    val differenceAmount: Double,
    val confidence: Double,
    val reason: String,
    val status: PaymentMatchStatus = PaymentMatchStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val expiresAt: Long? = null,
    val acceptedApplicationId: String? = null
)
