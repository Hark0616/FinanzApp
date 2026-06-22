package com.ivan.finanzapp.data.local.dao

import androidx.room.*
import com.ivan.finanzapp.data.local.entity.SyncDeleteLogEntity

@Dao
interface SyncDeleteLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncDeleteLogEntity)

    @Query("SELECT * FROM sync_delete_log ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncDeleteLogEntity>

    @Delete
    suspend fun delete(log: SyncDeleteLogEntity)

    @Delete
    suspend fun deleteAll(logs: List<SyncDeleteLogEntity>)
}
