package com.ivan.finanzapp.ui.creditcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.components.formatPercentage
import com.ivan.finanzapp.ui.dashboard.CreditCardSummary
import com.ivan.finanzapp.ui.theme.TrafficGreen
import com.ivan.finanzapp.ui.theme.TrafficRed
import com.ivan.finanzapp.ui.theme.TrafficYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardsScreen(
    viewModel: CreditCardsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedForPayment by remember { mutableStateOf<CreditCardSummary?>(null) }

    Scaffold { innerPadding ->
        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    SectionTitle("Tarjetas de Crédito")
                }

                if (state.creditCards.isEmpty()) {
                    item {
                        EmptyCardsCard()
                    }
                } else {
                    items(state.creditCards, key = { it.card.id }) { cardSummary ->
                        PhysicalLikeCreditCard(
                            summary = cardSummary,
                            onPayClick = { selectedForPayment = cardSummary }
                        )
                    }
                }
            }
        }

        // Diálogo para Registrar Abono
        selectedForPayment?.let { summary ->
            PayCardDialog(
                summary = summary,
                accounts = state.accounts,
                onDismiss = { selectedForPayment = null },
                onConfirm = { amount, fundingAccountId ->
                    viewModel.payCreditCard(summary.card, amount, fundingAccountId)
                    selectedForPayment = null
                }
            )
        }
    }
}

@Composable
private fun PhysicalLikeCreditCard(
    summary: CreditCardSummary,
    onPayClick: () -> Unit
) {
    val gradientColors = when (summary.usageLevel) {
        "LOW" -> listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
        "MEDIUM" -> listOf(Color(0xFF373B44), Color(0xFF4286f4))
        else -> listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))
    }

    val barColor = when (summary.usageLevel) {
        "LOW" -> TrafficGreen
        "MEDIUM" -> TrafficYellow
        else -> TrafficRed
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(colors = gradientColors))
                .padding(20.dp)
        ) {
            // Fila de arriba: Nombre de banco y chip físico ficticio
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    summary.account.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Icon(
                    Icons.Default.CreditCard,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Número ficticio
            Text(
                "••••  ••••  ••••  ${summary.card.id.takeLast(4).ifBlank { "8888" }}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 20.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(20.dp))

            // Información de saldos
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "DEUDA ACTUAL",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatCOP(summary.card.currentDebt),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "CUPO DISPONIBLE",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatCOP(summary.availableCredit),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Progreso del cupo
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Uso: ${formatPercentage(summary.usagePercentage)}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
                Text(
                    "Cupo Total: ${formatCOP(summary.card.creditLimit)}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (summary.usagePercentage / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = barColor,
                trackColor = Color.White.copy(alpha = 0.2f)
            )

            Spacer(Modifier.height(16.dp))

            // Fechas y Pago mínimo
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Pago mínimo", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                    Text(formatCOP(summary.minimumPayment), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Corte", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                    Text("Día ${summary.card.cutoffDay}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Vence en", color = Color.White.copy(alpha = 0.6f), fontSize = 9.sp)
                    val daysText = when {
                        summary.daysUntilDue == 0 -> "¡Hoy!"
                        summary.daysUntilDue < 0 -> "Vencida"
                        else -> "${summary.daysUntilDue} días"
                    }
                    Text(daysText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (summary.card.currentDebt > 0.0) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onPayClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Registrar Abono / Pago", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayCardDialog(
    summary: CreditCardSummary,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, fundingAccountId: String?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Abono a Tarjeta") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Vas a registrar un pago para la tarjeta \"${summary.account.name}\". " +
                            "La deuda actual es ${formatCOP(summary.card.currentDebt)}.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto del Abono ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Cuenta de fondos para debitar (Opcional)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: "Ninguna cuenta (Abono manual externo)",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { dropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ninguna cuenta") },
                            onClick = {
                                selectedAccount = null
                                dropdownExpanded = false
                            }
                        )
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.name} (Saldo: ${formatCOP(account.currentBalance)})") },
                                onClick = {
                                    selectedAccount = account
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onConfirm(amt, selectedAccount?.id)
                    }
                },
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0.0
            ) {
                Text("Confirmar Pago")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun EmptyCardsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CreditCard,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No tienes tarjetas de crédito",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ve a Ajustes → Agregar Cuenta Bancaria y crea una cuenta seleccionando el tipo 'TARJETA_CREDITO' " +
                    "para que empiece a mostrarse aquí con su cupo, corte y control de vencimiento.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
