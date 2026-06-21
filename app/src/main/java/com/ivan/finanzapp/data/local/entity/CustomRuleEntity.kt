package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa una regla de parseo personalizada creada por el usuario de forma interactiva.
 * 
 * La expresión regular (regexPattern) debe contener grupos de captura con nombre para
 * extraer los valores necesarios:
 * - `(?<amount>...)` para extraer el monto de la transacción.
 * - `(?<merchant>...)` para extraer el comercio (opcional).
 */
@Entity(tableName = "custom_rules")
data class CustomRuleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val regexPattern: String,
    val transactionType: String,      // ej: "GASTO", "INGRESO", "GASTO_TC"
    val bankSource: String,           // ej: "DAVIVIENDA", "NEQUI", "SMS"
    /**
     * Tipo de formato para parsear decimales/miles del monto:
     * 0 = LatAm (123.456,78 - punto para miles, coma decimal)
     * 1 = US/Anglo (123,456.78 - coma para miles, punto decimal)
     * 2 = Plano (123456 - sin separadores de miles)
     */
    val amountFormatType: Int,
    val createdAt: Long = System.currentTimeMillis()
)
