package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivan.finanzapp.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM assets WHERE type = 'SUELDO' OR UPPER(name) = 'SUELDO'")
    suspend fun deleteSueldoAssets()

    @Query("SELECT * FROM assets")
    suspend fun getAllSnapshot(): List<AssetEntity>
}
