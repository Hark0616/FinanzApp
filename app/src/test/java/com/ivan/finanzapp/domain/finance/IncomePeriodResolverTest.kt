package com.ivan.finanzapp.domain.finance

import com.ivan.finanzapp.data.local.entity.TransactionEntity
import com.ivan.finanzapp.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class IncomePeriodResolverTest {

    private val zoneId = ZoneId.of("America/Bogota")

    @Test
    fun lateMonthPayrollAppliesToNextMonth() {
        val payroll = income(
            id = "payroll-june",
            amount = 6_174_962.0,
            merchant = "Nómina - Portal pyme BI-KON SAS",
            rawNotification = "DAVIVIENDA Abono Pago de Nomina, \$6,174,962",
            date = LocalDate.of(2026, 6, 30)
        )

        assertFalse(
            IncomePeriodResolver.appliesToMonth(payroll, YearMonth.of(2026, 6), zoneId)
        )
        assertTrue(
            IncomePeriodResolver.appliesToMonth(payroll, YearMonth.of(2026, 7), zoneId)
        )
    }

    @Test
    fun midMonthPayrollStaysInCalendarMonth() {
        val payroll = income(
            id = "payroll-june-15",
            amount = 1_000_000.0,
            merchant = "Nómina",
            rawNotification = "Pago de Nomina",
            date = LocalDate.of(2026, 6, 15)
        )

        assertTrue(
            IncomePeriodResolver.appliesToMonth(payroll, YearMonth.of(2026, 6), zoneId)
        )
        assertFalse(
            IncomePeriodResolver.appliesToMonth(payroll, YearMonth.of(2026, 7), zoneId)
        )
    }

    @Test
    fun regularIncomeDoesNotRollOverAtMonthEnd() {
        val transfer = income(
            id = "transfer-june",
            amount = 150_000.0,
            merchant = "Transferencia recibida",
            rawNotification = "Recibiste \$150,000",
            date = LocalDate.of(2026, 6, 30)
        )

        assertEquals(
            150_000.0,
            IncomePeriodResolver.sumEffectiveMonthlyIncome(
                transactions = listOf(transfer),
                monthDate = LocalDate.of(2026, 6, 1),
                zoneId = zoneId
            ),
            MONEY_DELTA
        )
    }

    @Test
    fun sumsPayrollFromPreviousMonthIntoCurrentMonth() {
        val payroll = income(
            id = "payroll-june",
            amount = 6_174_962.0,
            merchant = "Portal pyme BI-KON SAS",
            rawNotification = "DAVIVIENDA Abono Pago de Nomina, \$6,174,962",
            date = LocalDate.of(2026, 6, 30)
        )
        val julyIncome = income(
            id = "income-july",
            amount = 200_000.0,
            merchant = "Transferencia recibida",
            rawNotification = "Recibiste \$200,000",
            date = LocalDate.of(2026, 7, 2)
        )

        assertEquals(
            6_374_962.0,
            IncomePeriodResolver.sumEffectiveMonthlyIncome(
                transactions = listOf(payroll, julyIncome),
                monthDate = LocalDate.of(2026, 7, 1),
                zoneId = zoneId
            ),
            MONEY_DELTA
        )
    }

    private fun income(
        id: String,
        amount: Double,
        merchant: String,
        rawNotification: String,
        date: LocalDate
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            accountId = "account",
            amount = amount,
            type = TransactionType.INGRESO,
            merchant = merchant,
            categoryId = "cat_ingresos",
            rawNotification = rawNotification,
            timestamp = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        )
    }

    private companion object {
        const val MONEY_DELTA = 0.001
    }
}
