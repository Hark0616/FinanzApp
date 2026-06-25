package com.ivan.finanzapp.data.local.converters

import com.ivan.finanzapp.data.local.entity.NotificationProcessingStatus
import com.ivan.finanzapp.data.local.entity.DebtPaymentApplicationType
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentTargetType
import com.ivan.finanzapp.data.local.entity.PaymentMatchStatus
import com.ivan.finanzapp.data.local.entity.PaymentMatchTargetType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun notificationProcessingStatusRoundTripsThroughRoomConverter() {
        NotificationProcessingStatus.entries.forEach { status ->
            val stored = converters.fromNotificationProcessingStatus(status)

            assertEquals(status, converters.toNotificationProcessingStatus(stored))
        }
    }

    @Test
    fun unknownNotificationProcessingStatusFallsBackToFailed() {
        val status = converters.toNotificationProcessingStatus("BROKEN_STATUS")

        assertEquals(NotificationProcessingStatus.FAILED, status)
    }

    @Test
    fun paymentMatchEnumsRoundTripThroughRoomConverters() {
        PaymentMatchTargetType.entries.forEach { targetType ->
            assertEquals(
                targetType,
                converters.toPaymentMatchTargetType(converters.fromPaymentMatchTargetType(targetType))
            )
        }
        PaymentMatchStatus.entries.forEach { status ->
            assertEquals(
                status,
                converters.toPaymentMatchStatus(converters.fromPaymentMatchStatus(status))
            )
        }
        DebtPaymentApplicationType.entries.forEach { applicationType ->
            assertEquals(
                applicationType,
                converters.toDebtPaymentApplicationType(converters.fromDebtPaymentApplicationType(applicationType))
            )
        }
    }

    @Test
    fun financialAdjustmentTargetTypeRoundTripsThroughRoomConverter() {
        FinancialAdjustmentTargetType.entries.forEach { targetType ->
            assertEquals(
                targetType,
                converters.toFinancialAdjustmentTargetType(
                    converters.fromFinancialAdjustmentTargetType(targetType)
                )
            )
        }
    }
}
