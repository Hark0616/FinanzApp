package com.ivan.finanzapp.data.notification.parsers

import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType

/**
 * Resultado de aplicar un [BankParser] sobre el texto de una notificación.
 *
 * Todos los campos excepto [type] y [amount] son opcionales porque no
 * todas las notificaciones traen toda la información (ej. no todas
 * incluyen el saldo disponible).
 */
data class ParsedTransaction(
    val type: TransactionType,
    val amount: Double,
    val merchant: String? = null,
    /** Saldo disponible informado por el banco, si la notificación lo incluye. */
    val availableBalance: Double? = null,
    val source: BankSource,
    /**
     * Confianza del parser sobre el resultado (0.0 - 1.0). Por debajo de
     * un umbral (ej. 0.6) la transacción se marca con needsReview = true.
     */
    val confidence: Double = 1.0
)

/**
 * Contrato para los parsers específicos de cada banco/app.
 *
 * Cada implementación debe:
 * 1. Indicar mediante [matches] si esta notificación (identificada por su
 *    `packageName`) corresponde al banco que maneja.
 * 2. Intentar extraer los datos relevantes en [parse]. Si el formato no
 *    coincide con los patrones esperados, debe devolver null para que el
 *    [ParserDispatcher] intente el fallback de IA.
 */
interface BankParser {

    val source: BankSource

    /**
     * Paquetes de Android (apps) que este parser sabe interpretar.
     * Ej: "com.davivienda.daviviendamovil"
     */
    val packageNames: Set<String>

    fun matches(packageName: String): Boolean = packageName in packageNames

    /**
     * Intenta extraer los datos de la transacción desde [title] y [text]
     * (título y cuerpo de la notificación). Devuelve null si el formato
     * no fue reconocido.
     */
    fun parse(title: String, text: String): ParsedTransaction?
}
