package com.ivan.finanzapp.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAuth: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sección: Cuentas y Activos Financieros (¡AL PRINCIPIO!)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Cuentas y Activos Financieros")
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
                    Text(
                        "No has configurado cuentas ni instrumentos financieros todavía. Las transacciones de tus notificaciones " +
                                "aparecerán sin asignar hasta que las asocies.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                items(state.accounts, key = { it.id }) { account ->
                    AccountItemRow(
                        account = account,
                        onDeleteClick = { accountToDelete = account }
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Sección: Copia de Seguridad y Sincronización en la Nube
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                                    text = "Copia de Seguridad (Supabase)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (state.currentUserEmail != null)
                                        "Conectado: ${state.currentUserEmail}"
                                    else "Sincronización inactiva. Los datos están 100% locales.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (state.currentUserEmail != null) {
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
                                    Text("Sincronizar", fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = { viewModel.signOut() }
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
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
                                Text("Conectar con Supabase", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Sección: Automatización de Notificaciones (¡AL FINAL!)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleProcessingDialog(true) },
                    shape = RoundedCornerShape(16.dp),
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
                                    text = "Automatización e IA (Notificaciones)",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Configura el procesamiento por IA, reglas personalizadas y privacidad.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
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
                        "¿Estás seguro de que deseas eliminar la cuenta \"${account.name}\"? " +
                                "Esto desvinculará sus transacciones y eliminará de forma permanente sus tarjetas de crédito asociadas."
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
        // Opción 1: Reglas Automáticas Locales
        val isParser = state.processingMode == SecurePrefs.MODE_PARSER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onModeSelected(SecurePrefs.MODE_PARSER) },
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
                    onClick = { onModeSelected(SecurePrefs.MODE_PARSER) }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Reglas Locales (con Asistencia de IA)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Procesamiento por reglas. Si fallan, recurre a IA Local (Gemini Nano) y, de ser necesario, a IA en la Nube.",
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
                            text = "Solo Reglas Locales e IA Local",
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
                        "Procesamiento por reglas con fallback únicamente a IA Local (Gemini Nano) para total privacidad offline."
                    } else {
                        "IA local requiere un dispositivo premium con NPU integrada (ej. Galaxy S26 Ultra / Pixel 8+)."
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
                        text = "Solo Reglas Locales e IA en la Nube",
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

        // Sección adicional: Si se selecciona IA en la Nube, pedir la API Key de OpenRouter
        AnimatedVisibility(visible = isCloudAi) {
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
                                Icon(Icons.Default.KeyboardArrowRight, null)
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
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (account.type) {
                    AccountType.TARJETA_CREDITO -> Icons.Default.CreditCard
                    AccountType.NEQUI -> Icons.Default.Smartphone
                    AccountType.DAVIPLATA -> Icons.Default.AccountBalanceWallet
                    AccountType.EFECTIVO -> Icons.Default.Payments
                    AccountType.INVERSION -> Icons.Default.ShowChart
                    else -> Icons.Default.AccountBalance
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(account.type.displayName, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatCOP(account.currentBalance),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
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
        shape = RoundedCornerShape(16.dp),
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
                        "Garantía de Privacidad y Seguridad",
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
                        text = "Tu privacidad es nuestra prioridad absoluta. A diferencia de otras aplicaciones, FinanzApp opera bajo las siguientes estrictas reglas de seguridad:",
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
                                "Procesamiento 100% Offline y Local",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Tus notificaciones y mensajes se analizan en tiempo real dentro de tu propio celular. No tenemos servidores centrales, bases de datos en la nube ni guardamos tus textos fuera de tu dispositivo.",
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
                                "Filtro Estricto de Aplicaciones (Whitelisting)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "La app ignora de inmediato chats de WhatsApp, Telegram, llamadas, correos, fotos u otras aplicaciones personales. El listener solo intercepta y evalúa paquetes de apps bancarias autorizadas (ej. Nequi, Davivienda, Bancolombia, etc.).",
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
                                "Sin Intermediarios con Inteligencia Artificial",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Si activas IA local (Gemini Nano), se ejecuta offline. Si utilizas IA en la nube, la comunicación se realiza directamente desde tu celular hacia la API oficial usando tu API Key personal, de forma 100% anónima y libre de identificadores personales o de cuenta. La información compartida es exclusivamente para la clasificación del mensaje.",
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
                                "Base de Datos Encriptada (SQLCipher)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Todos tus movimientos financieros se almacenan localmente utilizando encriptación SQLCipher de grado militar. Tus datos están completamente blindados contra otras aplicaciones y accesos no autorizados en tu celular.",
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
        shape = RoundedCornerShape(16.dp),
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
                        "Guía de Optimización en Redmi y Samsung",
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
                        "Para que FinanzApp escuche las notificaciones de compras/NFC y SMS de forma confiable, " +
                                "debes configurar los siguientes ajustes en tus celulares actuales:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "📱 Samsung Galaxy S26 Ultra (One UI):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "1. Ve a Ajustes → Aplicaciones → FinanzApp → Batería → Selecciona 'Sin Restricciones'.\n" +
                                "2. En Cuidado del dispositivo → Batería → Límites de uso en segundo plano → Apps que nunca se suspenden → Agrega FinanzApp.\n" +
                                "3. Abre la pantalla de apps recientes, mantén pulsado el ícono de la app y selecciona 'Mantener abierta' para evitar que el sistema la cierre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                    )

                    Text(
                        "📱 Xiaomi Redmi 10s (MIUI / HyperOS):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "1. Ve a Ajustes → Aplicaciones → Administrar aplicaciones → FinanzApp.\n" +
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
                                                    AccountType.INVERSION -> Icons.Default.ShowChart
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
