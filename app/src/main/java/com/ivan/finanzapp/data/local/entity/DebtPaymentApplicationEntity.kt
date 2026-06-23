package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DebtPaymentApplicationType {
    CARD_STATEMENT_PAYMENT,
    CARD_FULL_PAYMENT,
    CARD_EXTRA_PAYMENT,
    LOAN_INSTALLMENT
}

/**
 * Registro definitivo de una conciliacion aceptada por el usuario.
 *
 * La transaccion fuente ya movio el saldo de la cuenta origen. Esta entidad
 * solo deja trazabilidad y permite aplicar la reduccion de deuda sin duplicar
 * el debito de caja.
 */
@Entity(
    tableName = "debt_payment_applications",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceTransactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceTransactionId"], unique = true),
        Index("suggestionId"),
        Index(value = ["targetType", "targetId"])
    ]
)
data class DebtPaymentApplicationEntity(
    @PrimaryKey
    val id: String,
    val sourceTransactionId: String,
    val suggestionId: String?,
    val targetType: PaymentMatchTargetType,
    val targetId: String,
    val targetName: String,
    val amount: Double,
    val expectedAmount: Double,
    val differenceAmount: Double,
    val applicationType: DebtPaymentApplicationType,
    val appliedAt: Long = System.currentTimeMillis()
)
