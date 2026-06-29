package com.ivan.finanzapp.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.SecurePrefs
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentEntity
import com.ivan.finanzapp.data.local.entity.FinancialAdjustmentTargetType
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivan.finanzapp.ui.security.LocalDeviceAuthenticator
import com.ivan.finanzapp.ui.security.findFragmentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var securityMessage by remember { mutableStateOf<String?>(null) }
    var isSecurityPromptOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshNotificationPermission()
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsHeader()
            }

            item {
                CaptureHealthCard(
                    state = state,
                    onConfigureClick = { viewModel.toggleProcessingDialog(true) },
                    onOpenPermissionClick = {
                        context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("Fuentes de datos")
                        Text(
                            text = "Cuentas, tarjetas y últimos 4 dígitos para asignar notificaciones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { viewModel.toggleAddAccountDialog(true) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar", fontSize = 13.sp)
                    }
                }
            }

            if (state.accounts.isEmpty()) {
                item {
                    EmptyDataSourcesCard(onAddClick = { viewModel.toggleAddAccountDialog(true) })
                }
            } else {
                items(state.accounts, key = { it.id }) { account ->
                    val isCredit = account.type == com.ivan.finanzapp.domain.model.AccountType.TARJETA_CREDITO
                    val displayValue = if (isCredit) {
                        val debt = state.creditCardsMap[account.id]?.currentDebt ?: 0.0
                        "Deuda: ${formatCOP(debt)}"
                    } else {
                        formatCOP(account.currentBalance)
                    }
                    val valueColor = if (isCredit && (state.creditCardsMap[account.id]?.currentDebt ?: 0.0) > 0.0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    AccountItemRow(
                        account = account,
                        displayValue = displayValue,
                        valueColor = valueColor,
                        onEditClick = { accountToEdit = account },
                        onDeleteClick = { accountToDelete = account }
                    )
                }
            }

            if (state.recentFinancialAdjustments.isNotEmpty()) {
                item {
                    RecentFinancialAdjustmentsCard(state.recentFinancialAdjustments)
                }
            }

            item {
                SecuritySettingsCard(
                    state = state,
                    securityMessage = securityMessage,
                    isBusy = isSecurityPromptOpen,
                    onAppLockToggle = { enabled ->
                        if (!enabled) {
                            isSecurityPromptOpen = true
                            requestLocalSecurityConfirmation(
                                context = context,
                                title = "Desactivar seguridad local",
                                subtitle = "Confirma con tu huella o PIN para desactivar",
                                cancelMessage = "Confirmación cancelada. El bloqueo local sigue activo.",
                                onSuccess = {
                                    isSecurityPromptOpen = false
                                    securityMessage = "Bloqueo local desactivado."
                                    viewModel.setAppLockEnabled(false)
                                },
                                onError = { message ->
                                    isSecurityPromptOpen = false
                                    securityMessage = message
                                }
                            )
                        } else {
                            isSecurityPromptOpen = true
                            requestLocalSecurityConfirmation(
                                context = context,
                                title = "Activar seguridad local",
                                subtitle = "Confirma con biometría o PIN del celular",
                                cancelMessage = "Confirmación cancelada. No se activó el bloqueo local.",
                                onSuccess = {
                                    isSecurityPromptOpen = false
                                    securityMessage = "Bloqueo local activado."
                                    viewModel.setAppLockEnabled(true)
                                },
                                onError = { message ->
                                    isSecurityPromptOpen = false
                                    securityMessage = message
                                }
                            )
                        }
                    }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Sección: Copia de Seguridad y Sincronización en la Nube
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.currentUserEmail != null) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (state.currentUserEmail != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Backup y sincronización",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.currentUserEmail != null)
                                        "Conectado como ${state.currentUserEmail}"
                                    else "Tus datos siguen locales hasta que conectes una cuenta.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (state.currentUserEmail != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        text = if (state.lastCloudSyncAt > 0L)
                                            "Última copia: ${formatLastSyncTime(state.lastCloudSyncAt)}"
                                        else "Aún no hay una copia confirmada en la nube.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "La app también intentará respaldar en segundo plano cuando haya conexión.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                    )
                                }
                            }

                            AnimatedVisibility(visible = state.syncStatusMessage != null) {
                                state.syncStatusMessage?.let { message ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = state.syncErrorMessage != null) {
                                state.syncErrorMessage?.let { message ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.syncNow() },
                                    modifier = Modifier.padding(end = 8.dp),
                                    enabled = !state.isSyncing
                                ) {
                                    if (state.isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (state.isSyncing) "Sincronizando" else "Sincronizar ahora", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { viewModel.signOut() }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cerrar Sesión", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        } else {
                            Button(
                                onClick = onNavigateToAuth,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Activar backup en la nube", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleProcessingDialog(true) },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Automatización",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Parser local, IA opcional, reglas personalizadas y privacidad.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Configurar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Diálogo para Agregar Cuenta
        if (state.isAddAccountDialogVisible) {
            AddAccountDialog(
                onDismiss = { viewModel.toggleAddAccountDialog(false) },
                onConfirm = { name, type, balance, limit, cutoff, due, interest, digits ->
                    viewModel.addAccount(name, type, balance, limit, cutoff, due, interest, digits)
                }
            )
        }

        // Diálogo de Confirmación para Eliminar Cuenta
        accountToDelete?.let { account ->
            AlertDialog(
                onDismissRequest = { accountToDelete = null },
                title = { Text("Eliminar cuenta / instrumento") },
                text = {
                    Text(
                        "¿Deseas eliminar \"${account.name}\"? Las transacciones históricas quedarán sin cuenta asignada para no inventar saldos. " +
                                "Si es una tarjeta, también se eliminará su configuración asociada."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteAccount(account.id)
                            accountToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { accountToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo para correcciones manuales auditadas
        accountToEdit?.let { account ->
            val isCredit = account.type == AccountType.TARJETA_CREDITO
            val associatedCard = state.creditCardsMap[account.id]

            var editBalance by remember(account.id) {
                mutableStateOf(if (isCredit) "" else formatEditableAmount(account.currentBalance))
            }
            var editCreditLimit by remember(account.id) {
                mutableStateOf(associatedCard?.creditLimit?.let(::formatEditableAmount) ?: "")
            }
            var editCurrentDebt by remember(account.id) {
                mutableStateOf(associatedCard?.currentDebt?.let(::formatEditableAmount) ?: "")
            }
            var correctionReason by remember(account.id) { mutableStateOf("") }

            val parsedBalance = parseMoneyInput(editBalance)
            val parsedLimit = parseMoneyInput(editCreditLimit)
            val parsedDebt = parseMoneyInput(editCurrentDebt)
            val hasReason = correctionReason.trim().length >= 6
            val canSave = if (isCredit) {
                hasReason && parsedLimit != null && parsedDebt != null
            } else {
                hasReason && parsedBalance != null
            }

            AlertDialog(
                onDismissRequest = { accountToEdit = null },
                title = { Text("Corrección auditada") },
                text = {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            Text(
                                text = account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isCredit) {
                                    "Esto no registra pagos ni compras. Solo corrige cupo o deuda visible con trazabilidad."
                                } else {
                                    "Esto no crea una transacción. Solo corrige el saldo visible con trazabilidad."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isCredit) {
                            item {
                                OutlinedTextField(
                                    value = editBalance,
                                    onValueChange = { editBalance = it },
                                    label = { Text("Nuevo saldo manual ($)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        } else {
                            item {
                                OutlinedTextField(
                                    value = editCreditLimit,
                                    onValueChange = { editCreditLimit = it },
                                    label = { Text("Cupo límite ($)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                            item {
                                OutlinedTextField(
                                    value = editCurrentDebt,
                                    onValueChange = { editCurrentDebt = it },
                                    label = { Text("Deuda visible ($)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = correctionReason,
                                onValueChange = { correctionReason = it },
                                label = { Text("Motivo obligatorio") },
                                placeholder = { Text("Ej: saldo confirmado en extracto") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = canSave,
                        onClick = {
                            if (isCredit) {
                                val limit = parsedLimit ?: return@Button
                                val debt = parsedDebt ?: return@Button
                                viewModel.recordCreditCardCorrection(account.id, limit, debt, correctionReason)
                            } else {
                                val balance = parsedBalance ?: return@Button
                                viewModel.recordAccountBalanceCorrection(account.id, balance, correctionReason)
                            }
                            accountToEdit = null
                        }
                    ) {
                        Text("Guardar corrección")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { accountToEdit = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo Consolidador para Automatización
        if (state.isProcessingDialogVisible) {
            AutomationSettingsDialog(
                state = state,
                onDismiss = { viewModel.toggleProcessingDialog(false) },
                onModeSelected = { viewModel.setNotificationProcessingMode(it) },
                onSaveApiKey = { viewModel.saveApiKey(it) },
                onLocalAiToggle = { viewModel.setLocalAiEnabled(it) },
                onOpenCustomRules = { viewModel.toggleCustomRulesDialog(true) },
                apiKeyInputInitial = state.apiKey
            )
        }

        // Diálogo para Reglas de Parseo Personalizadas
        if (state.isCustomRulesDialogVisible) {
            CustomRulesDialog(
                state = state,
                onDismiss = { viewModel.toggleCustomRulesDialog(false) },
                onSaveRule = { rule -> viewModel.saveCustomRule(rule) },
                onDeleteRule = { id -> viewModel.deleteCustomRule(id) }
            )
        }
    }
}

private fun formatLastSyncTime(timestampMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestampMillis))
}

private fun parseMoneyInput(value: String): Double? {
    val raw = value
        .trim()
        .replace("$", "")
        .replace(" ", "")
    val commaCount = raw.count { it == ',' }
    val dotCount = raw.count { it == '.' }
    val cleaned = when {
        commaCount > 1 && dotCount == 0 -> raw.replace(",", "")
        dotCount > 1 && commaCount == 0 -> raw.replace(".", "")
        commaCount > 0 && dotCount > 0 -> {
            if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) {
                raw.replace(".", "").replace(",", ".")
            } else {
                raw.replace(",", "")
            }
        }
        commaCount == 1 -> {
            val decimals = raw.substringAfterLast(',').length
            if (decimals == 3) raw.replace(",", "") else raw.replace(",", ".")
        }
        dotCount == 1 -> {
            val decimals = raw.substringAfterLast('.').length
            if (decimals == 3) raw.replace(".", "") else raw
        }
        else -> raw
    }
    return cleaned.toDoubleOrNull()?.takeIf { it >= 0.0 }
}

private fun formatEditableAmount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toString()
    }
}

private fun requestLocalSecurityConfirmation(
    context: Context,
    title: String,
    subtitle: String,
    cancelMessage: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        onError("No se pudo abrir la confirmación de seguridad.")
        return
    }

    LocalDeviceAuthenticator.authenticate(
        activity = activity,
        title = title,
        subtitle = subtitle,
        cancelMessage = cancelMessage,
        onSuccess = onSuccess,
        onError = onError
    )
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Centro de control para captura, fuentes y seguridad financiera.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CaptureHealthCard(
    state: SettingsUiState,
    onConfigureClick: () -> Unit,
    onOpenPermissionClick: () -> Unit
) {
    val total = state.ledgerTotalCount
    val statusTitle = when {
        !state.isNotificationListenerEnabled -> "Permiso requerido"
        state.ledgerRecentFailedCount > 0 -> "Revisar fallos"
        state.ledgerRecentCount == 0 && total > 0 -> "Sin actividad reciente"
        total == 0 -> "Sin capturas todavía"
        state.ledgerParsedCount > 0 -> "Captura operativa"
        else -> "Captura en observación"
    }
    val statusColor = when {
        !state.isNotificationListenerEnabled -> MaterialTheme.colorScheme.error
        state.ledgerRecentFailedCount > 0 -> MaterialTheme.colorScheme.error
        total == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val latestReceivedText = state.latestLedgerEntry?.let {
        val status = it.status.name.lowercase().replaceFirstChar(Char::titlecase)
        "Recibida ${formatLastSyncTime(it.receivedAtMillis)} · $status"
    } ?: "Aún no se ha recibido una notificación bancaria."
    val latestText = state.latestParsedLedgerEntry?.let {
        "Parseada ${formatLastSyncTime(it.receivedAtMillis)}"
    } ?: latestReceivedText

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Estado de captura",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = latestText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CaptureStatChip("24h", state.ledgerRecentCount, Modifier.weight(1f))
                CaptureStatChip("Parseadas", state.ledgerParsedCount, Modifier.weight(1f))
                CaptureStatChip(
                    "Cola",
                    state.ledgerQueuedCount,
                    Modifier.weight(1f),
                    isWarning = state.ledgerQueuedCount > 0
                )
                CaptureStatChip(
                    "Fallos",
                    state.ledgerRecentFailedCount,
                    Modifier.weight(1f),
                    isWarning = state.ledgerRecentFailedCount > 0
                )
            }

            if (!state.isNotificationListenerEnabled) {
                Button(
                    onClick = onOpenPermissionClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Activar acceso a notificaciones")
                }
            } else {
                OutlinedButton(
                    onClick = onConfigureClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Configurar captura y reglas")
                }
            }
        }
    }
}

@Composable
private fun CaptureStatChip(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isWarning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isWarning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isWarning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun EmptyDataSourcesCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Sin fuentes configuradas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Agrega cuentas y tarjetas con sus últimos 4 dígitos para que las notificaciones se asignen sin confundir saldos, deuda o pagos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onAddClick,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar fuente")
            }
        }
    }
}

@Composable
private fun RecentFinancialAdjustmentsCard(adjustments: List<FinancialAdjustmentEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Correcciones recientes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ajustes manuales con motivo y valor anterior/nuevo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            adjustments.take(4).forEach { adjustment ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = adjustment.targetName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = adjustmentTargetLabel(adjustment.targetType),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${formatCOP(adjustment.previousValue)} -> ${formatCOP(adjustment.newValue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = adjustment.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun adjustmentTargetLabel(targetType: FinancialAdjustmentTargetType): String {
    return when (targetType) {
        FinancialAdjustmentTargetType.ACCOUNT_BALANCE -> "Saldo"
        FinancialAdjustmentTargetType.CREDIT_CARD_LIMIT -> "Cupo"
        FinancialAdjustmentTargetType.CREDIT_CARD_DEBT -> "Deuda"
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SecuritySettingsCard(
    state: SettingsUiState,
    securityMessage: String?,
    isBusy: Boolean,
    onAppLockToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Seguridad local",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Protege la app con biometría o el PIN del celular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.isAppLockEnabled,
                    onCheckedChange = onAppLockToggle,
                    enabled = !isBusy
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (state.isAppLockEnabled)
                            "Activo: se pedirá desbloqueo al abrir la app y al volver tras estar en segundo plano."
                        else
                            "Inactivo: puedes activarlo cuando quieras pedir verificación al abrir la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Este bloqueo no reemplaza tu sesión de Google/Supabase; solo protege los datos locales ya descargados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }
            }

            AnimatedVisibility(visible = securityMessage != null) {
                securityMessage?.let { message ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (message.contains("activado", ignoreCase = true)) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        }
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun NotificationModeSelector(
    state: SettingsUiState,
    onModeSelected: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onLocalAiToggle: (Boolean) -> Unit,
    apiKeyInputInitial: String
) {
    var apiKeyInput by remember(apiKeyInputInitial) { mutableStateOf(apiKeyInputInitial) }
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Opción 1: Flujo automático completo
        val isParser = state.processingMode == SecurePrefs.MODE_PARSER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onModeSelected(SecurePrefs.MODE_PARSER)
                    onLocalAiToggle(true)
                },
            colors = CardDefaults.cardColors(
                containerColor = if (isParser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder().takeIf { !isParser }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isParser,
                    onClick = {
                        onModeSelected(SecurePrefs.MODE_PARSER)
                        onLocalAiToggle(true)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Automático: Reglas → IA Local → IA Nube",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Primero usa parsers y reglas personalizadas. Si fallan, intenta IA local y luego OpenRouter como último recurso.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Opción 2: IA Local (Gemini Nano)
        val isLocalAi = state.processingMode == SecurePrefs.MODE_LOCAL_AI
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = state.isLocalAiSupported) {
                    onModeSelected(SecurePrefs.MODE_LOCAL_AI)
                    onLocalAiToggle(true)
                },
            colors = CardDefaults.cardColors(
                containerColor = if (isLocalAi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder().takeIf { !isLocalAi }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isLocalAi,
                    enabled = state.isLocalAiSupported,
                    onClick = {
                        onModeSelected(SecurePrefs.MODE_LOCAL_AI)
                        onLocalAiToggle(true)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Privado: Reglas → IA Local",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (state.isLocalAiSupported) Color.Unspecified else Color.Gray
                        )
                        Spacer(Modifier.width(6.dp))
                        if (!state.isLocalAiSupported) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "No compatible",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val descText = if (state.isLocalAiSupported) {
                        "Usa reglas y fallback local únicamente. No envía notificaciones a OpenRouter."
                    } else {
                        "IA local requiere un dispositivo compatible; si no está disponible, este modo no tendrá fallback."
                    }
                    Text(
                        text = descText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Opción 3: IA en la Nube (Gemini Flash)
        val isCloudAi = state.processingMode == SecurePrefs.MODE_CLOUD_AI
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onModeSelected(SecurePrefs.MODE_CLOUD_AI) },
            colors = CardDefaults.cardColors(
                containerColor = if (isCloudAi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            border = CardDefaults.outlinedCardBorder().takeIf { !isCloudAi }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isCloudAi,
                    onClick = { onModeSelected(SecurePrefs.MODE_CLOUD_AI) }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Nube directa: Reglas → IA Nube",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Procesamiento por reglas con fallback directo a IA en la nube, saltando la IA local.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Sección adicional: Si el modo puede usar nube, pedir la API Key de OpenRouter.
        AnimatedVisibility(visible = isParser || isCloudAi) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Clave API de OpenRouter:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = "Se guarda cifrada solo en este dispositivo. No se sincroniza con Supabase ni queda en el backup de tu cuenta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("OpenRouter API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Mostrar/Ocultar"
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onSaveApiKey(apiKeyInput) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar API Key")
                    }
                    AnimatedVisibility(visible = state.isSavedSuccess) {
                        Text(
                            "¡API Key guardada correctamente!",
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomationSettingsDialog(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onModeSelected: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onLocalAiToggle: (Boolean) -> Unit,
    onOpenCustomRules: () -> Unit,
    apiKeyInputInitial: String
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Automatización e IA",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gestiona la lectura de tus transacciones",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Selector de modo de procesamiento
                    item {
                        Text(
                            text = "Modo de Procesamiento de Mensajes:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        NotificationModeSelector(
                            state = state,
                            onModeSelected = onModeSelected,
                            onSaveApiKey = onSaveApiKey,
                            onLocalAiToggle = onLocalAiToggle,
                            apiKeyInputInitial = apiKeyInputInitial
                        )
                    }

                    // 2. Acceso a Reglas Personalizadas
                    item {
                        Text(
                            text = "Reglas Personalizadas:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss() // Cierra este diálogo primero
                                    onOpenCustomRules() // Abre el diálogo de reglas
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Entrenador de Reglas de Parseo",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        val count = state.customRules.size
                                        Text(
                                            text = if (count == 0) "Entrena patrones para nuevos bancos" else "$count reglas personalizadas creadas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                            }
                        }
                    }

                    // 3. Garantía de Privacidad y Seguridad
                    item {
                        PrivacyAndSecurityCard()
                    }

                    // 4. Guía de optimización de sistema
                    item {
                        DeviceOptimizationGuideCard()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Listo")
                }
            }
        }
    }
}

@Composable
private fun AccountItemRow(
    account: AccountEntity,
    displayValue: String,
    valueColor: androidx.compose.ui.graphics.Color,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = when (account.type) {
                    AccountType.TARJETA_CREDITO -> Icons.Default.CreditCard
                    AccountType.NEQUI -> Icons.Default.Smartphone
                    AccountType.DAVIPLATA -> Icons.Default.AccountBalanceWallet
                    AccountType.EFECTIVO -> Icons.Default.Payments
                    AccountType.INVERSION -> Icons.AutoMirrored.Filled.ShowChart
                    else -> Icons.Default.AccountBalance
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(account.type.displayName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        account.lastFourDigits?.takeIf { it.isNotBlank() }?.let { digits ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "*$digits",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayValue,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = valueColor,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Corrección auditada", tint = Color.Gray)
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar cuenta", tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun PrivacyAndSecurityCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Privacidad y seguridad",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "FinanzApp procesa notificaciones bancarias sensibles. Estas son las reglas reales del sistema:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Procesamiento local por defecto",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Las reglas y parsers se ejecutan en el celular. Si activas backup, tus datos se sincronizan con tu cuenta en la nube; no se promete que todo quede solo local.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Filtro estricto de aplicaciones",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "El listener debe evaluar solo paquetes bancarios autorizados. Los mensajes que no cumplen reglas quedan ignorados o fallidos en el ledger para auditoría.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(modifier = Modifier.padding(bottom = 8.dp)) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "IA opcional y explícita",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "La IA local se ejecuta en el dispositivo si está disponible. La IA en nube solo se usa si eliges ese modo y guardas tu API key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        Icon(
                            Icons.Default.EnhancedEncryption,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Base local cifrada",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "La base local usa SQLCipher. El bloqueo local protege la app en uso, pero el respaldo en nube depende de la sesión y reglas de Supabase.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceOptimizationGuideCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Optimización del sistema",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    Text(
                        "Para que FinanzApp lea notificaciones de forma confiable, evita que el sistema suspenda la app en segundo plano:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Samsung One UI:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "1. Ve a Ajustes > Aplicaciones > FinanzApp > Batería y selecciona 'Sin restricciones'.\n" +
                                "2. En Cuidado del dispositivo > Batería, agrega FinanzApp a las apps que nunca se suspenden.\n" +
                                "3. Verifica que las notificaciones de bancos estén habilitadas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                    )

                    Text(
                        "Xiaomi MIUI / HyperOS:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "1. Ve a Ajustes > Aplicaciones > Administrar aplicaciones > FinanzApp.\n" +
                                "2. Activa la opción 'Inicio Automático'.\n" +
                                "3. En 'Ahorro de batería', selecciona 'Sin Restricciones'.\n" +
                                "4. Abre la app, ve a la vista de apps recientes, mantén presionado FinanzApp y pulsa el ícono de candado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        type: AccountType,
        initialBalance: Double,
        creditLimit: Double,
        cutoffDay: Int,
        paymentDueDay: Int,
        interestRateEA: Double?,
        lastFourDigits: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.AHORROS) }
    var initialBalance by remember { mutableStateOf("") }
    var lastFourDigits by remember { mutableStateOf("") }

    // Campos de Tarjeta de Crédito
    var creditLimit by remember { mutableStateOf("") }
    var cutoffDay by remember { mutableStateOf("15") }
    var paymentDueDay by remember { mutableStateOf("30") }
    var interestRateEA by remember { mutableStateOf("") }

    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Cuenta") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre") },
                        placeholder = { Text("Ej: Ahorros, Efectivo, Bolsa") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = lastFourDigits,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                lastFourDigits = input
                            }
                        },
                        label = { Text("Últimos 4 dígitos (opcional)") },
                        placeholder = { Text("Ej: 5039") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                item {
                    Text("Tipo de Cuenta", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = type.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { typeExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { typeExpanded = true }
                        )

                        DropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            val groupedTypes = AccountType.entries.groupBy { it.category }
                            groupedTypes.forEach { (category, types) ->
                                Text(
                                    text = category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                types.forEach { t ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val tIcon = when (t) {
                                                    AccountType.AHORROS -> Icons.Default.AccountBalance
                                                    AccountType.TARJETA_CREDITO -> Icons.Default.CreditCard
                                                    AccountType.NEQUI -> Icons.Default.Smartphone
                                                    AccountType.DAVIPLATA -> Icons.Default.AccountBalanceWallet
                                                    AccountType.EFECTIVO -> Icons.Default.Payments
                                                    AccountType.INVERSION -> Icons.AutoMirrored.Filled.ShowChart
                                                    else -> Icons.Default.Wallet
                                                }
                                                Icon(
                                                    tIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(t.displayName)
                                            }
                                        },
                                        onClick = {
                                            type = t
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (type != AccountType.TARJETA_CREDITO) {
                    item {
                        OutlinedTextField(
                            value = initialBalance,
                            onValueChange = { initialBalance = it },
                            label = { Text("Saldo Actual ($)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                // Mostrar campos adicionales si es Tarjeta de Crédito
                if (type == AccountType.TARJETA_CREDITO) {
                    item {
                        OutlinedTextField(
                            value = creditLimit,
                            onValueChange = { creditLimit = it },
                            label = { Text("Cupo Limite de Crédito ($)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = cutoffDay,
                            onValueChange = { cutoffDay = it },
                            label = { Text("Día de Corte (1-31)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = paymentDueDay,
                            onValueChange = { paymentDueDay = it },
                            label = { Text("Día Límite de Pago (1-31)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = interestRateEA,
                            onValueChange = { interestRateEA = it },
                            label = { Text("Tasa de Interés EA (%) - Opcional") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isBalanceValid = if (type == AccountType.TARJETA_CREDITO) true else {
                initialBalance.isBlank() || initialBalance.toDoubleOrNull() != null
            }
            val isLimitValid = if (type == AccountType.TARJETA_CREDITO) {
                (creditLimit.toDoubleOrNull() ?: 0.0) > 0.0
            } else true

            Button(
                onClick = {
                    // Para tarjetas de crédito, la deuda siempre inicia en $0.
                    // La deuda se construye desde las Compras Diferidas dentro de cada tarjeta.
                    val balance = if (type == AccountType.TARJETA_CREDITO) 0.0
                                  else (initialBalance.toDoubleOrNull() ?: 0.0)
                    val limit = creditLimit.toDoubleOrNull() ?: 0.0
                    val cutoff = cutoffDay.toIntOrNull() ?: 15
                    val due = paymentDueDay.toIntOrNull() ?: 30
                    val ea = interestRateEA.toDoubleOrNull()

                    if (name.isNotBlank()) {
                        val digits = lastFourDigits.trim().takeIf { it.isNotBlank() }
                        onConfirm(name, type, balance, limit, cutoff, due, ea, digits)
                    }
                },
                enabled = name.isNotBlank() && isBalanceValid && isLimitValid
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
