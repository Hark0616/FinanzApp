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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
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
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.components.formatPercentage
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

        // Diálogo para Agregar Compra
        selectedForDeferredPurchase?.let { summary ->
            AddDeferredPurchaseDialog(
                cardSummary = summary,
                onDismiss = { selectedForDeferredPurchase = null },
                onConfirm = { desc, amount, totalInst, paidInst, dateLong, interest ->
                    viewModel.addDeferredPurchase(summary.card.id, desc, amount, totalInst, paidInst, dateLong, interest)
                    selectedForDeferredPurchase = null
                }
            )
        }

        // Diálogo para Editar Compra
        editingDeferredPurchase?.let { (summary, purchase) ->
            EditDeferredPurchaseDialog(
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
    val calculator = remember { CreditCardCalculator() }

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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
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

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Toca para ver detalle →",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
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
                    imageVector = Icons.Default.ArrowBack,
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
                    text = "••••  ••••  ••••  ${summary.card.id.takeLast(4).ifBlank { "8888" }}",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeferredPurchaseDialog(
    cardSummary: CreditCardSummary,
    onDismiss: () -> Unit,
    onConfirm: (description: String, totalAmount: Double, totalInstallments: Int, paidInstallments: Int, purchaseDate: Long, interestRateEA: Double?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var totalAmount by remember { mutableStateOf("") }
    var totalInstallments by remember { mutableStateOf("") }
    var paidInstallments by remember { mutableStateOf("0") }
    var purchaseDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var interestRateEA by remember { mutableStateOf("") }
    var allowOverdraft by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    fun autoComputePaid(dateLong: Long, totalInstStr: String) {
        val totalInst = totalInstStr.toIntOrNull() ?: return
        val today = LocalDate.now()
        val pDate = Instant.ofEpochMilli(dateLong)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val calculator = CreditCardCalculator()
        val calculatedBilled = calculator.billedInstallments(
            pDate,
            cardSummary.card.cutoffDay,
            today,
            totalInst
        )
        paidInstallments = calculatedBilled.toString()
    }

    val amountVal = totalAmount.toDoubleOrNull() ?: 0.0
    val isOverdraft = amountVal > cardSummary.availableCredit

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Compra") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Agrega una compra a cuotas activa en la tarjeta \"${cardSummary.account.name}\". " +
                            "La deuda de esta compra se sumará a la deuda total de la tarjeta.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    placeholder = { Text("Ej. Televisor, Ropa Zara, etc.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = totalAmount,
                    onValueChange = { totalAmount = it },
                    label = { Text("Monto Total ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = totalInstallments,
                    onValueChange = { newValue ->
                        totalInstallments = newValue
                        autoComputePaid(purchaseDate, newValue)
                    },
                    label = { Text("Cuotas Totales") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = paidInstallments,
                    onValueChange = { paidInstallments = it },
                    label = { Text("Cuotas Pagadas") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = interestRateEA,
                    onValueChange = { interestRateEA = it },
                    label = { Text("Tasa de Interés E.A. % (Opcional)") },
                    placeholder = { Text("Ej. 28.5") },
                    supportingText = { Text("Si queda vacío, usa la tasa de la tarjeta.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Campo interactivo para la fecha de compra
                val cal = Calendar.getInstance().apply { timeInMillis = purchaseDate }
                val datePickerDialog = DatePickerDialog(
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

                OutlinedTextField(
                    value = dateFormat.format(Date(purchaseDate)),
                    onValueChange = {},
                    label = { Text("Fecha de Compra") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() },
                    enabled = false, // Deshabilitar entrada de texto para forzar el click en el DatePicker
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (isOverdraft) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allowOverdraft,
                            onCheckedChange = { allowOverdraft = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Acepto registrar esta compra sobregirando la tarjeta (Cupo disponible: ${formatCOP(cardSummary.availableCredit)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            val totalInstVal = totalInstallments.toIntOrNull() ?: 0
            val paidInstVal = paidInstallments.toIntOrNull() ?: 0
            val interestVal = interestRateEA.trim().toDoubleOrNull()
            val isInterestValid = interestRateEA.isBlank() || interestVal != null
            val isValid = description.isNotBlank() &&
                    amountVal > 0.0 &&
                    totalInstVal > 0 &&
                    paidInstVal >= 0 &&
                    paidInstVal <= totalInstVal &&
                    isInterestValid &&
                    (!isOverdraft || allowOverdraft)

            Button(
                onClick = {
                    onConfirm(description, amountVal, totalInstVal, paidInstVal, purchaseDate, interestVal)
                },
                enabled = isValid
            ) {
                Text("Guardar Compra")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDeferredPurchaseDialog(
    purchase: DeferredPurchaseEntity,
    cardSummary: CreditCardSummary,
    onDismiss: () -> Unit,
    onConfirm: (description: String, totalAmount: Double, totalInstallments: Int, paidInstallments: Int, purchaseDate: Long, interestRateEA: Double?) -> Unit
) {
    var description by remember { mutableStateOf(purchase.description) }
    var totalAmount by remember { mutableStateOf(purchase.totalAmount.toString()) }
    var totalInstallments by remember { mutableStateOf(purchase.totalInstallments.toString()) }
    var paidInstallments by remember { mutableStateOf(purchase.paidInstallments.toString()) }
    var purchaseDate by remember { mutableStateOf(purchase.purchaseDate) }
    var interestRateEA by remember { mutableStateOf(purchase.interestRateEA?.toString() ?: "") }
    var allowOverdraft by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    fun autoComputePaid(dateLong: Long, totalInstStr: String) {
        val totalInst = totalInstStr.toIntOrNull() ?: return
        val today = LocalDate.now()
        val pDate = Instant.ofEpochMilli(dateLong)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val calculator = CreditCardCalculator()
        val calculatedBilled = calculator.billedInstallments(
            pDate,
            cardSummary.card.cutoffDay,
            today,
            totalInst
        )
        paidInstallments = calculatedBilled.toString()
    }

    val amountVal = totalAmount.toDoubleOrNull() ?: 0.0
    val totalInstVal = totalInstallments.toIntOrNull() ?: 0
    val paidInstVal = paidInstallments.toIntOrNull() ?: 0
    val calculator = remember { CreditCardCalculator() }
    val oldRemainingDebt = calculator.remainingDebt(purchase)
    val remainingInstVal = (totalInstVal - paidInstVal).coerceAtLeast(0)
    val newInstVal = if (totalInstVal > 0) amountVal / totalInstVal else 0.0
    val newRemainingDebt = newInstVal * remainingInstVal
    val isOverdraft = newRemainingDebt > cardSummary.availableCredit + oldRemainingDebt

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Compra") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Modifica los detalles de la compra \"${purchase.description}\".",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = totalAmount,
                    onValueChange = { totalAmount = it },
                    label = { Text("Monto Total ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = totalInstallments,
                    onValueChange = { newValue ->
                        totalInstallments = newValue
                        autoComputePaid(purchaseDate, newValue)
                    },
                    label = { Text("Cuotas Totales") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = paidInstallments,
                    onValueChange = { paidInstallments = it },
                    label = { Text("Cuotas Pagadas") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = interestRateEA,
                    onValueChange = { interestRateEA = it },
                    label = { Text("Tasa de Interés E.A. % (Opcional)") },
                    placeholder = { Text("Ej. 28.5") },
                    supportingText = { Text("Si queda vacío, usa la tasa de la tarjeta.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Campo interactivo para la fecha de compra
                val cal = Calendar.getInstance().apply { timeInMillis = purchaseDate }
                val datePickerDialog = DatePickerDialog(
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

                OutlinedTextField(
                    value = dateFormat.format(Date(purchaseDate)),
                    onValueChange = {},
                    label = { Text("Fecha de Compra") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                if (isOverdraft) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allowOverdraft,
                            onCheckedChange = { allowOverdraft = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Acepto registrar esta compra sobregirando la tarjeta (Cupo disponible para esta compra: ${formatCOP(cardSummary.availableCredit + oldRemainingDebt)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            val interestVal = interestRateEA.trim().toDoubleOrNull()
            val isInterestValid = interestRateEA.isBlank() || interestVal != null
            val isValid = description.isNotBlank() &&
                    amountVal > 0.0 &&
                    totalInstVal > 0 &&
                    paidInstVal >= 0 &&
                    paidInstVal <= totalInstVal &&
                    isInterestValid &&
                    (!isOverdraft || allowOverdraft)

            Button(
                onClick = {
                    onConfirm(description, amountVal, totalInstVal, paidInstVal, purchaseDate, interestVal)
                },
                enabled = isValid
            ) {
                Text("Guardar Cambios")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
