package com.ivan.finanzapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Registra los registros que han sido eliminados localmente.
 * Permite al motor de sincronización eliminar estos registros en Supabase
 * antes de hacer el pull de datos nuevos.
 */
@Entity(tableName = "sync_delete_log")
data class SyncDeleteLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tableName: String,
    val recordId: String,
    val createdAt: Long = System.currentTimeMillis()
)
