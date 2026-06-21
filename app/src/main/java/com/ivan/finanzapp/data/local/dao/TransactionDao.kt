package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivan.finanzapp.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Resultado agregado: total gastado por categoría dentro de un rango
 * de tiempo. Se usa para el gráfico de gastos del Dashboard.
 */
data class CategorySpending(
    val categoryId: String?,
    val total: Double
)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY timestamp DESC")
    fun observeByAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE needsReview = 1 ORDER BY timestamp DESC")
    fun observeNeedsReview(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    /**
     * Inserta la transacción solo si no existe (deduplicación por hash/id).
     * Devuelve el número de filas insertadas (0 si ya existía = duplicado).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Suma de gastos (GASTO y GASTO_TC) agrupados por categoría,
     * dentro del rango [startMillis, endMillis). Usado para el gráfico
     * de "Gastos del mes por categoría" del Dashboard.
     */
    @Query(
        """
        SELECT categoryId, SUM(amount) as total
        FROM transactions
        WHERE type IN ('GASTO', 'GASTO_TC', 'TRANSFERENCIA')
          AND timestamp >= :startMillis AND timestamp < :endMillis
        GROUP BY categoryId
        ORDER BY total DESC
        """
    )
    fun observeSpendingByCategory(startMillis: Long, endMillis: Long): Flow<List<CategorySpending>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp >= :startMillis AND timestamp < :endMillis
        ORDER BY timestamp DESC
        """
    )
    fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>
}
