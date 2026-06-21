package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
import com.ivan.finanzapp.domain.model.LoanAmortizationType
import com.ivan.finanzapp.domain.model.LoanInterestRateType

/**
 * Representa un crédito o préstamo (crédito de consumo, libre inversión, hipotecario, etc.).
 * A diferencia de las tarjetas de crédito, tiene un saldo deudor fijo inicial, cuotas e intereses fijos/variables.
 */
@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedAccountId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("linkedAccountId")]
)
data class LoanEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    /** Tasa mensual efectiva normalizada usada para cálculo interno. */
    val monthlyInterestRate: Double,
    /** Tasa declarada por el usuario/banco antes de normalizarla. */
    val interestRateInputValue: Double = monthlyInterestRate,
    /** Convención de la tasa declarada. */
    val interestRateType: LoanInterestRateType = LoanInterestRateType.MONTHLY_EFFECTIVE,
    /** Método de amortización pactado. */
    val amortizationType: LoanAmortizationType = LoanAmortizationType.FIXED_INSTALLMENT,
    /** Número total de cuotas pactadas. */
    val totalInstallments: Int,
    /** Cantidad de cuotas pagadas hasta el momento. */
    val paidInstallments: Int = 0,
    /** Valor de la cuota mensual pactada (capital + interes + seguros/cargos). */
    val monthlyInstallmentAmount: Double,
    /** Seguro mensual incluido en la cuota pactada. No reduce capital. */
    val monthlyInsuranceAmount: Double = 0.0,
    /** Cargos fijos mensuales incluidos en la cuota pactada. No reducen capital. */
    val monthlyFeeAmount: Double = 0.0,
    /** Día del mes en que vence el pago de la cuota (1-31). */
    val paymentDay: Int,
    /** Timestamp de la próxima fecha límite de pago de la cuota. */
    val nextPaymentDate: Long,
    /** Cuenta bancaria asociada de donde se debita o donde se depositó el préstamo. */
    val linkedAccountId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
