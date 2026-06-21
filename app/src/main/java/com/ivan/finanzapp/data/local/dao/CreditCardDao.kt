package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivan.finanzapp.data.local.entity.CreditCardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CreditCardDao {

    @Query("SELECT * FROM credit_cards")
    fun observeAll(): Flow<List<CreditCardEntity>>

    @Query("SELECT * FROM credit_cards WHERE accountId = :accountId LIMIT 1")
    suspend fun getByAccountId(accountId: String): CreditCardEntity?

    @Query("SELECT * FROM credit_cards WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CreditCardEntity?

    @Query("SELECT * FROM credit_cards WHERE accountId = :accountId LIMIT 1")
    fun observeByAccountId(accountId: String): Flow<CreditCardEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CreditCardEntity)

    @Update
    suspend fun update(card: CreditCardEntity)

    /**
     * Incrementa la deuda actual (compra con TC). Para pagos, pasar
     * un [delta] negativo.
     */
    @Query("UPDATE credit_cards SET currentDebt = currentDebt + :delta WHERE id = :cardId")
    suspend fun adjustDebt(cardId: String, delta: Double)

    @Query("DELETE FROM credit_cards WHERE id = :id")
    suspend fun delete(id: String)
}
