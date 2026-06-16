package com.ivan.finanzapp.ui.loan

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.theme.TrafficGreen
import com.ivan.finanzapp.ui.theme.TrafficRed
import com.ivan.finanzapp.ui.theme.TrafficYellow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    viewModel: LoansViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedLoanForPayment by remember { mutableStateOf<LoanEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleAddDialog(true) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar crédito")
            }
        }
    ) { innerPadding ->
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
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // Tarjeta de Deuda Total con Gradiente Premium
                item {
                    TotalDebtCard(totalDebt = state.totalDebt)
                }

                item {
                    SectionTitle(
                        text = "Tus créditos y préstamos activos",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Lista de Créditos
                if (state.loans.isEmpty()) {
                    item {
                        EmptyLoansCard()
                    }
                } else {
                    items(state.loans, key = { it.id }) { loan ->
                        LoanCard(
                            loan = loan,
                            onPayClick = { selectedLoanForPayment = loan },
                            onDeleteClick = { viewModel.deleteLoan(loan.id) }
                        )
                    }
                }
            }
        }

        // Diálogo para Agregar Crédito
        if (state.isAddDialogVisible) {
            AddLoanDialog(
                accounts = state.accounts,
                onDismiss = { viewModel.toggleAddDialog(false) },
                onConfirm = { name, total, interest, installments, cuota, day, accountId ->
                    viewModel.addLoan(name, total, interest, installments, cuota, day, accountId)
                }
            )
        }

        // Diálogo de Confirmación de Pago
        selectedLoanForPayment?.let { loan ->
            AlertDialog(
                onDismissRequest = { selectedLoanForPayment = null },
                title = { Text("Confirmar Pago de Cuota") },
                text = {
                    Text(
                        "¿Confirmas el pago de la cuota de ${formatCOP(loan.monthlyInstallmentAmount)} " +
                                "para el crédito \"${loan.name}\"? Esto registrará un movimiento de gasto y " +
                                "reducirá la deuda restante." +
                                if (loan.linkedAccountId != null) "\n\nSe debitará automáticamente del saldo de la cuenta vinculada." else ""
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.payInstallment(loan)
                            selectedLoanForPayment = null
                        }
                    ) {
                        Text("Confirmar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedLoanForPayment = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun TotalDebtCard(totalDebt: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF3F2B96), Color(0xFFA8C0FF))
                ),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                "Deuda total en créditos",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                formatCOP(totalDebt),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 42.sp
            )
        }
    }
}

@Composable
private fun LoanCard(
    loan: LoanEntity,
    onPayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val today = LocalDate.now()
    val dueDate = Instant.ofEpochMilli(loan.nextPaymentDate).atZone(ZoneId.systemDefault()).toLocalDate()
    val daysRemaining = (dueDate.toEpochDay() - today.toEpochDay()).toInt()

    val dateColor = when {
        daysRemaining < 0 -> TrafficRed
        daysRemaining <= 3 -> TrafficRed
        daysRemaining <= 7 -> TrafficYellow
        else -> MaterialTheme.colorScheme.onSurface
    }

    val dateText = when {
        daysRemaining < 0 -> "Vencido hace ${-daysRemaining} días"
        daysRemaining == 0 -> "Vence ¡Hoy!"
        daysRemaining == 1 -> "Vence mañana"
        else -> "Vence el ${dueDate.format(DateTimeFormatter.ofPattern("dd MMM"))} (en $daysRemaining días)"
    }

    val installmentsProgress = if (loan.totalInstallments > 0) {
        loan.paidInstallments.toFloat() / loan.totalInstallments.toFloat()
    } else 0f

    val debtProgress = if (loan.totalAmount > 0) {
        (loan.totalAmount - loan.remainingAmount).toFloat() / loan.totalAmount.toFloat()
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Fila de título y eliminar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        loan.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar crédito", tint = Color.Gray)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Progreso de las cuotas
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Cuotas pagadas: ${loan.paidInstallments} de ${loan.totalInstallments}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${(installmentsProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { installmentsProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(12.dp))

            // Progreso del monto abonado
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Deuda pendiente: ${formatCOP(loan.remainingAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TrafficRed
                )
                Text(
                    "Total: ${formatCOP(loan.totalAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { debtProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = TrafficGreen,
                trackColor = TrafficRed.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(16.dp))

            // Detalles financieros del crédito
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Cuota Mensual", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        formatCOP(loan.monthlyInstallmentAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tasa Interés", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        "${loan.monthlyInterestRate}% mes",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Día de Pago", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        "Día ${loan.paymentDay}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Próximo vencimiento
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Próxima cuota", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        dateText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = dateColor
                    )
                }

                if (loan.remainingAmount > 0) {
                    Button(
                        onClick = onPayClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Registrar Pago", fontSize = 13.sp)
                    }
                } else {
                    Text(
                        "¡Pagado!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TrafficGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        totalAmount: Double,
        interestRate: Double,
        totalInstallments: Int,
        monthlyInstallment: Double,
        paymentDay: Int,
        linkedAccountId: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var totalInstallments by remember { mutableStateOf("") }
    var monthlyInstallment by remember { mutableStateOf("") }
    var paymentDay by remember { mutableStateOf("") }

    var expanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Crédito / Préstamo") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del Crédito") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = totalAmount,
                        onValueChange = { totalAmount = it },
                        label = { Text("Monto Total Prestado ($)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                item {
                    OutlinedTextField(
                        value = interestRate,
                        onValueChange = { interestRate = it },
                        label = { Text("Tasa de Interés Mensual (%)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                item {
                    OutlinedTextField(
                        value = totalInstallments,
                        onValueChange = { totalInstallments = it },
                        label = { Text("Total de Cuotas (Meses)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                item {
                    OutlinedTextField(
                        value = monthlyInstallment,
                        onValueChange = { monthlyInstallment = it },
                        label = { Text("Valor Cuota Mensual ($)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                item {
                    OutlinedTextField(
                        value = paymentDay,
                        onValueChange = { paymentDay = it },
                        label = { Text("Día de Pago (1-31)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Cuenta para débito automático (Opcional)", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "Ninguna cuenta seleccionada",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                        )

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ninguna cuenta") },
                                onClick = {
                                    selectedAccount = null
                                    expanded = false
                                }
                            )
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text("${account.name} (Saldo: ${formatCOP(account.currentBalance)})") },
                                    onClick = {
                                        selectedAccount = account
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val total = totalAmount.toDoubleOrNull() ?: 0.0
                    val interest = interestRate.toDoubleOrNull() ?: 0.0
                    val installments = totalInstallments.toIntOrNull() ?: 1
                    val cuota = monthlyInstallment.toDoubleOrNull() ?: 0.0
                    val day = paymentDay.toIntOrNull()?.coerceIn(1, 31) ?: 1

                    if (name.isNotBlank() && total > 0.0 && cuota > 0.0) {
                        onConfirm(name, total, interest, installments, cuota, day, selectedAccount?.id)
                    }
                },
                enabled = name.isNotBlank() &&
                        totalAmount.toDoubleOrNull() != null &&
                        monthlyInstallment.toDoubleOrNull() != null
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

@Composable
private fun EmptyLoansCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.MonetizationOn,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No tienes créditos registrados",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Usa el botón de abajo para registrar tu primer crédito de consumo, préstamo personal o hipotecario y llevar un control pro de tus cuotas, intereses y saldos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
