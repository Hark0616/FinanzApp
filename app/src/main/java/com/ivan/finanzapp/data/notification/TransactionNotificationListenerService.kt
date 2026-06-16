package com.ivan.finanzapp.data.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Servicio del sistema que recibe todas las notificaciones del dispositivo.
 *
 * Android requiere que el usuario habilite este permiso manualmente en
 * Configuración → Apps → Acceso especial → Acceso a notificaciones.
 * La app detecta si el permiso está activo y muestra un aviso en el
 * Dashboard si no lo está (ver [com.ivan.finanzapp.ui.dashboard.DashboardViewModel]).
 *
 * El servicio filtra notificaciones por packageName antes de pasar
 * el trabajo al [TransactionProcessor], para minimizar overhead.
 *
 * NOTA SAMSUNG (S26 Ultra): Samsung Device Care puede matar este
 * servicio en background. Guía al usuario para añadir FinanzApp a
 * "Apps sin restricciones" en Cuidado del dispositivo → Batería.
 */
@AndroidEntryPoint
class TransactionNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var transactionProcessor: TransactionProcessor

    @Inject
    lateinit var parserDispatcher: com.ivan.finanzapp.data.notification.parsers.ParserDispatcher

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val packageName = sbn.packageName ?: return

        // Filtro rápido: ignorar notificaciones de apps no bancarias
        if (!parserDispatcher.isSupportedPackage(packageName)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // Ignorar notificaciones vacías o solo con título sin cuerpo
        if (text.isBlank()) return

        transactionProcessor.processAsync(
            packageName = packageName,
            title = title,
            text = text,
            postedAtMillis = sbn.postTime
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No necesitamos hacer nada cuando se elimina una notificación
    }
}
