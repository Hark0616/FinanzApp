package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserDispatcherTest {

    private val dispatcher = ParserDispatcher()

    @Test
    fun supportsCurrentDaviviendaAndDaviplataAndroidPackages() {
        assertTrue(dispatcher.isSupportedPackage("com.davivienda.daviviendaapp"))
        assertTrue(dispatcher.isSupportedPackage("com.davivienda.daviplataapp"))
        assertTrue(dispatcher.isSupportedPackage("com.google.android.apps.messaging"))
    }

    @Test
    fun dispatchesDaviplataSmsThroughMessagingPackage() {
        val parsed = dispatcher.dispatch(
            packageName = "com.google.android.apps.messaging",
            title = "",
            text = "DaviPlata: acabas de hacer una compra por PSE de \$91,087. Para saber mas, consulta tus movimientos desde la app DaviPlata."
        )

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(TransactionType.GASTO, parsed.type)
        assertEquals(91_087.0, parsed.amount, MONEY_DELTA)
        assertEquals("PSE", parsed.merchant)
        assertEquals(BankSource.DAVIPLATA, parsed.source)
    }

    private companion object {
        const val MONEY_DELTA = 0.001
    }
}
