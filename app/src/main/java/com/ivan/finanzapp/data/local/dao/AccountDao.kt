package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.domain.model.AccountType
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY createdAt ASC")
    suspend fun getAllAccountsSnapshot(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE type = :type ORDER BY createdAt ASC")
    suspend fun getAccountsByType(type: AccountType): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeAccountById(id: String): Flow<AccountEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    /**
     * Ajusta el saldo de una cuenta sumando un delta (positivo o negativo).
     * Útil para que el [TransactionProcessor] actualice saldos sin
     * necesidad de leer-modificar-escribir manualmente.
     */
    @Query("UPDATE accounts SET currentBalance = currentBalance + :delta WHERE id = :accountId")
    suspend fun adjustBalance(accountId: String, delta: Double)

    /**
     * Establece el saldo de forma absoluta (cuando la notificación trae
     * el "saldo disponible" explícito).
     */
    @Query("UPDATE accounts SET currentBalance = :newBalance, isManualBalance = 0 WHERE id = :accountId")
    suspend fun setAbsoluteBalance(accountId: String, newBalance: Double)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: String)
}
