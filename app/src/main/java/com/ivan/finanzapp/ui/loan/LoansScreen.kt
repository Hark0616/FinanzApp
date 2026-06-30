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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.LoanEntity
import com.ivan.finanzapp.data.local.entity.LoanPaymentEntity
import com.ivan.finanzapp.domain.model.LoanAmortizationType
import com.ivan.finanzapp.domain.model.LoanInterestRateType
import com.ivan.finanzapp.ui.components.MoneyInputField
import com.ivan.finanzapp.ui.components.OverflowActionMenu
import com.ivan.finanzapp.ui.components.OverflowMenuAction
import com.ivan.finanzapp.ui.components.ProgressiveFormSheet
import com.ivan.finanzapp.ui.components.formatEditableAmount
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.components.parseMoneyInput
import com.ivan.finanzapp.ui.theme.TrafficGreen
import com.ivan.finanzapp.ui.theme.TrafficRed
import com.ivan.finanzapp.ui.theme.TrafficYellow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    viewModel: LoansViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedLoanForPayment by remember { mutableStateOf<LoanEntity?>(null) }
    var loanToDelete by remember { mutableStateOf<LoanEntity?>(null) }

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
                    TotalDebtCard(
                        totalDebt = state.totalDebt,
                        totalUnpaidInterest = state.totalUnpaidInterest,
                        totalUnpaidCharges = state.totalUnpaidCharges
                    )
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
                            lastPayment = state.latestPaymentsByLoanId[loan.id],
                            onPayClick = { selectedLoanForPayment = loan },
                            onDeleteClick = { loanToDelete = loan }
                        )
                    }
                }
            }
        }

        if (state.isAddDialogVisible) {
            AddLoanSheet(
                accounts = state.accounts,
                onDismiss = { viewModel.toggleAddDialog(false) },
                onConfirm = { name, total, interest, interestType, amortizationType, installments, cuota, insurance, fee, day, accountId ->
                    viewModel.addLoan(
                        name = name,
                        totalAmount = total,
                        interestRate = interest,
                        interestRateType = interestType,
                        amortizationType = amortizationType,
                        totalInstallments = installments,
                        monthlyInstallment = cuota,
                        monthlyInsurance = insurance,
                        monthlyFee = fee,
                        paymentDay = day,
                        linkedAccountId = accountId
                    )
                }
            )
        }

        selectedLoanForPayment?.let { loan ->
            LoanPaymentSheet(
                loan = loan,
                onDismiss = { selectedLoanForPayment = null },
                onConfirm = { amount ->
                    viewModel.payInstallment(
                        loan = loan,
                        actualPaymentAmount = amount
                    )
                    selectedLoanForPayment = null
                }
            )
        }

        // Diálogo de Confirmación de Eliminación de Crédito
        loanToDelete?.let { loan ->
            AlertDialog(
                onDismissRequest = { loanToDelete = null },
                title = { Text("Eliminar crédito") },
                text = { Text("¿Estás seguro de que deseas eliminar el crédito \"${loan.name}\"? Esta acción no se puede deshacer y borrará todo su historial de pagos.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteLoan(loan.id)
                            loanToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { loanToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun LoanPaymentSheet(
    loan: LoanEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val scheduledPayment = estimatedScheduledPaymentAmount(loan)
    val maximumApplicablePayment = estimatedMaximumApplicablePayment(loan)
    val defaultPayment = scheduledPayment.coerceAtMost(maximumApplicablePayment)
    var paymentAmount by remember(loan.id, defaultPayment) {
        mutableStateOf(formatEditableAmount(defaultPayment))
    }
    val parsedPaymentAmount = parseMoneyInput(paymentAmount)
    val exceedsApplicablePayment = parsedPaymentAmount != null &&
            parsedPaymentAmount > maximumApplicablePayment
    val canConfirmPayment = parsedPaymentAmount != null &&
            parsedPaymentAmount > 0.0 &&
            !exceedsApplicablePayment
    val extraPayment = ((parsedPaymentAmount ?: 0.0) - defaultPayment).coerceAtLeast(0.0)
    val partialPayment = parsedPaymentAmount != null &&
            parsedPaymentAmount > 0.0 &&
            parsedPaymentAmount < defaultPayment

    ProgressiveFormSheet(
        title = "Registrar pago",
        subtitle = loan.name,
        onDismiss = onDismiss,
        primaryActionLabel = "Registrar",
        canSubmit = canConfirmPayment,
        onSubmit = { parsedPaymentAmount?.let(onConfirm) }
    ) {
        Text(
            "Cuota programada: ${formatCOP(scheduledPayment)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (defaultPayment < scheduledPayment) {
            Text(
                "Pago para cerrar: ${formatCOP(defaultPayment)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
        MoneyInputField(
            value = paymentAmount,
            onValueChange = { paymentAmount = it },
            label = "Monto pagado",
            isError = paymentAmount.isNotBlank() && parsedPaymentAmount == null,
            supportingText = if (paymentAmount.isNotBlank() && parsedPaymentAmount == null) "Monto inválido" else null
        )
        if (extraPayment > 0.0) {
            Text(
                "Extra a capital estimado: ${formatCOP(extraPayment)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (exceedsApplicablePayment) {
            Text(
                "Máximo aplicable: ${formatCOP(maximumApplicablePayment)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficRed,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (partialPayment) {
            Text(
                "Pago parcial: puede dejar cargos o interés sin cubrir.",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficYellow,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (loan.linkedAccountId != null) {
            Text(
                "Se debitará de la cuenta vinculada.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TotalDebtCard(
    totalDebt: Double,
    totalUnpaidInterest: Double,
    totalUnpaidCharges: Double
) {
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
                "Capital pendiente en créditos",
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
            if (totalUnpaidInterest > 0.0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Interés no cubierto registrado: ${formatCOP(totalUnpaidInterest)}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (totalUnpaidCharges > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Seguros/cargos no cubiertos: ${formatCOP(totalUnpaidCharges)}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LoanCard(
    loan: LoanEntity,
    lastPayment: LoanPaymentEntity?,
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
    val installmentsText = if (loan.paidInstallments > loan.totalInstallments) {
        "Pagos registrados: ${loan.paidInstallments} (plan original: ${loan.totalInstallments})"
    } else {
        "Cuotas pagadas: ${loan.paidInstallments} de ${loan.totalInstallments}"
    }

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
                OverflowActionMenu(
                    actions = listOf(
                        OverflowMenuAction(
                            label = "Eliminar crédito",
                            icon = Icons.Default.Delete,
                            destructive = true,
                            onClick = onDeleteClick
                        )
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Progreso de las cuotas
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    installmentsText,
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
                    Text(
                        if (loan.amortizationType == LoanAmortizationType.FIXED_PRINCIPAL) {
                            "Próx. cuota est."
                        } else {
                            "Cuota Mensual"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                    Text(
                        formatCOP(estimatedScheduledPaymentAmount(loan)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tasa Interés", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        "${formatRatePercent(loan.interestRateInputValue)} ${loan.interestRateType.shortLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (loan.interestRateType != LoanInterestRateType.MONTHLY_EFFECTIVE) {
                        Text(
                            "Eq. ${formatRatePercent(loan.monthlyInterestRate)}% mes",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
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

            Spacer(Modifier.height(8.dp))
            Text(
                "Método: ${loan.amortizationType.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (loan.amortizationType == LoanAmortizationType.FIXED_PRINCIPAL) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Capital mensual pactado: ${formatCOP(loan.fixedPrincipalAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            val monthlyFixedCharges = loan.monthlyInsuranceAmount + loan.monthlyFeeAmount
            if (monthlyFixedCharges > 0.0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Seguro/cargos en cuota: ${formatCOP(monthlyFixedCharges)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(16.dp))

            lastPayment?.let { payment ->
                LastLoanPaymentSummary(payment = payment)
                Spacer(Modifier.height(16.dp))
            }

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

@Composable
private fun LastLoanPaymentSummary(payment: LoanPaymentEntity) {
    val paymentDate = Instant.ofEpochMilli(payment.paymentDate)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd MMM"))

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Último pago", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Text(paymentDate, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Aplicado: ${formatCOP(payment.actualPaymentAmount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Capital: ${formatCOP(payment.principalAmount)} · Interés: ${formatCOP(payment.interestPaidAmount)}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        if (payment.extraPrincipalAmount > 0.0) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Capital extra: ${formatCOP(payment.extraPrincipalAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficGreen,
                fontWeight = FontWeight.SemiBold
            )
        }
        val fixedChargesPaid = payment.insurancePaidAmount + payment.feePaidAmount
        if (fixedChargesPaid > 0.0) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Seguro/cargos: ${formatCOP(fixedChargesPaid)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        if (payment.unpaidInterestAmount > 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Interés no cubierto: ${formatCOP(payment.unpaidInterestAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficRed,
                fontWeight = FontWeight.SemiBold
            )
        }
        val unpaidFixedCharges = payment.unpaidInsuranceAmount + payment.unpaidFeeAmount
        if (unpaidFixedCharges > 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Seguro/cargos no cubiertos: ${formatCOP(unpaidFixedCharges)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficRed,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (payment.unappliedPaymentAmount > 0.0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Pago no aplicado: ${formatCOP(payment.unappliedPaymentAmount)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrafficRed,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLoanSheet(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        totalAmount: Double,
        interestRate: Double,
        interestRateType: LoanInterestRateType,
        amortizationType: LoanAmortizationType,
        totalInstallments: Int,
        monthlyInstallment: Double,
        monthlyInsurance: Double,
        monthlyFee: Double,
        paymentDay: Int,
        linkedAccountId: String?
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf("") }
    var totalInstallments by remember { mutableStateOf("") }
    var monthlyInstallment by remember { mutableStateOf("") }
    var interestRate by remember { mutableStateOf("") }
    var interestRateType by remember { mutableStateOf(LoanInterestRateType.MONTHLY_EFFECTIVE) }
    var amortizationType by remember { mutableStateOf(LoanAmortizationType.FIXED_INSTALLMENT) }
    var monthlyInsurance by remember { mutableStateOf("") }
    var monthlyFee by remember { mutableStateOf("") }
    var paymentDay by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var rateMenuOpen by remember { mutableStateOf(false) }
    var amortizationMenuOpen by remember { mutableStateOf(false) }
    var accountMenuOpen by remember { mutableStateOf(false) }

    val parsedTotalAmount = parseMoneyInput(totalAmount)
    val parsedInstallments = totalInstallments.toIntOrNull()
    val parsedMonthlyInstallment = parseMoneyInput(monthlyInstallment)
    val parsedMonthlyInsurance = parseMoneyInput(monthlyInsurance) ?: 0.0
    val parsedMonthlyFee = parseMoneyInput(monthlyFee) ?: 0.0
    val parsedInterestRate = interestRate.replace(',', '.').toDoubleOrNull() ?: 0.0
    val parsedPaymentDay = paymentDay.toIntOrNull()
    val calculatedFixedPrincipal = if (
        parsedTotalAmount != null &&
        parsedInstallments != null &&
        parsedInstallments > 0
    ) parsedTotalAmount / parsedInstallments else null
    val normalizedMonthlyRate = normalizeMonthlyInterestRateForPreview(parsedInterestRate, interestRateType)
    val firstMonthInterest = parsedTotalAmount?.let { it * normalizedMonthlyRate / 100.0 }
    val estimatedFirstFixedPrincipalPayment = if (
        amortizationType == LoanAmortizationType.FIXED_PRINCIPAL &&
        calculatedFixedPrincipal != null &&
        parsedTotalAmount != null &&
        firstMonthInterest != null
    ) {
        parsedMonthlyInsurance + parsedMonthlyFee + firstMonthInterest + calculatedFixedPrincipal.coerceAtMost(parsedTotalAmount)
    } else null
    val hasValidAmortizationAmount = when (amortizationType) {
        LoanAmortizationType.FIXED_INSTALLMENT -> parsedMonthlyInstallment != null && parsedMonthlyInstallment > 0.0
        LoanAmortizationType.FIXED_PRINCIPAL -> calculatedFixedPrincipal != null && calculatedFixedPrincipal > 0.0
    }
    val canSave = name.isNotBlank() &&
            parsedTotalAmount != null && parsedTotalAmount > 0.0 &&
            parsedInstallments != null && parsedInstallments > 0 &&
            hasValidAmortizationAmount &&
            parsedPaymentDay != null && parsedPaymentDay in 1..31

    ProgressiveFormSheet(
        title = "Nuevo crédito",
        onDismiss = onDismiss,
        primaryActionLabel = "Guardar",
        canSubmit = canSave,
        onSubmit = {
            val total = parsedTotalAmount
            val installments = parsedInstallments
            val day = parsedPaymentDay
            val cuota = when (amortizationType) {
                LoanAmortizationType.FIXED_INSTALLMENT -> parsedMonthlyInstallment
                LoanAmortizationType.FIXED_PRINCIPAL -> estimatedFirstFixedPrincipalPayment
            }
            if (total != null && installments != null && day != null && cuota != null) {
                onConfirm(
                    name.trim(),
                    total,
                    parsedInterestRate,
                    interestRateType,
                    amortizationType,
                    installments,
                    cuota,
                    parsedMonthlyInsurance,
                    parsedMonthlyFee,
                    day,
                    selectedAccount?.id
                )
            }
        },
        advancedLabel = "Avanzado",
        advancedContent = {
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { rateMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("${interestRateType.displayName} (${interestRateType.shortLabel})", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = rateMenuOpen, onDismissRequest = { rateMenuOpen = false }) {
                    LoanInterestRateType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text("${type.displayName} (${type.shortLabel})") },
                            onClick = {
                                interestRateType = type
                                rateMenuOpen = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = interestRate,
                onValueChange = { interestRate = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                label = { Text("Tasa de interés (${interestRateType.shortLabel})") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small
            )
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { amortizationMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(amortizationType.displayName, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = amortizationMenuOpen, onDismissRequest = { amortizationMenuOpen = false }) {
                    LoanAmortizationType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                amortizationType = type
                                amortizationMenuOpen = false
                            }
                        )
                    }
                }
            }
            MoneyInputField(value = monthlyInsurance, onValueChange = { monthlyInsurance = it }, label = "Seguro mensual")
            MoneyInputField(value = monthlyFee, onValueChange = { monthlyFee = it }, label = "Otros cargos")
            OutlinedTextField(
                value = paymentDay,
                onValueChange = { paymentDay = it.filter(Char::isDigit).take(2) },
                label = { Text("Día de pago") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small
            )
            Box(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { accountMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedAccount?.name ?: "Sin cuenta vinculada", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Sin cuenta vinculada") },
                        onClick = {
                            selectedAccount = null
                            accountMenuOpen = false
                        }
                    )
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                selectedAccount = account
                                accountMenuOpen = false
                            }
                        )
                    }
                }
            }
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        MoneyInputField(value = totalAmount, onValueChange = { totalAmount = it }, label = "Monto total")
        OutlinedTextField(
            value = totalInstallments,
            onValueChange = { totalInstallments = it.filter(Char::isDigit) },
            label = { Text("Cuotas") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small
        )
        if (amortizationType == LoanAmortizationType.FIXED_INSTALLMENT) {
            MoneyInputField(value = monthlyInstallment, onValueChange = { monthlyInstallment = it }, label = "Cuota mensual")
        } else {
            calculatedFixedPrincipal?.let {
                Text(
                    "Capital mensual: ${formatCOP(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun normalizeMonthlyInterestRateForPreview(
    interestRateInputValue: Double,
    interestRateType: LoanInterestRateType
): Double {
    val rate = interestRateInputValue.coerceAtLeast(0.0)
    return when (interestRateType) {
        LoanInterestRateType.MONTHLY_EFFECTIVE -> rate
        LoanInterestRateType.EFFECTIVE_ANNUAL -> ((1.0 + rate / 100.0).pow(1.0 / 12.0) - 1.0) * 100.0
        LoanInterestRateType.NOMINAL_ANNUAL_MONTHLY -> rate / 12.0
    }
}

private fun estimatedScheduledPaymentAmount(loan: LoanEntity): Double {
    if (loan.remainingAmount <= 0.0) return 0.0
    val fixedCharges = loan.monthlyInsuranceAmount.coerceAtLeast(0.0) +
            loan.monthlyFeeAmount.coerceAtLeast(0.0)
    return when (loan.amortizationType) {
        LoanAmortizationType.FIXED_INSTALLMENT -> loan.monthlyInstallmentAmount
        LoanAmortizationType.FIXED_PRINCIPAL -> {
            val interest = loan.remainingAmount *
                    loan.monthlyInterestRate.coerceAtLeast(0.0) / 100.0
            val principal = loan.fixedPrincipalAmount.coerceAtLeast(0.0)
                .coerceAtMost(loan.remainingAmount)
            fixedCharges + interest + principal
        }
    }.coerceAtLeast(0.0)
}

private fun estimatedMaximumApplicablePayment(loan: LoanEntity): Double {
    if (loan.remainingAmount <= 0.0) return 0.0
    val fixedCharges = loan.monthlyInsuranceAmount.coerceAtLeast(0.0) +
            loan.monthlyFeeAmount.coerceAtLeast(0.0)
    val interest = loan.remainingAmount *
            loan.monthlyInterestRate.coerceAtLeast(0.0) / 100.0
    return fixedCharges + interest + loan.remainingAmount
}

private fun formatAmountInput(value: Double): String {
    val normalizedValue = value.coerceAtLeast(0.0)
    return if (normalizedValue % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", normalizedValue)
    } else {
        String.format(Locale.US, "%.2f", normalizedValue)
            .trimEnd('0')
            .trimEnd('.')
    }
}

private fun formatRatePercent(value: Double): String {
    return String.format(Locale.US, "%.4f", value)
        .trimEnd('0')
        .trimEnd('.')
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
