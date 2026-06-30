package com.ivan.finanzapp.ui.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object WidgetUpdater {
    private const val TAG = "WidgetUpdater"
    private const val DEFAULT_DEBOUNCE_MILLIS = 500L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var updateJob: Job? = null

    @Synchronized
    fun updateAllWidgets(
        context: Context,
        debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS
    ) {
        val appContext = context.applicationContext
        updateJob?.cancel()
        updateJob = scope.launch {
            if (debounceMillis > 0L) {
                delay(debounceMillis)
            }
            Log.d(TAG, "Triggering update for all Glance widgets...")
            updateWidget("FinanzAppWidget") { FinanzAppWidget().updateAll(appContext) }
            updateWidget("DisponibleNetoWidget") { DisponibleNetoWidget().updateAll(appContext) }
            updateWidget("AutonomiaWidget") { AutonomiaWidget().updateAll(appContext) }
            updateWidget("ProximoVencimientoWidget") { ProximoVencimientoWidget().updateAll(appContext) }
            updateWidget("UltimosMovimientosWidget") { UltimosMovimientosWidget().updateAll(appContext) }
        }
    }

    private suspend fun updateWidget(name: String, update: suspend () -> Unit) {
        try {
            update()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update $name", e)
        }
    }
}
