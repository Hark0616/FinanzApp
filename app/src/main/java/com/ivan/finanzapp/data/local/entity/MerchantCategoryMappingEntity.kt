package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mapeo aprendido comercio -> categoría.
 *
 * Se crea/actualiza cada vez que el usuario corrige manualmente la
 * categoría de una transacción. En adelante, el [CategoryResolver]
 * consultará esta tabla (Nivel 2) antes de recurrir al LLM (Nivel 3).
 */
@Entity(tableName = "merchant_category_mappings")
data class MerchantCategoryMappingEntity(
    /** Nombre del comercio normalizado (mayúsculas, sin tildes/espacios extra). */
    @PrimaryKey
    val merchantKey: String,
    val categoryId: String,
    val updatedAt: Long = System.currentTimeMillis()
)
