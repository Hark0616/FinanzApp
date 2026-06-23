package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivan.finanzapp.data.local.entity.PaymentMatchStatus
import com.ivan.finanzapp.data.local.entity.PaymentMatchSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentMatchSuggestionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(suggestion: PaymentMatchSuggestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(suggestion: PaymentMatchSuggestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(suggestions: List<PaymentMatchSuggestionEntity>)

    @Update
    suspend fun update(suggestion: PaymentMatchSuggestionEntity)

    @Query("SELECT * FROM payment_match_suggestions WHERE id = :id")
    suspend fun getById(id: String): PaymentMatchSuggestionEntity?

    @Query("SELECT * FROM payment_match_suggestions WHERE sourceTransactionId = :transactionId ORDER BY confidence DESC, createdAt DESC")
    suspend fun getBySourceTransactionId(transactionId: String): List<PaymentMatchSuggestionEntity>

    @Query("SELECT * FROM payment_match_suggestions WHERE sourceTransactionId = :transactionId AND status = :status ORDER BY confidence DESC, createdAt DESC")
    suspend fun getBySourceTransactionIdAndStatus(
        transactionId: String,
        status: PaymentMatchStatus
    ): List<PaymentMatchSuggestionEntity>

    @Query("SELECT * FROM payment_match_suggestions WHERE status = :status ORDER BY confidence DESC, createdAt DESC")
    fun observeByStatus(status: PaymentMatchStatus): Flow<List<PaymentMatchSuggestionEntity>>

    @Query("SELECT * FROM payment_match_suggestions ORDER BY createdAt DESC")
    suspend fun getAllSnapshot(): List<PaymentMatchSuggestionEntity>

    @Query(
        """
        UPDATE payment_match_suggestions
        SET status = :status,
            updatedAt = :updatedAt,
            acceptedApplicationId = :acceptedApplicationId
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: PaymentMatchStatus,
        updatedAt: Long = System.currentTimeMillis(),
        acceptedApplicationId: String? = null
    )

    @Query(
        """
        UPDATE payment_match_suggestions
        SET status = :status,
            updatedAt = :updatedAt
        WHERE sourceTransactionId = :transactionId
          AND id != :acceptedSuggestionId
          AND status = 'PENDING'
        """
    )
    suspend fun updateOtherPendingForTransaction(
        transactionId: String,
        acceptedSuggestionId: String,
        status: PaymentMatchStatus,
        updatedAt: Long = System.currentTimeMillis()
    )
}
