package com.ivan.finanzapp.data.notification

import java.security.MessageDigest

/**
 * Genera un identificador determinístico para una transacción a partir
 * de sus datos clave, usado como PRIMARY KEY en [com.ivan.finanzapp.data.local.entity.TransactionEntity]
 * para deduplicar notificaciones repetidas o reenviadas por el sistema.
 *
 * El timestamp se redondea al minuto para tolerar pequeñas diferencias
 * de tiempo entre notificaciones duplicadas del mismo evento.
 */
object TransactionIdGenerator {

    fun generate(
        packageName: String,
        amount: Double,
        merchant: String?,
        timestampMillis: Long
    ): String {
        val roundedTimestamp = timestampMillis / 60_000 // redondeo al minuto
        val raw = "$packageName|$amount|${merchant.orEmpty()}|$roundedTimestamp"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
