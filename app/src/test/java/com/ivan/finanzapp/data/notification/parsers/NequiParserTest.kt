package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NequiParserTest {

    private val parser = NequiParser()

    @Test
    fun parsesNequiTeEnvio() {
        val parsed = parser.parse(
            title = "Envío",
            text = "HANNAHI CABRERA te envió 50000, ¡lo mejor!"
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(TransactionType.INGRESO, parsed.type)
        assertEquals(50000.0, parsed.amount, MONEY_DELTA)
        assertEquals("HANNAHI CABRERA", parsed.merchant)
        assertEquals(BankSource.NEQUI, parsed.source)
    }

    @Test
    fun parsesNequiEnvioPlataExitoso() {
        val parsed = parser.parse(
            title = "Envío de plata exitoso",
            text = "Te contamos que el envío de plata por \$65.000 fue exitoso. Puedes revisar en ..."
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(TransactionType.TRANSFERENCIA, parsed.type)
        assertEquals(65000.0, parsed.amount, MONEY_DELTA)
        assertEquals("Envío de plata", parsed.merchant)
        assertEquals(BankSource.NEQUI, parsed.source)
    }

    @Test
    fun parsesNequiPagaste() {
        val parsed = parser.parse(
            title = "Nequi",
            text = "Pagaste \$25.000 en RAPPI COLOMBIA. Tu saldo es \$150.000"
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(TransactionType.GASTO, parsed.type)
        assertEquals(25000.0, parsed.amount, MONEY_DELTA)
        assertEquals("RAPPI COLOMBIA", parsed.merchant)
        assertEquals(150000.0, parsed.availableBalance ?: 0.0, MONEY_DELTA)
    }

    private companion object {
        const val MONEY_DELTA = 0.001
    }
}
