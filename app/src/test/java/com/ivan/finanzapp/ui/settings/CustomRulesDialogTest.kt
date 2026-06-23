package com.ivan.finanzapp.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomRulesDialogTest {

    @Test
    fun generatedRegexUsesNamedGroupsOnlyOnce() {
        val pattern = generateRegexFromTokens(
            tokens = listOf("DAVIVIENDA", "Abono", "\$3,801,011", "Lugar", "Portal", "Pyme"),
            amountIndices = setOf(2),
            merchantIndices = setOf(0, 4, 5)
        )

        Regex(pattern)

        assertEquals(1, namedGroupCount(pattern, "amount"))
        assertEquals(1, namedGroupCount(pattern, "merchant"))
    }

    @Test
    fun generatedRegexAllowsOnlyOneNamedAmountGroup() {
        val pattern = generateRegexFromTokens(
            tokens = listOf("Compra", "\$10,000", "saldo", "\$90,000", "Tienda"),
            amountIndices = setOf(1, 3),
            merchantIndices = setOf(4)
        )

        Regex(pattern)

        assertEquals(1, namedGroupCount(pattern, "amount"))
        assertEquals(1, namedGroupCount(pattern, "merchant"))
    }

    private fun namedGroupCount(pattern: String, name: String): Int =
        Regex("""\(\?<$name>""").findAll(pattern).count()
}
