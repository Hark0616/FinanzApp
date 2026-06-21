package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeferredPurchaseDao {
    @Query("SELECT * FROM deferred_purchases WHERE creditCardId = :cardId ORDER BY createdAt DESC")
    fun observeByCardId(cardId: String): Flow<List<DeferredPurchaseEntity>>

    @Query("SELECT * FROM deferred_purchases WHERE creditCardId = :cardId ORDER BY createdAt DESC")
    suspend fun getByCardIdSnapshot(cardId: String): List<DeferredPurchaseEntity>

    @Query("SELECT * FROM deferred_purchases ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DeferredPurchaseEntity>>

    @Upsert
    suspend fun upsert(purchase: DeferredPurchaseEntity)

    @Query("UPDATE deferred_purchases SET paidInstallments = paidInstallments + 1 WHERE id = :purchaseId AND paidInstallments < totalInstallments")
    suspend fun incrementPaidInstallment(purchaseId: String)

    @Query("DELETE FROM deferred_purchases WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM deferred_purchases WHERE id = :id AND paidInstallments >= totalInstallments")
    suspend fun deleteIfFullyPaid(id: String)
}
