package com.ivan.finanzapp.ui.creditcard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.DeferredPurchaseEntity
import com.ivan.finanzapp.domain.calculator.CreditCardCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.ActionSheet
import com.ivan.finanzapp.ui.components.MoneyInputField
import com.ivan.finanzapp.ui.components.ProgressiveFormSheet
import com.ivan.finanzapp.ui.components.QuickSelectOption
import com.ivan.finanzapp.ui.components.QuickSelectSheet
import com.ivan.finanzapp.ui.components.formatEditableAmount
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.components.formatPercentage
import com.ivan.finanzapp.ui.components.parseMoneyInput
import com.ivan.finanzapp.ui.dashboard.CreditCardSummary
import com.ivan.finanzapp.ui.theme.TrafficGreen
import com.ivan.finanzapp.ui.theme.TrafficRed
import com.ivan.finanzapp.ui.theme.TrafficYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditCardsScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: CreditCardsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedForPayment by remember { mutableStateOf<CreditCardSummary?>(null) }
    var selectedForDeferredPurchase by remember { mutableStateOf<CreditCardSummary?>(null) }
    var editingDeferredPurchase by remember { mutableStateOf<Pair<CreditCardSummary, DeferredPurchaseEntity>?>(null) }
    var selectedCardIdForDetail by remember { mutableStateOf<String?>(null) }

    val resetRoot by navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("reset_root", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    LaunchedEffect(resetRoot) {
        if (resetRoot) {
            selectedCardIdForDetail = null
            navController.currentBackStackEntry?.savedStateHandle?.set("reset_root", false)
        }
    }

    val selectedCardSummary = remember(state.creditCards, selectedCardIdForDetail) {
        state.creditCards.find { it.card.id == selectedCardIdForDetail }
    }

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
        } else if (selectedCardSummary != null) {
            // Vista de DETALLE de la tarjeta seleccionada
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CreditCardDetailView(
                    summary = selectedCardSummary,
                    onBackClick = { selectedCardIdForDetail = null },
                    onPayClick = { selectedForPayment = selectedCardSummary },
                    onAddDeferredClick = { selectedForDeferredPurchase = selectedCardSummary },
                    onDeletePurchaseClick = { purchaseId ->
                        viewModel.deleteDeferredPurchase(purchaseId, selectedCardSummary.card.id)
                    },
                    onMarkPaidClick = { purchaseId ->
                        viewModel.markInstallmentPaid(purchaseId, selectedCardSummary.card.id)
                    },
                    onEditPurchaseClick = { purchase ->
                        editingDeferredPurchase = selectedCardSummary to purchase
                    }
                )
            }
        } else {
            // Vista de LISTADO general de tarjetas
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
                            onCardClick = { selectedCardIdForDetail = cardSummary.card.id }
                        )
                    }
                }
            }
        }

        selectedForPayment?.let { summary ->
            PayCardSheet(
                summary = summary,
                accounts = state.accounts,
                onDismiss = { selectedForPayment = null },
                onConfirm = { amount, fundingAccountId ->
                    viewModel.payCreditCard(summary.card, amount, fundingAccountId)
                    selectedForPayment = null
                }
            )
        }

        selectedForDeferredPurchase?.let { summary ->
            DeferredPurchaseSheet(
                cardSummary = summary,
                onDismiss = { selectedForDeferredPurchase = null },
                onConfirm = { desc, amount, totalInst, paidInst, dateLong, interest ->
                    viewModel.addDeferredPurchase(summary.card.id, desc, amount, totalInst, paidInst, dateLong, interest)
                    selectedForDeferredPurchase = null
                }
            )
        }

        editingDeferredPurchase?.let { (summary, purchase) ->
            DeferredPurchaseSheet(
                purchase = purchase,
                cardSummary = summary,
                onDismiss = { editingDeferredPurchase = null },
                onConfirm = { desc, amount, totalInst, paidInst, dateLong, interest ->
                    viewModel.updateDeferredPurchase(
                        purchaseId = purchase.id,
                        cardId = summary.card.id,
                        description = desc,
                        totalAmount = amount,
                        totalInstallments = totalInst,
                        paidInstallments = paidInst,
                        purchaseDate = dateLong,
                        interestRateEA = interest
                    )
                    editingDeferredPurchase = null
                }
            )
        }
    }
}


