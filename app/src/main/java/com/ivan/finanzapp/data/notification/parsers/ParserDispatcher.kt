package com.ivan.finanzapp.data.notification.parsers

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recibe una notificación (packageName + título + texto) y decide qué
 * [BankParser] debe procesarla.
 *
 * Si ningún parser reconoce el formato (o el parser correspondiente no
 * logra extraer los datos), devuelve null y el llamador (normalmente
 * [com.ivan.finanzapp.data.notification.TransactionProcessor]) debe
 * recurrir al fallback de IA vía OpenRouter.
 */
@Singleton
class ParserDispatcher @Inject constructor() {

    private val parsers: List<BankParser> = listOf(
        DaviviendaParser(),
        NequiParser(),
        DaviplataParser(),
        SmsParser()
    )

    /**
     * Conjunto de todos los packageNames soportados, útil para que el
     * [com.ivan.finanzapp.data.notification.TransactionNotificationListenerService]
     * filtre notificaciones irrelevantes antes de procesarlas.
     */
    val supportedPackages: Set<String> = parsers.flatMap { it.packageNames }.toSet()

    /**
     * Intenta parsear la notificación con el parser correspondiente al
     * [packageName]. Devuelve null si no hay parser para ese paquete o si
     * el parser no reconoció el formato del texto.
     */
    fun dispatch(packageName: String, title: String, text: String): ParsedTransaction? {
        val parser = parsers.firstOrNull { it.matches(packageName) } ?: return null
        return parser.parse(title, text)
    }

    /**
     * Indica si el paquete dado corresponde a alguno de los bancos
     * soportados (independientemente de si el texto se puede parsear).
     */
    fun isSupportedPackage(packageName: String): Boolean = packageName in supportedPackages
}
