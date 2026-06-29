package com.ivan.finanzapp.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.dao.NotificationSyncLedgerDao
import com.ivan.finanzapp.data.local.dao.TransactionDao
import com.ivan.finanzapp.data.remote.LocalAiClassifier
import com.ivan.finanzapp.data.security.SecureLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationSimulatorReceiver : BroadcastReceiver() {

    @Inject
    lateinit var transactionProcessor: TransactionProcessor

    @Inject
    lateinit var securePrefs: SecurePrefs

    @Inject
    lateinit var localAiClassifier: LocalAiClassifier

    @Inject
    lateinit var notificationSyncLedgerDao: NotificationSyncLedgerDao

    @Inject
    lateinit var transactionDao: TransactionDao

    override fun onReceive(context: Context, intent: Intent) {
        Log.i("NotificationSimulator", "QA receiver action=${intent.action}")
        when (intent.action) {
            ACTION_SIMULATE_NOTIFICATION -> {
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

            ACTION_SET_DEBUG_PROCESSING -> {
                intent.getStringExtra("mode")?.takeIf { it in SUPPORTED_MODES }?.let {
                    securePrefs.setNotificationProcessingMode(it)
                }
                if (intent.hasExtra("local_ai_enabled")) {
                    securePrefs.setLocalAiEnabled(intent.getBooleanExtra("local_ai_enabled", false))
                }
                intent.getStringExtra("cloud_provider")
                    ?.takeIf { it in SUPPORTED_CLOUD_PROVIDERS }
                    ?.let { securePrefs.setCloudAiProvider(it) }
                if (intent.getBooleanExtra("clear_api_key", false)) {
                    securePrefs.clearOpenRouterApiKey()
                }
                intent.getStringExtra("api_key")?.takeIf { it.isNotBlank() }?.let {
                    securePrefs.setOpenRouterApiKey(it.trim())
                }
                SecureLog.i(
                    "NotificationSimulator",
                    "Debug processing config: mode=${securePrefs.getNotificationProcessingMode()}, localAiEnabled=${securePrefs.isLocalAiEnabled()}, cloudProvider=${securePrefs.getCloudAiProvider()}, hasOpenRouterKey=${securePrefs.getOpenRouterApiKey() != null}"
                )
            }

            ACTION_TEST_LOCAL_AI -> {
                val pendingResult = goAsync()
                val packageName = intent.getStringExtra("package")
                    ?: intent.getStringExtra("pkg_name")
                    ?: "com.nequi.MobileApp"
                val title = intent.getStringExtra("title") ?: "Movimiento"
                val text = intent.getStringExtra("text")
                    ?: "Compra por 18750 pesos en cafeteria local"
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        localAiClassifier.runConnectionTest(
                            packageName = packageName,
                            title = title,
                            text = text
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            ACTION_DUMP_QA_STATE -> {
                val pendingResult = goAsync()
                val limit = intent.getIntExtra("limit", 12).coerceIn(1, 50)
                val contains = intent.getStringExtra("contains")?.takeIf { it.isNotBlank() }
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        dumpQaState(limit = limit, contains = contains)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private suspend fun dumpQaState(limit: Int, contains: String?) {
        val allLedger = notificationSyncLedgerDao.getAllSnapshot()
        val filteredLedger = allLedger
            .asSequence()
            .filter { entry ->
                contains == null ||
                        entry.title.contains(contains, ignoreCase = true) ||
                        entry.text.contains(contains, ignoreCase = true) ||
                        entry.merchant?.contains(contains, ignoreCase = true) == true
            }
            .sortedByDescending { it.receivedAtMillis }
            .take(limit)
            .toList()

        val recentTransactions = transactionDao.getRecentTransactionsSnapshot(limit)
            .filter { transaction ->
                contains == null ||
                        transaction.rawNotification.contains(contains, ignoreCase = true) ||
                        transaction.merchant?.contains(contains, ignoreCase = true) == true
            }

        Log.i(
            "NotificationQa",
            "ledgerTotal=${allLedger.size}, filteredLedger=${filteredLedger.size}, filteredTx=${recentTransactions.size}, contains=${contains.orEmpty()}"
        )
        filteredLedger.forEach { entry ->
            Log.i(
                "NotificationQa",
                "LEDGER status=${entry.status} reason=${entry.statusReason.orEmpty()} source=${entry.classifierSource.orEmpty()} amount=${entry.amount ?: 0.0} merchant=${entry.merchant.orEmpty()} pkg=${entry.packageName} title=${entry.title.take(48)} text=${entry.text.take(96)}"
            )
        }
        recentTransactions.forEach { transaction ->
            Log.i(
                "NotificationQa",
                "TX type=${transaction.type} amount=${transaction.amount} merchant=${transaction.merchant.orEmpty()} ai=${transaction.confirmedByAI} review=${transaction.needsReview} raw=${transaction.rawNotification.take(140)}"
            )
        }
    }

    private companion object {
        const val ACTION_SIMULATE_NOTIFICATION = "com.ivan.finanzapp.SIMULATE_NOTIFICATION"
        const val ACTION_SET_DEBUG_PROCESSING = "com.ivan.finanzapp.DEBUG_SET_PROCESSING"
        const val ACTION_TEST_LOCAL_AI = "com.ivan.finanzapp.DEBUG_TEST_LOCAL_AI"
        const val ACTION_DUMP_QA_STATE = "com.ivan.finanzapp.DEBUG_DUMP_QA_STATE"
        val SUPPORTED_MODES = setOf(
            SecurePrefs.MODE_PARSER,
            SecurePrefs.MODE_LOCAL_AI,
            SecurePrefs.MODE_CLOUD_AI
        )
        val SUPPORTED_CLOUD_PROVIDERS = setOf(
            SecurePrefs.CLOUD_PROVIDER_OPENROUTER_DIRECT,
            SecurePrefs.CLOUD_PROVIDER_SUPABASE_EDGE
        )
    }
}
