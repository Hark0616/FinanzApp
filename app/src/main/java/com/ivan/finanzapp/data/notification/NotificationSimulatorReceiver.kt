package com.ivan.finanzapp.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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

            Log.d("NotificationSimulator", "Simulating notification: package=$packageName, title='$title', text='$text'")
            transactionProcessor.processAsync(
                packageName = packageName,
                title = title,
                text = text,
                postedAtMillis = System.currentTimeMillis()
            )
        }
    }
}
