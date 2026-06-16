package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ivan.finanzapp.domain.model.TransactionType

/**
 * Una transacción individual extraída de una notificación bancaria
 * (o ingresada manualmente).
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("accountId"), Index("categoryId"), Index("timestamp")]
)
data class TransactionEntity(
    /**
     * Hash determinístico (monto + entidad + timestamp redondeado + comercio)
     * usado para deduplicar notificaciones repetidas o actualizadas.
     */
    @PrimaryKey
    val id: String,
    val accountId: String?,
    val amount: Double,
    val type: TransactionType,
    val merchant: String?,
    val categoryId: String?,
    /** Texto crudo de la notificación, guardado para auditoría/depuración. */
    val rawNotification: String,
    val timestamp: Long,
    /** true si la categoría/datos fueron resueltos vía LLM (OpenRouter). */
    val confirmedByAI: Boolean = false,
    /**
     * true si la confianza del parser/IA fue baja y el usuario debería
     * revisar manualmente esta transacción.
     */
    val needsReview: Boolean = false
)
