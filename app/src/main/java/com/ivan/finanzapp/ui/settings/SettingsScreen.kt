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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    // Rellenar la API key cuando cargue
    LaunchedEffect(state.apiKey) {
        apiKeyInput = state.apiKey
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
            // Sección: API Key de OpenRouter
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        SectionTitle("AI Fallback (OpenRouter API Key)")
                        Text(
                            "Ingresa tu clave API de OpenRouter para activar la extracción automática " +
                                    "con inteligencia artificial (Gemini Flash) cuando los mensajes o notificaciones no coincidan " +
                                    "con las reglas locales. Se almacena localmente de forma cifrada.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 12.dp)
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

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.saveApiKey(apiKeyInput) },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Guardar API Key")
                        }

                        AnimatedVisibility(visible = state.isSavedSuccess) {
                            Text(
                                "¡API Key guardada correctamente!",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    "Procesamiento local con IA",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val description = if (state.isLocalAiSupported) {
                                    "Compatible con tu dispositivo. Usa Gemini Nano local para clasificar transacciones sin enviar datos a la nube."
                                } else {
                                    "IA en dispositivo no compatible. Requiere procesador con NPU premium (ej. Galaxy S26 Ultra). Se usará IA en la nube."
                                }
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = state.isLocalAiEnabled,
                                onCheckedChange = { viewModel.setLocalAiEnabled(it) },
                                enabled = state.isLocalAiSupported
                            )
                        }
                    }
                }
            }

            // Sección: Cuentas configuradas
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Mis Cuentas Bancarias")
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
                        "No has agregado cuentas bancarias todavía. Las transacciones de tus notificaciones " +
                                "aparecerán sin asignar hasta que las asocies.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                items(state.accounts, key = { it.id }) { account ->
                    AccountItemRow(
                        account = account,
                        onDeleteClick = { viewModel.deleteAccount(account.id) }
                    )
                }
            }

            // Sección: Optimización para Redmi 10s y S26 Ultra (Dispositivos del usuario)
            item {
                Spacer(Modifier.height(8.dp))
                DeviceOptimizationGuideCard()
            }
        }

        // Diálogo para Agregar Cuenta
        if (state.isAddAccountDialogVisible) {
            AddAccountDialog(
                onDismiss = { viewModel.toggleAddAccountDialog(false) },
                onConfirm = { name, type, balance, limit, cutoff, due, interest ->
                    viewModel.addAccount(name, type, balance, limit, cutoff, due, interest)
                }
            )
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
                    else -> Icons.Default.AccountBalance
                }
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(account.type.name, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
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
        interestRateEA: Double?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.AHORROS) }
    var initialBalance by remember { mutableStateOf("") }

    // Campos de Tarjeta de Crédito
    var creditLimit by remember { mutableStateOf("") }
    var cutoffDay by remember { mutableStateOf("15") }
    var paymentDueDay by remember { mutableStateOf("30") }
    var interestRateEA by remember { mutableStateOf("") }

    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Cuenta Bancaria") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre de la Cuenta") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    Text("Tipo de Cuenta", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = type.name,
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
                            AccountType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.name) },
                                    onClick = {
                                        type = t
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    val label = if (type == AccountType.TARJETA_CREDITO) "Deuda Actual ($)" else "Saldo Actual ($)"
                    OutlinedTextField(
                        value = initialBalance,
                        onValueChange = { initialBalance = it },
                        label = { Text(label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
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
            Button(
                onClick = {
                    val balance = initialBalance.toDoubleOrNull() ?: 0.0
                    val limit = creditLimit.toDoubleOrNull() ?: 0.0
                    val cutoff = cutoffDay.toIntOrNull() ?: 15
                    val due = paymentDueDay.toIntOrNull() ?: 30
                    val ea = interestRateEA.toDoubleOrNull()

                    if (name.isNotBlank()) {
                        onConfirm(name, type, balance, limit, cutoff, due, ea)
                    }
                },
                enabled = name.isNotBlank() && initialBalance.toDoubleOrNull() != null
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
