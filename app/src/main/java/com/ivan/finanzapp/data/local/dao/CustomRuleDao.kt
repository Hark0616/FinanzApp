package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ivan.finanzapp.data.local.entity.CustomRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomRuleDao {

    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomRuleEntity>>

    @Query("SELECT * FROM custom_rules ORDER BY createdAt DESC")
    suspend fun getAll(): List<CustomRuleEntity>

    @Upsert
    suspend fun upsert(rule: CustomRuleEntity)

    @Query("DELETE FROM custom_rules WHERE id = :id")
    suspend fun delete(id: String)
}
