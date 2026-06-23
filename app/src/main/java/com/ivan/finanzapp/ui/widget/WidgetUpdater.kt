package com.ivan.finanzapp.ui.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private val scope = CoroutineScope(Dispatchers.Default)
    private var updateJob: Job? = null

    @Synchronized
    fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        updateJob?.cancel()
        updateJob = scope.launch {
            delay(500) // Debounce delay to prevent multiple rapid database writes triggering excessive updates
            Log.d(TAG, "Triggering update for all Glance widgets...")
            try {
                FinanzAppWidget().updateAll(appContext)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update FinanzAppWidget", e)
            }
            try {
                DisponibleNetoWidget().updateAll(appContext)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update DisponibleNetoWidget", e)
            }
            try {
                AutonomiaWidget().updateAll(appContext)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update AutonomiaWidget", e)
            }
            try {
                ProximoVencimientoWidget().updateAll(appContext)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update ProximoVencimientoWidget", e)
            }
        }
    }
}
