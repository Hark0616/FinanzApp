package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivan.finanzapp.data.local.entity.NotificationProcessingStatus
import com.ivan.finanzapp.data.local.entity.NotificationSyncLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationSyncLedgerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: NotificationSyncLedgerEntity)

    @Update
    suspend fun update(entry: NotificationSyncLedgerEntity)

    @Query("SELECT * FROM notification_sync_ledger WHERE id = :id")
    suspend fun getById(id: String): NotificationSyncLedgerEntity?

    @Query("SELECT * FROM notification_sync_ledger ORDER BY receivedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<NotificationSyncLedgerEntity>>

    @Query("SELECT * FROM notification_sync_ledger WHERE status = :status ORDER BY receivedAtMillis DESC LIMIT :limit")
    fun observeRecentByStatus(
        status: NotificationProcessingStatus,
        limit: Int = 100
    ): Flow<List<NotificationSyncLedgerEntity>>

    @Query("SELECT COUNT(*) FROM notification_sync_ledger WHERE status = :status")
    fun observeCountByStatus(status: NotificationProcessingStatus): Flow<Int>
}
