package com.ivan.finanzapp.ui.quickadd

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.AppearancePrefs
import com.ivan.finanzapp.data.security.SecureLog
import com.ivan.finanzapp.domain.model.AccountType
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.theme.FinanzAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {
    @Inject
    lateinit var appearancePrefs: AppearancePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val action = intent.action
        val data = intent.data
        var initialAmount = ""
        var initialMerchant = ""
        var isVoiceAction = false
        
        if (Intent.ACTION_VIEW == action && data != null) {
            initialAmount = data.getQueryParameter("amount") ?: ""
            initialMerchant = data.getQueryParameter("merchant") ?: ""
            // External deep links may prefill the form, but financial writes
            // must still be confirmed by the user inside the app.
            isVoiceAction = false
        }
        
        setContent {
            val useDynamicColor by appearancePrefs.useDynamicColor.collectAsState(initial = false)
            FinanzAppTheme(dynamicColor = useDynamicColor) {
                QuickAddScreen(
                    initialAmount = initialAmount,
                    initialMerchant = initialMerchant,
                    isVoiceAction = isVoiceAction,
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    initialAmount: String = "",
    initialMerchant: String = "",
    isVoiceAction: Boolean = false,
    onDismiss: () -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var amountStr by remember { mutableStateOf(initialAmount) }
    var merchant by remember { mutableStateOf(initialMerchant) }
    var selectedType by remember { mutableStateOf("Gasto") } // "Gasto" o "Ingreso"
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // Estado del guardado por voz: "none", "saving", "success"
    var voiceSaveState by remember { mutableStateOf(if (isVoiceAction) "saving" else "none") }

    // Pre-seleccionar cuenta por defecto cuando se carguen
    LaunchedEffect(accounts) {
        if (selectedAccountId == null && accounts.isNotEmpty()) {
            selectedAccountId = accounts.preferredManualDefaultAccountId()
        }
    }

    // Pre-seleccionar categoría por defecto cuando se carguen
    LaunchedEffect(categories) {
        if (selectedCategoryId == null && categories.isNotEmpty()) {
            selectedCategoryId = categories.find { it.isDefault }?.id ?: categories.firstOrNull()?.id
        }
    }

    // Lógica para auto-guardar cuando viene de comandos de voz (Assistant/Gemini)
    if (isVoiceAction && voiceSaveState == "saving") {
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val isDataReady = amount > 0.0 && merchant.isNotBlank()
        
        LaunchedEffect(amount, merchant, accounts, categories, isDataReady) {
            SecureLog.d(
                "QuickAddScreen",
                "Voice transaction readiness: hasAmount=${amount > 0.0}, hasMerchant=${merchant.isNotBlank()}, accounts.size=${accounts.size}, isDataReady=$isDataReady"
            )
            if (isDataReady) {
                // Si las cuentas están vacías al inicio, damos un margen de 100ms para que Room cargue el flujo.
                // Si tras 100ms sigue vacío, significa que el usuario no tiene cuentas creadas y guardamos sin cuenta (Pendiente).
                if (accounts.isEmpty()) {
                    kotlinx.coroutines.delay(100)
                }
                
                val defaultAccountId = accounts.preferredManualDefaultAccountId()
                val defaultCategoryId = categories.find { it.isDefault }?.id ?: categories.firstOrNull()?.id
                SecureLog.d(
                    "QuickAddScreen",
                    "Saving voice transaction with defaultAccount=${defaultAccountId != null}, defaultCategory=${defaultCategoryId != null}"
                )
                
                viewModel.saveTransaction(
                    amount = amount,
                    merchant = merchant,
                    typeStr = "Gasto",
                    accountId = defaultAccountId,
                    categoryId = defaultCategoryId,
                    onSuccess = {
                        voiceSaveState = "success"
                    }
                )
            } else if (amountStr.isNotBlank() && amount <= 0.0) {
                // Error al parsear el número: volvemos al formulario para corrección manual
                voiceSaveState = "none"
            }
        }
    }

    // Temporizador de 2 segundos para cerrar el diálogo tras registrar por voz con éxito
    if (voiceSaveState == "success") {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClick = onDismiss,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clickable(
                    enabled = false,
                    onClick = {}
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (voiceSaveState == "saving" || voiceSaveState == "success") {
                // UI Translucida de Confirmación de 2 segundos para comandos de voz
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Registro por Voz",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (voiceSaveState == "saving") {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Registrando movimiento...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Éxito",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Text(
                            text = "¡Gasto Registrado!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        
                        val formattedVal = amountStr.toDoubleOrNull()?.let { formatCOP(it) } ?: ""
                        Text(
                            text = "$formattedVal en $merchant",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val accountName = accounts.firstOrNull()?.name ?: "Cuenta por Defecto"
                        Text(
                            text = "Cuenta: $accountName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Formulario manual estándar
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cabecera: Título + Botón Cerrar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Registrar Movimiento",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Selector de Tipo (Gasto / Ingreso)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Gasto", "Ingreso").forEach { type ->
                            val isSelected = selectedType == type
                            Button(
                                onClick = { selectedType = type },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(type, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Campo de Monto ($)
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it.filter { char -> char.isDigit() } },
                        label = { Text("Monto ($)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Campo de Comercio
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = { Text("Comercio / Descripción") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Selector de Cuenta
                    val currentAccountName = selectedAccountId?.let { id -> accounts.find { it.id == id }?.name } ?: "Sin asignar (Pendiente)"
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Cuenta",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { accountDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(currentAccountName)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = accountDropdownExpanded,
                                onDismissRequest = { accountDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sin asignar (Pendiente)") },
                                    onClick = {
                                        selectedAccountId = null
                                        accountDropdownExpanded = false
                                    }
                                )
                                accounts.forEach { account ->
                                    DropdownMenuItem(
                                        text = { Text(account.name) },
                                        onClick = {
                                            selectedAccountId = account.id
                                            accountDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Selector de Categoría
                    val currentCategoryName = selectedCategoryId?.let { id -> categories.find { it.id == id }?.name } ?: "Sin categoría"
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Categoría",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { categoryDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(currentCategoryName)
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sin categoría") },
                                    onClick = {
                                        selectedCategoryId = null
                                        categoryDropdownExpanded = false
                                    }
                                )
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            selectedCategoryId = category.id
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Botón Guardar
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    val isEnabled = amount > 0.0 && merchant.isNotBlank()

                    Button(
                        onClick = {
                            if (isEnabled) {
                                viewModel.saveTransaction(
                                    amount = amount,
                                    merchant = merchant,
                                    typeStr = selectedType,
                                    accountId = selectedAccountId,
                                    categoryId = selectedCategoryId,
                                    onSuccess = onDismiss
                                )
                            }
                        },
                        enabled = isEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Guardar Transacción",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

private fun List<AccountEntity>.preferredManualDefaultAccountId(): String? =
    firstOrNull { it.type != AccountType.TARJETA_CREDITO }?.id ?: firstOrNull()?.id
