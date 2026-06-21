package com.ivan.finanzapp.domain.model

enum class AccountType(val displayName: String, val category: String) {
    AHORROS("Cuenta de Ahorros / Corriente", "Cuentas Bancarias"),
    NEQUI("Nequi", "Billeteras Digitales / Bajo Monto"),
    DAVIPLATA("Daviplata", "Billeteras Digitales / Bajo Monto"),
    TARJETA_CREDITO("Tarjeta de Crédito", "Tarjetas de Crédito"),
    EFECTIVO("Efectivo", "Efectivo y Otros"),
    INVERSION("Inversión / Fiducia", "Efectivo y Otros"),
    OTRO("Otro", "Efectivo y Otros")
}

/**
 * Tipos de transacción detectables desde notificaciones.
 */
enum class TransactionType {
    INGRESO,
    GASTO,
    GASTO_TC,       // Compra con tarjeta de crédito
    TRANSFERENCIA,
    PAGO_TC         // Pago a la tarjeta de crédito (abono a deuda)
}

/**
 * Bancos/entidades reconocidas por los parsers.
 */
enum class BankSource {
    DAVIVIENDA,
    NEQUI,
    DAVIPLATA,
    BANCOLOMBIA,
    SMS,
    DESCONOCIDO
}
