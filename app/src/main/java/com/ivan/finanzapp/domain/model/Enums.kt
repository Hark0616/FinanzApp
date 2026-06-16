package com.ivan.finanzapp.domain.model

/**
 * Tipos de cuenta soportados.
 */
enum class AccountType {
    AHORROS,
    NEQUI,
    DAVIPLATA,
    TARJETA_CREDITO,
    OTRO
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
