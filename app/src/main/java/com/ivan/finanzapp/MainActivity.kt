package com.ivan.finanzapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.dao.NotificationSyncLedgerDao
import com.ivan.finanzapp.data.local.entity.NotificationProcessingStatus
import com.ivan.finanzapp.data.notification.TransactionProcessor
import com.ivan.finanzapp.data.remote.LocalAiClassifier
import com.ivan.finanzapp.data.security.SecureLog
import com.ivan.finanzapp.ui.navigation.FinanzAppNavHost
import com.ivan.finanzapp.ui.navigation.Screen
import com.ivan.finanzapp.ui.navigation.bottomNavScreens
import com.ivan.finanzapp.ui.security.LocalDeviceAuthenticator
import com.ivan.finanzapp.ui.theme.FinanzAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var pendingDeepLinkIntent by mutableStateOf<Intent?>(null)
    private var isLocalUnlocked by mutableStateOf(false)
    private var localUnlockError by mutableStateOf<String?>(null)
    private var isUnlockPromptShowing = false
    private var lastBackgroundAtMillis = 0L
    private var pendingLocalAiDebugIntent: Intent? = null

    @Inject
    lateinit var securePrefs: SecurePrefs

    @Inject
    lateinit var localAiClassifier: LocalAiClassifier

    @Inject
    lateinit var transactionProcessor: TransactionProcessor

    @Inject
    lateinit var notificationSyncLedgerDao: NotificationSyncLedgerDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLinkIntent = intent
        isLocalUnlocked = !securePrefs.isAppLockEnabled()

        setContent {
            FinanzAppTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                val deepLinkIntent = pendingDeepLinkIntent
                val shouldShowLocalLock = securePrefs.isAppLockEnabled() && !isLocalUnlocked
                val showBottomBar = bottomNavScreens.any { screen ->
                    currentDestination?.hierarchy?.any { it.route == screen.route } == true
                }
                val queuedNotificationCount by notificationSyncLedgerDao
                    .observeCountByStatus(NotificationProcessingStatus.QUEUED)
                    .collectAsState(initial = 0)
                val isProcessingQueuedNotifications by transactionProcessor
                    .isProcessingQueuedNotifications
                    .collectAsState()

                LaunchedEffect(deepLinkIntent) {
                    if (deepLinkIntent?.data != null) {
                        navController.handleDeepLink(deepLinkIntent)
                        pendingDeepLinkIntent = null
                    }
                }

                LaunchedEffect(shouldShowLocalLock) {
                    if (shouldShowLocalLock) {
                        requestLocalUnlock()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (showBottomBar) {
                                NavigationBar {
                                    bottomNavScreens.forEach { screen ->
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                                            label = { Text(screen.label) },
                                            selected = currentDestination?.hierarchy?.any {
                                                it.route == screen.route
                                            } == true,
                                            onClick = {
                                                val isSelected = currentDestination?.hierarchy?.any {
                                                    it.route == screen.route
                                                } == true

                                                if (isSelected) {
                                                    navController.currentBackStackEntry?.savedStateHandle?.set("reset_root", true)
                                                } else {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = false
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        FinanzAppNavHost(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    if (shouldShowLocalLock) {
                        LocalAppLockScreen(
                            errorMessage = localUnlockError,
                            onUnlockClick = { requestLocalUnlock() }
                        )
                    }

                    QueuedNotificationsBanner(
                        queuedCount = queuedNotificationCount,
                        isProcessing = isProcessingQueuedNotifications,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 18.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        }

        queueLocalAiDebug(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!securePrefs.isAppLockEnabled()) {
            isLocalUnlocked = true
            localUnlockError = null
            runPendingLocalAiDebug()
            maybeProcessQueuedNotifications()
            return
        }

        val now = System.currentTimeMillis()
        val shouldRelock = lastBackgroundAtMillis == 0L ||
                now - lastBackgroundAtMillis >= LOCAL_LOCK_TIMEOUT_MILLIS
        if (shouldRelock) {
            isLocalUnlocked = false
        }
        runPendingLocalAiDebug()
        maybeProcessQueuedNotifications()
    }

    override fun onPause() {
        lastBackgroundAtMillis = System.currentTimeMillis()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkIntent = intent
        queueLocalAiDebug(intent)
    }

    private fun requestLocalUnlock() {
        if (!securePrefs.isAppLockEnabled() || isLocalUnlocked || isUnlockPromptShowing) return

        isUnlockPromptShowing = true
        localUnlockError = null
        LocalDeviceAuthenticator.authenticate(
            activity = this,
            title = "Desbloquear FinanzApp",
            subtitle = "Usa biometría o el PIN del celular",
            failedMessage = "No se pudo verificar. Inténtalo de nuevo.",
            onSuccess = {
                isUnlockPromptShowing = false
                localUnlockError = null
                isLocalUnlocked = true
                maybeProcessQueuedNotifications()
            },
            onError = { message ->
                isUnlockPromptShowing = false
                localUnlockError = message
            },
            onCancel = {
                isUnlockPromptShowing = false
            }
        )
    }

    private fun queueLocalAiDebug(intent: Intent?) {
        if (!BuildConfig.SECURITY_LAB_MODE || intent == null) return
        val requested = intent.action == ACTION_DEBUG_LOCAL_AI ||
                intent.getBooleanExtra(EXTRA_DEBUG_LOCAL_AI, false)
        if (!requested) return

        pendingLocalAiDebugIntent = Intent(intent)
    }

    private fun runPendingLocalAiDebug() {
        val intent = pendingLocalAiDebugIntent ?: return
        pendingLocalAiDebugIntent = null
        val packageName = intent.getStringExtra("package")
            ?: intent.getStringExtra("pkg_name")
            ?: "com.nequi.MobileApp"
        val title = intent.getStringExtra("title") ?: "Movimiento"
        val text = intent.getStringExtra("text") ?: "Compra por 18750 pesos en cafeteria local"
        SecureLog.i("MainActivity", "Running foreground Gemini Nano debug test.")
        lifecycleScope.launch {
            localAiClassifier.runConnectionTest(
                packageName = packageName,
                title = title,
                text = text
            )
        }
    }

    private fun maybeProcessQueuedNotifications() {
        if (securePrefs.isAppLockEnabled() && !isLocalUnlocked) return
        lifecycleScope.launch {
            delay(1_000)
            transactionProcessor.processQueuedAsync()
        }
    }

    private companion object {
        private const val LOCAL_LOCK_TIMEOUT_MILLIS = 60_000L
        private const val ACTION_DEBUG_LOCAL_AI = "com.ivan.finanzapp.DEBUG_LOCAL_AI"
        private const val EXTRA_DEBUG_LOCAL_AI = "debug_local_ai"
    }
}

@Composable
private fun QueuedNotificationsBanner(
    queuedCount: Int,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    if (queuedCount <= 0 && !isProcessing) return

    val message = if (isProcessing) {
        if (queuedCount > 0) {
            "Sincronizando mensajes irreconocidos en cola ($queuedCount)"
        } else {
            "Sincronizando mensajes irreconocidos"
        }
    } else {
        "Mensajes irreconocidos en cola: $queuedCount"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun LocalAppLockScreen(
    errorMessage: String?,
    onUnlockClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "FinanzApp está protegida",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Desbloquea con biometría o el PIN del celular para ver tus datos financieros.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Button(
                        onClick = onUnlockClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Desbloquear")
                    }
                }
            }
        }
    }
}
