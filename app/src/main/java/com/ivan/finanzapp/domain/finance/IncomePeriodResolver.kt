package com.ivan.finanzapp.domain.finance

import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.model.TransactionType
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

object IncomePeriodResolver {
    private const val LATE_MONTH_WINDOW_DAYS = 5

    fun sumEffectiveMonthlyIncome(
        transactions: List<TransactionEntity>,
        monthDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Double {
        val targetMonth = YearMonth.from(monthDate)
        return transactions
            .filter { appliesToMonth(it, targetMonth, zoneId) }
            .sumOf { it.amount }
    }

    fun appliesToMonth(
        transaction: TransactionEntity,
        targetMonth: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (transaction.type != TransactionType.INGRESO) return false
        return effectiveMonth(transaction, zoneId) == targetMonth
    }

    fun isPayrollIncome(transaction: TransactionEntity): Boolean {
        if (transaction.type != TransactionType.INGRESO) return false
        val evidence = normalize("${transaction.merchant.orEmpty()} ${transaction.rawNotification}")
        return PAYROLL_KEYWORDS.any { evidence.contains(it) }
    }

    private fun effectiveMonth(
        transaction: TransactionEntity,
        zoneId: ZoneId
    ): YearMonth {
        val transactionDate = Instant.ofEpochMilli(transaction.timestamp)
            .atZone(zoneId)
            .toLocalDate()
        val calendarMonth = YearMonth.from(transactionDate)
        return if (isPayrollIncome(transaction) && isLateMonth(transactionDate)) {
            calendarMonth.plusMonths(1)
        } else {
            calendarMonth
        }
    }

    private fun isLateMonth(date: LocalDate): Boolean {
        val firstLateDay = date.lengthOfMonth() - LATE_MONTH_WINDOW_DAYS + 1
        return date.dayOfMonth >= firstLateDay
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.uppercase()
    }

    private val PAYROLL_KEYWORDS = listOf(
        "NOMINA",
        "SALARIO",
        "SUELDO",
        "PAYROLL"
    )
}
