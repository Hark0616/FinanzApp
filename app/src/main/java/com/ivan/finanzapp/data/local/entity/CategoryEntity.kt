package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Categoría de gasto/ingreso (Mercado, Transporte, Suscripciones, etc.).
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    /** Nombre de un icono de Material Icons (ej. "ShoppingCart"). */
    val icon: String,
    /** Color en formato hex, ej. "#4CAF50". */
    val color: String,
    /** Límite de presupuesto mensual, opcional (para alertas futuras). */
    val budgetLimit: Double? = null,
    val isDefault: Boolean = false
)
