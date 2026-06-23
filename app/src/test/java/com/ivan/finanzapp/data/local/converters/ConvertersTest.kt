package com.ivan.finanzapp.data.local.converters

import com.ivan.finanzapp.data.local.entity.NotificationProcessingStatus
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
}
