package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SmsParserTest {

    private val parser = SmsParser()

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

    private data class ExpectedPurchase(
        val amount: Double,
        val merchant: String
    )

    private companion object {
        const val MONEY_DELTA = 0.001
    }
}
