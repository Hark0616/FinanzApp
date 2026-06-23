package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DaviviendaParserTest {

    private val parser = DaviviendaParser()

    @Test
    fun parsesDaviviendaSavingsAccountCredits() {
        val cases = listOf(
            "DAVIVIENDA Abono Pago de Nomina, \$3,801,011, Cta de Ahorros *5607,Hora 16:18,Lugar Portal pyme BI-KON SAS." to
                    ExpectedTransaction(TransactionType.INGRESO, 3_801_011.0, "Portal pyme BI-KON SAS"),
            "DAVIVIENDA Abono Transferencia de Daviplata a Cuenta, \$1,184,964, Cta de Ahorros *5607,Hora 16:35,Lugar DAVIPLATA." to
                    ExpectedTransaction(TransactionType.INGRESO, 1_184_964.0, "DAVIPLATA"),
            "DAVIVIENDA Abono Transferencia a su llave \$73,000, Cta de Ahorros *5607,Hora 21:12,Lugar Llaves Bre-B." to
                    ExpectedTransaction(TransactionType.INGRESO, 73_000.0, "Llaves Bre-B"),
            "DAVIVIENDA Abono Transferencia a su llave \$375,000, Cta de Ahorros *5607,Hora 17:04,Lugar Llaves Bre-B." to
                    ExpectedTransaction(TransactionType.INGRESO, 375_000.0, "Llaves Bre-B"),
            "DAVIVIENDA Abono Transferencia a su llave \$375,000, Cta de Ahorros *5607,Hora 21:02,Lugar Llaves Bre-B." to
                    ExpectedTransaction(TransactionType.INGRESO, 375_000.0, "Llaves Bre-B")
        )

        for ((message, expected) in cases) {
            assertParsed(message, expected)
        }
    }

    @Test
    fun parsesDaviviendaSavingsAccountDebits() {
        val cases = listOf(
            "DAVIVIENDA Descuento Pago a Credito, \$959,000, Cta de Ahorros *5607,Hora 16:21,Lugar App Davivienda." to
                    ExpectedTransaction(TransactionType.GASTO, 959_000.0, "Pago a Credito"),
            "DAVIVIENDA Descuento Pago a Credito, \$713,000, Cta de Ahorros *5607,Hora 16:21,Lugar App Davivienda." to
                    ExpectedTransaction(TransactionType.GASTO, 713_000.0, "Pago a Credito"),
            "DAVIVIENDA Descuento en Internet, \$3,667,203, Cta de Ahorros *5607,Hora 16:38,Lugar PSE BANCO COMERCIAL AV VI." to
                    ExpectedTransaction(TransactionType.TRANSFERENCIA, 3_667_203.0, "Pago Tarjeta de Crédito AV Villas")
        )

        for ((message, expected) in cases) {
            assertParsed(message, expected)
        }
    }

    @Test
    fun parsesDaviviendaCreditCardPurchases() {
        val cases = listOf(
            "DAVIVIENDA: Compra . Aprobado(a), \$31,180, Tarjeta *3620, Hora 07:55,Lugar OPECOM EDS LAS DELICIA." to
                    ExpectedTransaction(TransactionType.GASTO_TC, 31_180.0, "OPECOM EDS LAS DELICIA"),
            "DAVIVIENDA: Compra . Aprobado(a), \$4,023,476, Tarjeta *3620, Hora 13:19,Lugar MERCADO PAGO*SAMSUNG ." to
                    ExpectedTransaction(TransactionType.GASTO_TC, 4_023_476.0, "MERCADO PAGO*SAMSUNG")
        )

        for ((message, expected) in cases) {
            assertParsed(message, expected)
        }
    }

    @Test
    fun parsesWhenBankNameIsNotificationTitle() {
        val parsed = parser.parse(
            title = "DAVIVIENDA",
            text = "Abono Pago de Nomina, \$3,801,011, Cta de Ahorros *5607,Hora 16:18,Lugar Portal pyme BI-KON SAS."
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(TransactionType.INGRESO, parsed.type)
        assertEquals(3_801_011.0, parsed.amount, MONEY_DELTA)
    }

    private fun assertParsed(message: String, expected: ExpectedTransaction) {
        val parsed = parser.parse(title = "", text = message)

        assertNotNull("Expected Davivienda message to parse: $message", parsed)
        requireNotNull(parsed)
        assertEquals(expected.type, parsed.type)
        assertEquals(expected.amount, parsed.amount, MONEY_DELTA)
        assertEquals(expected.merchant, parsed.merchant)
        assertEquals(BankSource.DAVIVIENDA, parsed.source)
    }

    private data class ExpectedTransaction(
        val type: TransactionType,
        val amount: Double,
        val merchant: String
    )

    private companion object {
        const val MONEY_DELTA = 0.001
    }
}
