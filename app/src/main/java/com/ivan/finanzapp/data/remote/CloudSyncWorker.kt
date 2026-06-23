package com.ivan.finanzapp.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SupabaseSyncManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return syncManager.sync().fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (error.message?.contains("Usuario no autenticado", ignoreCase = true) == true) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        )
    }
}