@Composable
private fun PhysicalLikeCreditCard(
    summary: CreditCardSummary,
    onCardClick: () -> Unit
) {
    val barColor = when (summary.usageLevel) {
        "LOW" -> TrafficGreen
        "MEDIUM" -> TrafficYellow
        else -> TrafficRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(8.dp).size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.account.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Vence ${cardDueText(summary.daysUntilDue)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LinearProgressIndicator(
                progress = { (summary.usagePercentage / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.16f)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Deuda", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatCOP(summary.card.currentDebt), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TrafficRed)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Disponible", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatCOP(summary.availableCredit), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TrafficGreen)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Pago mín.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatCOP(summary.minimumPayment), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CreditCardDetailView(
    summary: CreditCardSummary,
    onBackClick: () -> Unit,
    onPayClick: () -> Unit,
    onAddDeferredClick: () -> Unit,
    onDeletePurchaseClick: (String) -> Unit,
    onMarkPaidClick: (String) -> Unit,
    onEditPurchaseClick: (DeferredPurchaseEntity) -> Unit
) {
    val calculator = remember { CreditCardCalculator() }
    val nextBillingCutoff = remember(summary.card) { calculator.nextBillingCutoffDate(summary.card) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Cabecera
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = summary.account.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "••••  ••••  ••••  ${summary.account.lastFourDigits ?: summary.card.id.takeLast(4).ifBlank { "8888" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Tarjeta de Resumen de Saldos (Highlight)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "DEUDA TOTAL",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatCOP(summary.card.currentDebt),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Barra de progreso de uso del cupo
                        val barColor = when (summary.usageLevel) {
                            "LOW" -> TrafficGreen
                            "MEDIUM" -> TrafficYellow
                            else -> TrafficRed
                        }
                        LinearProgressIndicator(
                            progress = { (summary.usagePercentage / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cupo Disponible",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatCOP(summary.availableCredit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Cupo Total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatCOP(summary.card.creditLimit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Información de Facturación y Vencimiento
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "FACTURACIÓN Y PAGOS",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        
                        // Pago Mínimo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Pago Mínimo del Mes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatCOP(summary.minimumPayment),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Día de Corte
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Día de Corte",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Día ${summary.card.cutoffDay} de cada mes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Vencimiento del Pago
                        val isUrgent = summary.daysUntilDue <= 3
                        val badgeBgColor = if (isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(badgeBgColor.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isUrgent) Icons.Default.Warning else Icons.Default.Event,
                                    contentDescription = null,
                                    tint = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fecha de Vencimiento",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val daysText = when {
                                    summary.daysUntilDue == 0 -> "¡Hoy vence!"
                                    summary.daysUntilDue < 0 -> "Vencida"
                                    else -> "Día ${summary.card.paymentDueDay} (en ${summary.daysUntilDue} días)"
                                }
                                Text(
                                    text = daysText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Botón de Registrar Pago / Abono
            if (summary.card.currentDebt > 0.0) {
                item {
                    Button(
                        onClick = onPayClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = "Registrar Abono / Pago",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Sección de Compras
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compras (${summary.activeDeferredCount} activas)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = onAddDeferredClick,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar compra",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (summary.deferredPurchases.isEmpty()) {
                item {
                    Text(
                        text = "No tienes compras registradas en esta tarjeta. Agrégalas presionando el botón '+'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(summary.deferredPurchases, key = { it.id }) { purchase ->
                    DeferredPurchaseItem(
                        purchase = purchase,
                        cardInterestRateEA = summary.card.interestRateEA,
                        cutoffDay = summary.card.cutoffDay,
                        nextBillingCutoff = nextBillingCutoff,
                        onMarkPaid = onMarkPaidClick,
                        onDelete = onDeletePurchaseClick,
                        onEdit = onEditPurchaseClick
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayCardSheet(
    summary: CreditCardSummary,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, fundingAccountId: String?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var accountSheetOpen by remember { mutableStateOf(false) }
    val amountVal = parseMoneyInput(amount)
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId }

    ActionSheet(
        title = "Registrar pago",
        subtitle = "${summary.account.name} · deuda ${formatCOP(summary.card.currentDebt)}",
        onDismiss = onDismiss
    ) {
        MoneyInputField(
            value = amount,
            onValueChange = { amount = it },
            label = "Monto",
            isError = amount.isNotBlank() && amountVal == null,
            supportingText = if (amount.isNotBlank() && amountVal == null) "Monto inválido" else null
        )
        OutlinedButton(
            onClick = { accountSheetOpen = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("Cuenta de fondos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedAccount?.name ?: "Pago externo",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        SheetActions(
            primaryLabel = "Confirmar",
            canSubmit = amountVal != null && amountVal > 0.0,
            onDismiss = onDismiss,
            onSubmit = { amountVal?.let { onConfirm(it, selectedAccountId) } }
        )
    }

    if (accountSheetOpen) {
        QuickSelectSheet(
            title = "Cuenta de fondos",
            options = listOf(
                QuickSelectOption<String?>(
                    value = null,
                    title = "Pago externo",
                    subtitle = "No descuenta una cuenta",
                    icon = Icons.Default.Clear
                )
            ) + accounts.map { account ->
                QuickSelectOption<String?>(
                    value = account.id,
                    title = account.name,
                    subtitle = "Saldo ${formatCOP(account.currentBalance)}",
                    icon = Icons.Default.AccountBalanceWallet
                )
            },
            selectedValue = selectedAccountId,
            onDismiss = { accountSheetOpen = false },
            onSelect = { selectedAccountId = it }
        )
    }
}

@Composable
private fun DeferredPurchaseSheet(
    cardSummary: CreditCardSummary,
    onDismiss: () -> Unit,
    onConfirm: (description: String, totalAmount: Double, totalInstallments: Int, paidInstallments: Int, purchaseDate: Long, interestRateEA: Double?) -> Unit,
    purchase: DeferredPurchaseEntity? = null
) {
    var description by remember(purchase?.id) { mutableStateOf(purchase?.description.orEmpty()) }
    var totalAmount by remember(purchase?.id) { mutableStateOf(purchase?.totalAmount?.let(::formatEditableAmount).orEmpty()) }
    var totalInstallments by remember(purchase?.id) { mutableStateOf(purchase?.totalInstallments?.toString().orEmpty()) }
    var paidInstallments by remember(purchase?.id) { mutableStateOf(purchase?.paidInstallments?.toString() ?: "0") }
    var purchaseDate by remember(purchase?.id) { mutableStateOf(purchase?.purchaseDate ?: System.currentTimeMillis()) }
    var interestRateEA by remember(purchase?.id) { mutableStateOf(purchase?.interestRateEA?.let(::formatEditableAmount).orEmpty()) }
    var allowOverdraft by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val amountVal = parseMoneyInput(totalAmount) ?: 0.0
    val totalInstVal = totalInstallments.toIntOrNull() ?: 0
    val paidInstVal = paidInstallments.toIntOrNull() ?: 0
    val interestVal = interestRateEA.trim().replace(",", ".").toDoubleOrNull()
    val isInterestValid = interestRateEA.isBlank() || interestVal != null
    val calculator = remember { CreditCardCalculator() }
    val oldRemainingDebt = purchase?.let { calculator.remainingDebt(it) } ?: 0.0
    val remainingInstVal = (totalInstVal - paidInstVal).coerceAtLeast(0)
    val newInstallment = if (totalInstVal > 0) amountVal / totalInstVal else 0.0
    val newRemainingDebt = newInstallment * remainingInstVal
    val isOverdraft = newRemainingDebt > cardSummary.availableCredit + oldRemainingDebt

    fun autoComputePaid(dateLong: Long, totalInstStr: String) {
        val totalInst = totalInstStr.toIntOrNull() ?: return
        val today = LocalDate.now()
        val pDate = Instant.ofEpochMilli(dateLong)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val calculatedBilled = calculator.billedInstallments(
            pDate,
            cardSummary.card.cutoffDay,
            today,
            totalInst
        )
        paidInstallments = calculatedBilled.toString()
    }

    val datePickerDialog = remember(purchaseDate, totalInstallments) {
        val cal = Calendar.getInstance().apply { timeInMillis = purchaseDate }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance()
                newCal.set(Calendar.YEAR, year)
                newCal.set(Calendar.MONTH, month)
                newCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                purchaseDate = newCal.timeInMillis
                autoComputePaid(newCal.timeInMillis, totalInstallments)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    ProgressiveFormSheet(
        title = if (purchase == null) "Compra diferida" else "Editar compra",
        subtitle = cardSummary.account.name,
        onDismiss = onDismiss,
        primaryActionLabel = if (purchase == null) "Guardar" else "Actualizar",
        canSubmit = description.isNotBlank() &&
                amountVal > 0.0 &&
                totalInstVal > 0 &&
                paidInstVal >= 0 &&
                paidInstVal <= totalInstVal &&
                isInterestValid &&
                (!isOverdraft || allowOverdraft),
        onSubmit = {
            onConfirm(description.trim(), amountVal, totalInstVal, paidInstVal, purchaseDate, interestVal)
        },
        advancedLabel = "Avanzado",
        advancedContent = {
            OutlinedTextField(
                value = paidInstallments,
                onValueChange = { paidInstallments = it.filter(Char::isDigit) },
                label = { Text("Cuotas pagadas") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small
            )
            OutlinedButton(
                onClick = { datePickerDialog.show() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(dateFormat.format(Date(purchaseDate)), modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            OutlinedTextField(
                value = interestRateEA,
                onValueChange = { interestRateEA = it.filter { char -> char.isDigit() || char == '.' || char == ',' } },
                label = { Text("Tasa EA") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small
            )
        }
    ) {
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        MoneyInputField(
            value = totalAmount,
            onValueChange = { totalAmount = it },
            label = "Monto"
        )
        OutlinedTextField(
            value = totalInstallments,
            onValueChange = {
                totalInstallments = it.filter(Char::isDigit)
                autoComputePaid(purchaseDate, totalInstallments)
            },
            label = { Text("Cuotas") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.small
        )
        if (isOverdraft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowOverdraft, onCheckedChange = { allowOverdraft = it })
                Text(
                    text = "Sobregira el cupo disponible (${formatCOP(cardSummary.availableCredit + oldRemainingDebt)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SheetActions(
    primaryLabel: String,
    canSubmit: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text("Cancelar")
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.weight(1f)
        ) {
            Text(primaryLabel)
        }
    }
}

private fun cardDueText(days: Int): String {
    return when {
        days < 0 -> "vencida"
        days == 0 -> "hoy"
        days == 1 -> "mañana"
        else -> "en $days días"
    }
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
            "Ve a Ajustes → Agregar Cuenta y crea una cuenta seleccionando el tipo 'Tarjeta de Crédito' " +
                    "para que empiece a mostrarse aquí con su cupo, corte y control de vencimiento.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun DeferredPurchaseItem(
    purchase: DeferredPurchaseEntity,
    cardInterestRateEA: Double?,
    cutoffDay: Int,
    nextBillingCutoff: LocalDate,
    onMarkPaid: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (DeferredPurchaseEntity) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(purchase.purchaseDate))
    val calculator = remember { CreditCardCalculator() }
    val amountDueThisMonth = calculator.amountDue(purchase, cardInterestRateEA, cutoffDay, nextBillingCutoff)
    val remaining = (purchase.totalInstallments - purchase.paidInstallments).coerceAtLeast(0)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Fila Superior: Título + Badge de Cuotas Restantes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = purchase.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateStr,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        val rate = purchase.interestRateEA ?: cardInterestRateEA
                        if (rate != null && rate > 0.0) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${rate}% E.A.${if (purchase.interestRateEA != null) " (indiv.)" else ""}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Badge de Cuotas Restantes
                Box(
                    modifier = Modifier
                        .background(
                            if (remaining > 0) MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (remaining > 0) "$remaining cuotas pend." else "Pagada",
                        color = if (remaining > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Fila Central: Datos Financieros
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Monto Total",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatCOP(purchase.totalAmount),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val installment = calculator.installmentAmount(purchase, cardInterestRateEA)
                    Text(
                        text = "Valor Cuota: ${formatCOP(installment)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (amountDueThisMonth > 0.0) {
                        Text(
                            text = "Factura este mes: ${formatCOP(amountDueThisMonth)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Factura este mes: $0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Barra de Progreso
            val progress = if (purchase.totalInstallments > 0) purchase.paidInstallments.toFloat() / purchase.totalInstallments else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val installment = if (purchase.totalInstallments > 0) purchase.totalAmount / purchase.totalInstallments else 0.0
                val remainingDebt = installment * remaining
                Text(
                    text = "Cuotas pagadas: ${purchase.paidInstallments}/${purchase.totalInstallments}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Deuda Restante: ${formatCOP(remainingDebt)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            // Botones de acción alineados abajo de forma elegante y espaciada
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Botón Editar
                TextButton(
                    onClick = { onEdit(purchase) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Editar", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.width(8.dp))

                // Botón Pagar Cuota (si quedan cuotas por pagar)
                if (remaining > 0) {
                    TextButton(
                        onClick = { onMarkPaid(purchase.id) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Pagar Cuota", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Botón Eliminar
                TextButton(
                    onClick = { onDelete(purchase.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Eliminar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
