package com.ivan.finanzapp.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivan.finanzapp.data.security.SecureLog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationSimulatorReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionProcessor: TransactionProcessor

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.ivan.finanzapp.SIMULATE_NOTIFICATION") {
            val packageName = intent.getStringExtra("package") ?: return
            val title = intent.getStringExtra("title") ?: ""
            val text = intent.getStringExtra("text") ?: ""

            SecureLog.d("NotificationSimulator", "Simulating notification: package=$packageName, textLength=${text.length}")
            transactionProcessor.processAsync(
                packageName = packageName,
                title = title,
                text = text,
                postedAtMillis = System.currentTimeMillis()
            )
        }
    }
}
