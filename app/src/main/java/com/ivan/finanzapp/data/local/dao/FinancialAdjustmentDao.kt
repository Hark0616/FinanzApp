package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialAdjustmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(adjustment: FinancialAdjustmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(adjustments: List<FinancialAdjustmentEntity>)

    @Query("SELECT * FROM financial_adjustments ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<FinancialAdjustmentEntity>>

    @Query("SELECT * FROM financial_adjustments ORDER BY createdAt DESC")
    suspend fun getAllSnapshot(): List<FinancialAdjustmentEntity>
}
