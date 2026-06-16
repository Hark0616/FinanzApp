package com.ivan.finanzapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivan.finanzapp.data.local.entity.MerchantCategoryMappingEntity

@Dao
interface MerchantCategoryMappingDao {

    @Query("SELECT * FROM merchant_category_mappings WHERE merchantKey = :merchantKey")
    suspend fun getByMerchant(merchantKey: String): MerchantCategoryMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: MerchantCategoryMappingEntity)

    @Query("SELECT * FROM merchant_category_mappings")
    suspend fun getAll(): List<MerchantCategoryMappingEntity>
}
