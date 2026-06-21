package com.ivan.finanzapp.domain.usecase

import com.ivan.finanzapp.data.local.dao.AccountDao
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resuelve a qué [com.ivan.finanzapp.data.local.entity.AccountEntity]
 * pertenece una transacción detectada, a partir del [BankSource] del
 * parser que la procesó y el [TransactionType].
 *
 * Para el MVP se asume una relación simple entre [BankSource] y
 * [AccountType]:
 * - DAVIVIENDA -> AHORROS (o TARJETA_CREDITO si la transacción es GASTO_TC/PAGO_TC)
 * - NEQUI -> NEQUI
 * - DAVIPLATA -> DAVIPLATA
 *
 * Si el usuario tiene varias cuentas del mismo tipo (ej. dos tarjetas de
 * crédito Davivienda), se usa la primera encontrada. En una fase
 * posterior se puede refinar usando los últimos dígitos de la
 * tarjeta/cuenta si la notificación los incluye.
 */
@Singleton
class AccountResolver @Inject constructor(
    private val accountDao: AccountDao
) {

    /**
     * Devuelve el id de la cuenta correspondiente, o null si el usuario
     * no ha configurado ninguna cuenta de ese tipo todavía (la
     * transacción se guardará con `accountId = null` y aparecerá en una
     * sección de "sin asignar" hasta que el usuario configure sus cuentas).
     */
    suspend fun resolveAccountId(
        source: BankSource,
        type: TransactionType,
        rawNotificationText: String? = null
    ): String? {
        val isCreditCardRelated = type == TransactionType.GASTO_TC || type == TransactionType.PAGO_TC

        val accountType = when {
            isCreditCardRelated -> AccountType.TARJETA_CREDITO
            source == BankSource.NEQUI -> AccountType.NEQUI
            source == BankSource.DAVIPLATA -> AccountType.DAVIPLATA
            source == BankSource.DAVIVIENDA -> AccountType.AHORROS
            source == BankSource.BANCOLOMBIA -> AccountType.AHORROS
            else -> null
        }

        val candidates = if (accountType != null) {
            accountDao.getAccountsByType(accountType)
        } else {
            emptyList()
        }

        if (candidates.isEmpty()) return null

        // Buscar correspondencia exacta por últimos 4 dígitos
        if (!rawNotificationText.isNullOrBlank()) {
            val matched = candidates.firstOrNull { candidate ->
                val digits = candidate.lastFourDigits
                !digits.isNullOrBlank() && rawNotificationText.contains(digits)
            }
            if (matched != null) return matched.id
        }

        return candidates.firstOrNull()?.id
    }
}
