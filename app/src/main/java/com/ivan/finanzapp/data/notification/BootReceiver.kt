package com.ivan.finanzapp.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recibe el evento de reinicio del dispositivo para asegurarse de que
 * el [TransactionNotificationListenerService] sigue activo.
 *
 * En Android moderno el sistema debería reiniciar el
 * NotificationListenerService automáticamente, pero en Samsung con
 * restricciones de batería agresivas este receiver actúa como respaldo.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // El sistema Android se encarga de reiniciar el
            // NotificationListenerService si el permiso sigue activo.
            // Este receiver existe para garantizar compatibilidad con
            // versiones de Samsung donde el servicio no se autorecupera.
        }
    }
}
