package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Registro auditable de cada pago aplicado a un credito o prestamo.
 */
@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("loanId"),
        Index("transactionId"),
        Index("paymentDate")
    ]
)
data class LoanPaymentEntity(
    @PrimaryKey
    val id: String,
    val loanId: String,
    val transactionId: String?,
    val installmentNumber: Int,
    val scheduledPaymentAmount: Double,
    val actualPaymentAmount: Double,
    val interestAccruedAmount: Double,
    val interestPaidAmount: Double,
    val unpaidInterestAmount: Double,
    val principalAmount: Double,
    val remainingAmountBefore: Double,
    val remainingAmountAfter: Double,
    val paymentDate: Long,
    val createdAt: Long = System.currentTimeMillis()
)
