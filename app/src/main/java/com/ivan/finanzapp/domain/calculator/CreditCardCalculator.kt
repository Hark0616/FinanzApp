package com.ivan.finanzapp.domain.calculator

import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
     * Calcula la fecha del primer corte de facturación después de la fecha dada.
     */
    fun firstCutoffAfter(date: LocalDate, cutoffDay: Int): LocalDate {
        val length = date.month.length(date.isLeapYear)
        val day = cutoffDay.coerceAtMost(length)
        var cutoff = LocalDate.of(date.year, date.month, day)
        if (date.isAfter(cutoff)) {
            val nextMonth = date.plusMonths(1)
            val nextLength = nextMonth.month.length(nextMonth.isLeapYear)
            cutoff = LocalDate.of(nextMonth.year, nextMonth.month, cutoffDay.coerceAtMost(nextLength))
        }
        return cutoff
    }

    /**
     * Calcula cuántas cuotas de una compra ya han sido facturadas hasta una fecha determinada [targetDate],
     * basándose en el día de corte de la tarjeta.
     */
    fun billedInstallments(
        purchaseDate: LocalDate,
        cutoffDay: Int,
        targetDate: LocalDate,
        totalInstallments: Int
    ): Int {
        if (purchaseDate.isAfter(targetDate)) return 0

        var count = 0
        var currentCutoff = firstCutoffAfter(purchaseDate, cutoffDay)

        while (!currentCutoff.isAfter(targetDate) && count < totalInstallments) {
            count++
            currentCutoff = currentCutoff.plusMonths(1)
        }
        return count
    }

    /**
     * Método de conveniencia que convierte purchaseDate (Long) a LocalDate y calcula las cuotas facturadas.
     */
    fun billedInstallments(
        purchase: DeferredPurchaseEntity,
        cutoffDay: Int,
        targetDate: LocalDate
    ): Int {
        val pDate = Instant.ofEpochMilli(purchase.purchaseDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return billedInstallments(pDate, cutoffDay, targetDate, purchase.totalInstallments)
    }

    /**
     * Calcula cuántas cuotas están pendientes de pago para el ciclo actual (hasta [targetDate]),
     * comparando las cuotas facturadas con las cuotas ya pagadas.
     */
    fun installmentsDue(
        purchase: DeferredPurchaseEntity,
        cutoffDay: Int,
        targetDate: LocalDate
    ): Int {
        val billed = billedInstallments(purchase, cutoffDay, targetDate)
        return (billed - purchase.paidInstallments).coerceIn(0, purchase.totalInstallments - purchase.paidInstallments)
    }

    /**
     * Valor a pagar para esta compra diferida en el periodo de facturación actual.
     */
    fun amountDue(
        purchase: DeferredPurchaseEntity,
        cutoffDay: Int,
        targetDate: LocalDate
    ): Double {
        return installmentsDue(purchase, cutoffDay, targetDate) * installmentAmount(purchase)
    }

    /**
     * Suma de todos los valores a pagar en el periodo actual.
     */
    fun totalAmountDue(
        purchases: List<DeferredPurchaseEntity>,
        cutoffDay: Int,
        targetDate: LocalDate
    ): Double {
        return purchases.sumOf { amountDue(it, cutoffDay, targetDate) }
    }

    /**
     * Encuentra la fecha de corte de facturación correspondiente al próximo pago.
     */
    fun nextBillingCutoffDate(card: CreditCardEntity): LocalDate {
        val nextPayment = nextPaymentDueDate(card)
        return if (card.cutoffDay < card.paymentDueDay) {
            val day = card.cutoffDay.coerceAtMost(nextPayment.month.length(nextPayment.isLeapYear))
            LocalDate.of(nextPayment.year, nextPayment.month, day)
        } else {
            val prevMonth = nextPayment.minusMonths(1)
            val day = card.cutoffDay.coerceAtMost(prevMonth.month.length(prevMonth.isLeapYear))
            LocalDate.of(prevMonth.year, prevMonth.month, day)
        }
    }

    /**
     * Pago mínimo requerido.
     * Es la suma de las cuotas que corresponden al periodo de facturación actual (al corte actual).
     */
    fun minimumPayment(card: CreditCardEntity, deferredPurchases: List<DeferredPurchaseEntity> = emptyList()): Double {
        if (card.currentDebt <= 0.0) return 0.0
        val cutoffDate = nextBillingCutoffDate(card)
        val amountDue = totalAmountDue(deferredPurchases, card.cutoffDay, cutoffDate)
        return amountDue.coerceAtMost(card.currentDebt)
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

    /**
     * Distribuye un abono o pago a la tarjeta de crédito entre sus compras diferidas activas.
     * Prioriza las compras más antiguas (ordenadas por createdAt) y calcula el abono
     * de cuotas de manera exacta.
     */
    fun distributePayment(
        paymentAmount: Double,
        purchases: List<DeferredPurchaseEntity>
    ): PaymentDistributionResult {
        val updated = mutableListOf<DeferredPurchaseEntity>()
        val deletedIds = mutableListOf<String>()
        var remainingPayment = paymentAmount

        // Ordenar compras activas por fecha de creación (de más antigua a más nueva)
        val activePurchases = purchases
            .filter { (it.totalInstallments - it.paidInstallments) > 0 }
            .sortedBy { it.createdAt }

        for (purchase in activePurchases) {
            if (remainingPayment <= 0.0) {
                updated.add(purchase)
                continue
            }

            val instAmount = if (purchase.totalInstallments > 0) purchase.totalAmount / purchase.totalInstallments else 0.0
            if (instAmount <= 0.0) {
                deletedIds.add(purchase.id)
                continue
            }

            val remainingInst = purchase.totalInstallments - purchase.paidInstallments
            val fullInstPaid = (remainingPayment / instAmount).toInt()
            val numToPay = fullInstPaid.coerceAtMost(remainingInst)

            if (numToPay > 0) {
                val newPaid = purchase.paidInstallments + numToPay
                remainingPayment -= numToPay * instAmount

                if (newPaid >= purchase.totalInstallments) {
                    deletedIds.add(purchase.id)
                } else {
                    val updatedPurchase = purchase.copy(paidInstallments = newPaid)
                    val newRemainingInst = purchase.totalInstallments - newPaid
                    if (remainingPayment > 0.0 && remainingPayment < instAmount) {
                        // Aplicar remanente fraccionario de forma exacta
                        val adjustment = remainingPayment * purchase.totalInstallments.toDouble() / newRemainingInst.toDouble()
                        val newTotal = (updatedPurchase.totalAmount - adjustment).coerceAtLeast(0.0)
                        remainingPayment = 0.0
                        if (newTotal <= 0.0) {
                            deletedIds.add(purchase.id)
                        } else {
                            updated.add(updatedPurchase.copy(totalAmount = newTotal))
                        }
                    } else {
                        updated.add(updatedPurchase)
                    }
                }
            } else {
                // El pago remanente no cubre ni 1 cuota de esta compra, se abona como fracción de forma exacta
                val adjustment = remainingPayment * purchase.totalInstallments.toDouble() / remainingInst.toDouble()
                val newTotal = (purchase.totalAmount - adjustment).coerceAtLeast(0.0)
                remainingPayment = 0.0
                if (newTotal <= 0.0) {
                    deletedIds.add(purchase.id)
                } else {
                    updated.add(purchase.copy(totalAmount = newTotal))
                }
            }
        }

        return PaymentDistributionResult(updated, deletedIds)
    }

    enum class UsageLevel { LOW, MEDIUM, HIGH }
}

/**
 * Resultado de distribuir un abono o pago a la tarjeta entre sus compras diferidas.
 */
data class PaymentDistributionResult(
    val updatedPurchases: List<DeferredPurchaseEntity>,
    val deletedPurchaseIds: List<String>
)
