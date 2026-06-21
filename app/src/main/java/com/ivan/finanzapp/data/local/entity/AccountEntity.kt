package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivan.finanzapp.domain.model.AccountType

/**
 * Representa una cuenta financiera del usuario:
 * cuenta de ahorros, Nequi, Daviplata o tarjeta de crédito (esta última
 * tiene además su propio registro en [CreditCardEntity]).
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: AccountType,
    val currentBalance: Double,
    /**
     * true si el saldo fue ingresado/ajustado manualmente por el usuario
     * (sirve como punto de partida cuando el banco no informa saldo
     * disponible en sus notificaciones).
     */
    val isManualBalance: Boolean = true,
    val lastFourDigits: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
