package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NotificationProcessingStatus {
    RECEIVED,
    PARSED,
    DUPLICATE,
    FAILED,
    IGNORED
}

/**
 * Registro crudo y auditable de cada notificacion bancaria que entra al pipeline.
 *
 * La transaccion financiera derivada puede no existir si la notificacion fue duplicada,
 * promocional, de bajo valor transaccional o si el procesamiento fallo.
 */
@Entity(
    tableName = "notification_sync_ledger",
    indices = [
        Index("status"),
        Index("postedAtMillis"),
        Index("receivedAtMillis"),
        Index("transactionId"),
        Index("packageName")
    ]
)
data class NotificationSyncLedgerEntity(
    @PrimaryKey
    val id: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val receivedAtMillis: Long,
    val status: NotificationProcessingStatus = NotificationProcessingStatus.RECEIVED,
    val statusReason: String? = null,
    val transactionId: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val transactionType: String? = null,
    val amount: Double? = null,
    val merchant: String? = null,
    val bankSource: String? = null,
    val confidence: Double? = null,
    val classifierSource: String? = null,
    val errorMessage: String? = null,
    val processedAtMillis: Long? = null,
    val updatedAtMillis: Long = receivedAtMillis
)
