package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivan.finanzapp.data.local.entity.DebtPaymentApplicationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtPaymentApplicationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(application: DebtPaymentApplicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(application: DebtPaymentApplicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(applications: List<DebtPaymentApplicationEntity>)

    @Query("SELECT * FROM debt_payment_applications WHERE sourceTransactionId = :transactionId LIMIT 1")
    suspend fun getBySourceTransactionId(transactionId: String): DebtPaymentApplicationEntity?

    @Query("SELECT * FROM debt_payment_applications ORDER BY appliedAt DESC")
    fun observeAll(): Flow<List<DebtPaymentApplicationEntity>>

    @Query("SELECT * FROM debt_payment_applications")
    suspend fun getAllSnapshot(): List<DebtPaymentApplicationEntity>
}
