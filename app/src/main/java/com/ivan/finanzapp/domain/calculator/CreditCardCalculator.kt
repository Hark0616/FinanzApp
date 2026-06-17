package com.ivan.finanzapp.domain.calculator

import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Todos los cálculos financieros relacionados con una tarjeta de crédito.
 * Sin estado (stateless): recibe los datos y devuelve los resultados.
 */
@Singleton
class CreditCardCalculator @Inject constructor() {

    /** Cupo disponible en pesos. */
    fun availableCredit(card: CreditCardEntity): Double =
        (card.creditLimit - card.currentDebt).coerceAtLeast(0.0)

    /** Porcentaje de uso del cupo (0–100). */
    fun usagePercentage(card: CreditCardEntity): Double =
        if (card.creditLimit == 0.0) 0.0
        else (card.currentDebt / card.creditLimit * 100).coerceIn(0.0, 100.0)

    /** Cuota mensual de una compra diferida individual. */
    fun installmentAmount(purchase: DeferredPurchaseEntity): Double =
        if (purchase.totalInstallments > 0) purchase.totalAmount / purchase.totalInstallments
        else 0.0

    /** Cuotas restantes de una compra diferida. */
    fun remainingInstallments(purchase: DeferredPurchaseEntity): Int =
        (purchase.totalInstallments - purchase.paidInstallments).coerceAtLeast(0)

    /** Deuda restante de una compra diferida individual. */
    fun remainingDebt(purchase: DeferredPurchaseEntity): Double =
        installmentAmount(purchase) * remainingInstallments(purchase)

    /** Suma de todas las cuotas mensuales activas (lo que realmente cobra el banco cada mes). */
    fun totalMonthlyInstallments(purchases: List<DeferredPurchaseEntity>): Double =
        purchases.filter { remainingInstallments(it) > 0 }
            .sumOf { installmentAmount(it) }

    /** Deuda total calculada como suma de deuda restante de todas las compras diferidas. */
    fun totalDeferredDebt(purchases: List<DeferredPurchaseEntity>): Double =
        purchases.sumOf { remainingDebt(it) }

    /**
     * Pago mínimo requerido.
     * Se calcula como la suma de las cuotas mensuales de las compras diferidas activas.
     * Si no hay compras diferidas, el pago mínimo es $0.
     */
    fun minimumPayment(card: CreditCardEntity, deferredPurchases: List<DeferredPurchaseEntity> = emptyList()): Double {
        if (card.currentDebt <= 0.0) return 0.0
        val deferredMonthly = totalMonthlyInstallments(deferredPurchases)
        return deferredMonthly.coerceAtMost(card.currentDebt)
    }


    /**
     * Calcula los días que faltan para la próxima fecha de pago,
     * considerando el ciclo de facturación (corte + días de gracia).
     *
     * Si la fecha de pago ya pasó en este mes, busca la del mes siguiente.
     */
    fun daysUntilPaymentDue(card: CreditCardEntity): Int {
        val today = LocalDate.now()
        var dueDate = LocalDate.of(today.year, today.month, safeDayOfMonth(card.paymentDueDay, today))
        if (!dueDate.isAfter(today)) {
            dueDate = dueDate.plusMonths(1)
                .withDayOfMonth(safeDayOfMonth(card.paymentDueDay, dueDate.plusMonths(1)))
        }
        return (dueDate.toEpochDay() - today.toEpochDay()).toInt()
    }

    /** Próxima fecha de pago como [LocalDate]. */
    fun nextPaymentDueDate(card: CreditCardEntity): LocalDate {
        val today = LocalDate.now()
        var dueDate = LocalDate.of(today.year, today.month, safeDayOfMonth(card.paymentDueDay, today))
        if (!dueDate.isAfter(today)) {
            dueDate = dueDate.plusMonths(1)
                .withDayOfMonth(safeDayOfMonth(card.paymentDueDay, dueDate.plusMonths(1)))
        }
        return dueDate
    }

    /** Próxima fecha de corte como [LocalDate]. */
    fun nextCutoffDate(card: CreditCardEntity): LocalDate {
        val today = LocalDate.now()
        var cutoff = LocalDate.of(today.year, today.month, safeDayOfMonth(card.cutoffDay, today))
        if (!cutoff.isAfter(today)) {
            cutoff = cutoff.plusMonths(1)
                .withDayOfMonth(safeDayOfMonth(card.cutoffDay, cutoff.plusMonths(1)))
        }
        return cutoff
    }

    /**
     * Proyección del interés del próximo período si NO se paga el total.
     * Solo disponible si la tarjeta tiene [CreditCardEntity.interestRateEA] configurada.
     * Fórmula: i_mensual = (1 + EA)^(1/12) - 1
     */
    fun projectedMonthlyInterest(card: CreditCardEntity): Double? {
        val ea = card.interestRateEA ?: return null
        val monthlyRate = Math.pow(1.0 + ea / 100.0, 1.0 / 12.0) - 1.0
        return card.currentDebt * monthlyRate
    }

    /**
     * Color semáforo de uso del cupo:
     * - Verde: < 30%
     * - Amarillo: 30–70%
     * - Rojo: > 70%
     */
    fun usageTrafficLight(card: CreditCardEntity): UsageLevel {
        return when {
            usagePercentage(card) < 30.0 -> UsageLevel.LOW
            usagePercentage(card) < 70.0 -> UsageLevel.MEDIUM
            else -> UsageLevel.HIGH
        }
    }

    /**
     * Ajusta el día del mes para meses cortos (ej. día 31 en febrero
     * se convierte en el último día del mes).
     */
    private fun safeDayOfMonth(day: Int, reference: LocalDate): Int =
        day.coerceAtMost(reference.month.length(reference.isLeapYear))

    enum class UsageLevel { LOW, MEDIUM, HIGH }
}
