package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SmsParserTest {

    private val parser = SmsParser()

    @Test
    fun parsesDaviviendaPayrollSmsFromMessagingApp() {
        val parsed = parser.parse(
            title = "",
            text = "DAVIVIENDA Abono Pago de Nomina, \$6,174,962, Cta de Ahorros *5607,Hora 15:54,Lugar Portal pyme BI-KON SAS."
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(TransactionType.INGRESO, parsed.type)
        assertEquals(6_174_962.0, parsed.amount, MONEY_DELTA)
        assertEquals("Nómina - Portal pyme BI-KON SAS", parsed.merchant)
        assertEquals(BankSource.DAVIVIENDA, parsed.source)
    }

    @Test
    fun parsesAvVillasCreditCardPurchasesFromSms() {
        val cases = listOf(
            "AVVillas. 19/06/26 20:48 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 408,123 EN ALIEXPRESS COM" to
                    ExpectedPurchase(408_123.0, "ALIEXPRESS COM"),
            "AVVillas. 18/06/26 19:09 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 17,050 EN SEG ALFA MODULAR" to
                    ExpectedPurchase(17_050.0, "SEG ALFA MODULAR"),
            "AVVillas. 17/06/26 20:30 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 1,299 EN UNE TELCO UNE PAGO EXP" to
                    ExpectedPurchase(1_299.0, "UNE TELCO UNE PAGO EXP"),
            "AVVillas. 17/06/26 16:53 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 14,400 EN BOLD SA AVENA CU" to
                    ExpectedPurchase(14_400.0, "BOLD SA AVENA CU"),
            "AVVillas. 16/06/26 16:51 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 22,000 EN BOLD SA LA VAQUI" to
                    ExpectedPurchase(22_000.0, "BOLD SA LA VAQUI"),
            "AVVillas. 15/06/26 10:14 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 606,139 EN MERCAMIO PLAZA NORTE" to
                    ExpectedPurchase(606_139.0, "MERCAMIO PLAZA NORTE"),
            "AVVillas. 14/06/26 08:33 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 136,173 EN [AMAZON.COM](http://AMAZON.COM)" to
                    ExpectedPurchase(136_173.0, "AMAZON.COM"),
            "AVVillas. 12/06/26 18:41 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 30,000 EN BARBER SHOP" to
                    ExpectedPurchase(30_000.0, "BARBER SHOP"),
            "AVVillas. 10/06/26 07:20 COMPRA CON TU TARJETA CREDITO 5039 POR \$ 166,700 EN GOBERNACION DE CUNDINA" to
                    ExpectedPurchase(166_700.0, "GOBERNACION DE CUNDINA")
        )

        for ((message, expected) in cases) {
            val parsed = parser.parse(title = "", text = message)

            assertNotNull("Expected AV Villas message to parse: $message", parsed)
            requireNotNull(parsed)
            assertEquals(TransactionType.GASTO_TC, parsed.type)
            assertEquals(expected.amount, parsed.amount, MONEY_DELTA)
            assertEquals(expected.merchant, parsed.merchant)
            assertEquals(BankSource.AVVILLAS, parsed.source)
        }
    }

    @Test
    fun parsesRealDaviplataSmsMessages() {
        val cases = listOf(
            "DaviPlata: Recibiste plata por BreB por valor de \$18,500. Consulte el detalle de sus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.INGRESO, 18_500.0, "BreB"),
            "DaviPlata: Recibiste plata por BreB por valor de \$12,000. Consulte el detalle de sus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.INGRESO, 12_000.0, "BreB"),
            "DaviPlata: Recibiste plata por BreB por valor de \$15,000. Consulte el detalle de sus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.INGRESO, 15_000.0, "BreB"),
            "DaviPlata: Recibiste plata por BreB por valor de \$12,000. Consulte el detalle de sus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.INGRESO, 12_000.0, "BreB"),
            "DaviPlata: Recibiste plata por BreB por valor de \$80,000. Consulte el detalle de sus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.INGRESO, 80_000.0, "BreB"),
            "DaviPlata: acabas de Sacar \$140,000 de tu DaviPlata. Para saber mas, consulta tus movimientos en la app." to
                    ExpectedTransaction(TransactionType.TRANSFERENCIA, 140_000.0, "Retiro DaviPlata"),
            "DaviPlata: acabas de hacer una compra por PSE de \$60,000. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 60_000.0, "PSE"),
            "Recibiste \$300,000. Para saber mas, consulta tus movimientos." to
                    ExpectedTransaction(TransactionType.INGRESO, 300_000.0, "Transferencia recibida"),
            "DaviPlata: acabas de hacer una compra por PSE de \$91,087. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 91_087.0, "PSE"),
            "DaviPlata: acabas de hacer una compra por PSE de \$12,800. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 12_800.0, "PSE"),
            "Recibiste \$237,001. Para saber mas, consulta tus movimientos." to
                    ExpectedTransaction(TransactionType.INGRESO, 237_001.0, "Transferencia recibida"),
            "DaviPlata: Pago exitoso por \$204,949. Para saber mas consulta tus movimientos en la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 204_949.0, "Pago DaviPlata"),
            "DaviPlata: acabas de hacer una compra por PSE de \$3,566,187. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 3_566_187.0, "PSE"),
            "Recibiste \$150,000. Para saber mas, consulta tus movimientos." to
                    ExpectedTransaction(TransactionType.INGRESO, 150_000.0, "Transferencia recibida"),
            "Se realizo un descuento de \$50,000 en tu DaviPlata o App Civica. Para saber mas consulta tus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.GASTO, 50_000.0, "Descuento DaviPlata"),
            "DaviPlata: tu compra de \$29,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 29_000.0, "Compra QR"),
            "DaviPlata: tu compra de \$106,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 106_000.0, "Compra QR"),
            "DaviPlata: tu compra de \$20,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 20_000.0, "Compra QR"),
            "DaviPlata: tu compra de \$36,100 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 36_100.0, "Compra QR"),
            "DaviPlata: tu compra de \$18,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 18_000.0, "Compra QR"),
            "DaviPlata: recibiste \$1,178,696 desde una cuenta Davivienda. Usalos para pagar, comprar y mucho mas! Conoce todo lo que puedes hacer en [www.daviplata.com](http://www.daviplata.com)" to
                    ExpectedTransaction(TransactionType.INGRESO, 1_178_696.0, "Cuenta Davivienda"),
            "Recibiste \$959,000. Para saber mas, consulta tus movimientos." to
                    ExpectedTransaction(TransactionType.INGRESO, 959_000.0, "Transferencia recibida"),
            "DaviPlata: tu compra de \$68,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 68_000.0, "Compra QR"),
            "DaviPlata: Pasaste plata a otro banco por valor de \$310,000. Consulta el detalle de tus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.TRANSFERENCIA, 310_000.0, "Otro banco"),
            "DaviPlata: Pasaste plata a otro banco por valor de \$45,000. Consulta el detalle de tus movimientos desde la aplicacion." to
                    ExpectedTransaction(TransactionType.TRANSFERENCIA, 45_000.0, "Otro banco"),
            "DaviPlata: tu compra de \$16,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 16_000.0, "Compra QR"),
            "DaviPlata: tu compra de \$8,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 8_000.0, "Compra QR"),
            "DaviPlata: tu compra de \$5,000 por codigo QR fue exitosa. Para saber mas, consulta tus movimientos desde la app DaviPlata." to
                    ExpectedTransaction(TransactionType.GASTO, 5_000.0, "Compra QR")
        )

        for ((message, expected) in cases) {
            val parsed = parser.parse(title = "", text = message)

            assertNotNull("Expected DaviPlata SMS to parse: $message", parsed)
            requireNotNull(parsed)
            assertEquals(expected.type, parsed.type)
            assertEquals(expected.amount, parsed.amount, MONEY_DELTA)
            assertEquals(expected.merchant, parsed.merchant)
            assertEquals(BankSource.DAVIPLATA, parsed.source)
        }
    }

    private data class ExpectedPurchase(
        val amount: Double,
        val merchant: String
    )

    private data class ExpectedTransaction(
        val type: TransactionType,
        val amount: Double,
        val merchant: String
    )

    private companion object {
        const val MONEY_DELTA = 0.001
    }
}
