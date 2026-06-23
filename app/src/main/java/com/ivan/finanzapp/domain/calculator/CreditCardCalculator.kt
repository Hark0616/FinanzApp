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

    /** Cuota mensual de una compra diferida individual (amortización de capital lineal sin interés). */
    fun installmentAmount(purchase: DeferredPurchaseEntity): Double =
        if (purchase.totalInstallments > 0) purchase.totalAmount / purchase.totalInstallments
        else 0.0

    /**
     * Cuota mensual real de una compra diferida individual, sumando la amortización a capital y los intereses sobre el saldo deudor actual.
     * Si no tiene interés de compra, hereda el interés de la tarjeta.
     */
    fun installmentAmount(purchase: DeferredPurchaseEntity, cardInterestRateEA: Double?): Double {
        val capital = installmentAmount(purchase)
        val remainingInst = remainingInstallments(purchase)
        if (remainingInst <= 0) return 0.0

        val ea = purchase.interestRateEA ?: cardInterestRateEA ?: 0.0
        val r = if (ea > 0.0) Math.pow(1.0 + ea / 100.0, 1.0 / 12.0) - 1.0 else 0.0
        val remainingCapital = capital * remainingInst
        val interest = remainingCapital * r
        return capital + interest
    }

    /** Cuotas restantes de una compra diferida. */
    fun remainingInstallments(purchase: DeferredPurchaseEntity): Int =
        (purchase.totalInstallments - purchase.paidInstallments).coerceAtLeast(0)

    /** Deuda restante de una compra diferida individual. */
    fun remainingDebt(purchase: DeferredPurchaseEntity): Double =
        installmentAmount(purchase) * remainingInstallments(purchase)

    /** Suma de todas las cuotas mensuales activas (lo que realmente cobra el banco cada mes). */
    fun totalMonthlyInstallments(purchases: List<DeferredPurchaseEntity>, cardInterestRateEA: Double?): Double =
        purchases.filter { remainingInstallments(it) > 0 }
            .sumOf { installmentAmount(it, cardInterestRateEA) }

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
        cardInterestRateEA: Double?,
        cutoffDay: Int,
        targetDate: LocalDate
    ): Double {
        val dueInstallments = installmentsDue(purchase, cutoffDay, targetDate)
        if (dueInstallments <= 0) return 0.0

        var simulatedPurchase = purchase
        var totalDue = 0.0
        repeat(dueInstallments) {
            totalDue += installmentAmount(simulatedPurchase, cardInterestRateEA)
            simulatedPurchase = simulatedPurchase.copy(
                paidInstallments = (simulatedPurchase.paidInstallments + 1)
                    .coerceAtMost(simulatedPurchase.totalInstallments)
            )
        }
        return totalDue
    }

    /**
     * Suma de todos los valores a pagar en el periodo actual.
     */
    fun totalAmountDue(
        purchases: List<DeferredPurchaseEntity>,
        cardInterestRateEA: Double?,
        cutoffDay: Int,
        targetDate: LocalDate
    ): Double {
        return purchases.sumOf { amountDue(it, cardInterestRateEA, cutoffDay, targetDate) }
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
     * Fecha de corte que corresponde al vencimiento vigente en una fecha de pago concreta.
     * Es útil al conciliar notificaciones históricas: el ciclo se calcula desde la fecha
     * del movimiento, no desde el día actual del dispositivo.
     */
    fun billingCutoffDateForPayment(card: CreditCardEntity, paymentDate: LocalDate): LocalDate {
        var dueDate = LocalDate.of(paymentDate.year, paymentDate.month, safeDayOfMonth(card.paymentDueDay, paymentDate))
        if (paymentDate.isAfter(dueDate)) {
            dueDate = dueDate.plusMonths(1)
                .withDayOfMonth(safeDayOfMonth(card.paymentDueDay, dueDate.plusMonths(1)))
        }

        return if (card.cutoffDay < card.paymentDueDay) {
            val day = card.cutoffDay.coerceAtMost(dueDate.month.length(dueDate.isLeapYear))
            LocalDate.of(dueDate.year, dueDate.month, day)
        } else {
            val prevMonth = dueDate.minusMonths(1)
            val day = card.cutoffDay.coerceAtMost(prevMonth.month.length(prevMonth.isLeapYear))
            LocalDate.of(prevMonth.year, prevMonth.month, day)
        }
    }

    /**
     * Pago mínimo requerido.
     * Es la suma de las cuotas que corresponden al periodo de facturación actual (al corte actual).
     */
    fun minimumPayment(card: CreditCardEntity, deferredPurchases: List<DeferredPurchaseEntity> = emptyList()): Double {
        val cutoffDate = nextBillingCutoffDate(card)
        return minimumPayment(card, deferredPurchases, cutoffDate)
    }

    fun minimumPayment(
        card: CreditCardEntity,
        deferredPurchases: List<DeferredPurchaseEntity>,
        cutoffDate: LocalDate
    ): Double {
        if (card.currentDebt <= 0.0) return 0.0
        val amountDue = totalAmountDue(deferredPurchases, card.interestRateEA, card.cutoffDay, cutoffDate)
        return amountDue
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

    /**
     * Aplica un pago de extracto/ciclo sin confundir intereses con capital.
     *
     * Para cada cuota facturada primero cubre el interés calculado y solo después
     * avanza o reduce capital. Si sobra dinero, se trata como abono extra a capital.
     */
    fun distributeStatementPayment(
        paymentAmount: Double,
        card: CreditCardEntity,
        purchases: List<DeferredPurchaseEntity>,
        billingCutoffDate: LocalDate
    ): PaymentDistributionResult {
        var remainingPayment = paymentAmount.coerceAtLeast(0.0)
        val updatedById = linkedMapOf<String, DeferredPurchaseEntity>()
        val deletedIds = linkedSetOf<String>()

        val activePurchases = purchases
            .filter { remainingInstallments(it) > 0 }
            .sortedWith(compareBy<DeferredPurchaseEntity> { it.purchaseDate }.thenBy { it.createdAt })

        for (purchase in activePurchases) {
            if (remainingPayment <= MONEY_EPSILON) break
            if (purchase.id in deletedIds) continue

            var current = updatedById[purchase.id] ?: purchase
            val dueInstallments = installmentsDue(current, card.cutoffDay, billingCutoffDate)
                .coerceAtMost(remainingInstallments(current))

            repeat(dueInstallments) {
                if (remainingPayment <= MONEY_EPSILON || current.id in deletedIds) return@repeat

                val principalInstallment = installmentAmount(current)
                val installmentWithInterest = installmentAmount(current, card.interestRateEA)
                if (principalInstallment <= MONEY_EPSILON || installmentWithInterest <= MONEY_EPSILON) {
                    deletedIds.add(current.id)
                    updatedById.remove(current.id)
                    return@repeat
                }

                val interestPortion = (installmentWithInterest - principalInstallment).coerceAtLeast(0.0)
                val interestPaid = interestPortion.coerceAtMost(remainingPayment)
                remainingPayment -= interestPaid

                if (remainingPayment + MONEY_EPSILON >= principalInstallment) {
                    remainingPayment -= principalInstallment
                    val newPaid = current.paidInstallments + 1
                    if (newPaid >= current.totalInstallments) {
                        deletedIds.add(current.id)
                        updatedById.remove(current.id)
                    } else {
                        current = current.copy(paidInstallments = newPaid)
                        updatedById[current.id] = current
                    }
                } else if (remainingPayment > MONEY_EPSILON) {
                    val remainingInst = remainingInstallments(current)
                    val adjustment = remainingPayment * current.totalInstallments.toDouble() / remainingInst.toDouble()
                    val newTotal = (current.totalAmount - adjustment).coerceAtLeast(0.0)
                    remainingPayment = 0.0
                    if (newTotal <= MONEY_EPSILON) {
                        deletedIds.add(current.id)
                        updatedById.remove(current.id)
                    } else {
                        current = current.copy(totalAmount = newTotal)
                        updatedById[current.id] = current
                    }
                }
            }
        }

        if (remainingPayment > MONEY_EPSILON) {
            val remainingPurchases = purchases.mapNotNull { purchase ->
                when {
                    purchase.id in deletedIds -> null
                    updatedById.containsKey(purchase.id) -> updatedById[purchase.id]
                    else -> purchase
                }
            }
            val extraResult = distributePayment(remainingPayment, remainingPurchases)
            extraResult.updatedPurchases.forEach { updatedById[it.id] = it }
            extraResult.deletedPurchaseIds.forEach { id ->
                deletedIds.add(id)
                updatedById.remove(id)
            }
        }

        return PaymentDistributionResult(updatedById.values.toList(), deletedIds.toList())
    }

    enum class UsageLevel { LOW, MEDIUM, HIGH }

    private companion object {
        const val MONEY_EPSILON = 0.0001
    }
}

/**
 * Resultado de distribuir un abono o pago a la tarjeta entre sus compras diferidas.
 */
data class PaymentDistributionResult(
    val updatedPurchases: List<DeferredPurchaseEntity>,
    val deletedPurchaseIds: List<String>
)
