package com.ivan.finanzapp.data.notification.parsers

import org.junit.Assert.assertEquals
import org.junit.Test

class ParserUtilsTest {

    @Test
    fun parseAmountHandlesColombianThousandsWithComma() {
        assertEquals(408_123.0, ParserUtils.parseAmount("\$ 408,123") ?: 0.0, MONEY_DELTA)
        assertEquals(17_050.0, ParserUtils.parseAmount("\$ 17,050") ?: 0.0, MONEY_DELTA)
        assertEquals(1_299.0, ParserUtils.parseAmount("\$ 1,299") ?: 0.0, MONEY_DELTA)
        assertEquals(3_801_011.0, ParserUtils.parseAmount("\$3,801,011") ?: 0.0, MONEY_DELTA)
    }

    @Test
    fun parseAmountHandlesDotThousandsAndDecimalFormats() {
        assertEquals(1_234_567.0, ParserUtils.parseAmount("\$1.234.567") ?: 0.0, MONEY_DELTA)
        assertEquals(1_234_567.89, ParserUtils.parseAmount("\$1.234.567,89") ?: 0.0, MONEY_DELTA)
        assertEquals(1_234_567.89, ParserUtils.parseAmount("\$1,234,567.89") ?: 0.0, MONEY_DELTA)
        assertEquals(10.50, ParserUtils.parseAmount("\$10,50") ?: 0.0, MONEY_DELTA)
    }

    @Test
    fun cleanMerchantRemovesMarkdownLinksAndExtraPunctuation() {
        assertEquals("AMAZON.COM", ParserUtils.cleanMerchant("[AMAZON.COM](http://AMAZON.COM)."))
    }

    private companion object {
        const val MONEY_DELTA = 0.001
    }
}
