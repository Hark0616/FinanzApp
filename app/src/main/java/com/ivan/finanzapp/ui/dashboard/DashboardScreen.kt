package com.ivan.finanzapp.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.components.*
import com.ivan.finanzapp.ui.theme.TrafficGreen
import com.ivan.finanzapp.ui.theme.TrafficRed
import com.ivan.finanzapp.ui.theme.TrafficYellow
import com.ivan.finanzapp.ui.theme.FinanzMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToReviewTransactions: () -> Unit = onNavigateToTransactions,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToBalance: () -> Unit = {},
    onNavigateToCreditCards: () -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // Cabecera superior con botón de Ajustes
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inicio",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        // Banner de permiso de notificaciones
        if (!state.isNotificationPermissionGranted) {
            item {
                NotificationPermissionBanner(
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                )
            }
        }

        // Card de balance total con gradiente
        item {
            BalanceTotalCard(
                totalBalance = state.totalBalance,
                isExpanded = state.isAccountsExpanded,
                onToggleExpand = viewModel::toggleAccountsExpanded
            )
        }

        // Card de flujo de caja disponible del mes
        item {
            DisposableCashFlowCard(
                disposableCashFlow = state.disposableCashFlow,
                totalIncomes = state.totalIncomesThisMonth,
                totalDebt = state.totalDebtInstallmentsThisMonth,
                onClick = onNavigateToBalance
            )
        }

        item {
            ActionSignalsSection(
                state = state,
                onReviewClick = onNavigateToReviewTransactions,
                onNextPaymentClick = {
                    when (state.nextPaymentTarget) {
                        NextPaymentTarget.CREDIT_CARD -> onNavigateToCreditCards()
                        NextPaymentTarget.LOAN -> onNavigateToLoans()
                        null -> onNavigateToBalance()
                    }
                },
                onCaptureClick = onNavigateToSettings,
                onFlowClick = onNavigateToBalance
            )
        }

        // Detalle de cuentas (expandible)
        item {
            AnimatedVisibility(
                visible = state.isAccountsExpanded,
                enter = expandVertically(animationSpec = tween(FinanzMotion.ExpandCollapseMillis)) +
                        fadeIn(animationSpec = tween(FinanzMotion.FadeMillis)),
                exit = shrinkVertically(animationSpec = tween(FinanzMotion.ExpandCollapseMillis)) +
                        fadeOut(animationSpec = tween(FinanzMotion.FadeMillis))
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    state.accounts.forEach { accountItem ->
                        AccountRow(accountItem)
                    }
                }
            }
        }

        // Tarjetas de crédito
        if (state.creditCards.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(
                    text = "Tarjetas de crédito",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            items(state.creditCards) { card ->
                CreditCardSummaryCard(card)
            }
        }

        // Gastos del mes por categoría
        if (state.monthlySpendingByCategory.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(
                    text = "Gastos del mes",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                SpendingByCategoryList(state.monthlySpendingByCategory)
            }
        }

        // Últimos movimientos
        if (state.recentTransactions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle(text = "Últimos movimientos")
                    TextButton(onClick = onNavigateToTransactions) {
                        Text("Ver todos")
                    }
                }
            }
            items(state.recentTransactions) { item ->
                TransactionRow(item)
            }
        }

        // Pantalla vacía si no hay nada
        if (state.accounts.isEmpty() && state.recentTransactions.isEmpty()) {
            item { EmptyStateCard() }
        }
    }
}

@Composable
private fun NotificationPermissionBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Permiso de notificaciones requerido",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    "FinanzApp necesita acceso a notificaciones para detectar tus movimientos automáticamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenSettings) {
                Text("Activar")
            }
        }
    }
}

@Composable
private fun PendingReviewBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(8.dp))
            Text(
                "$count movimientos necesitan revisión",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun ActionSignalsSection(
    state: DashboardUiState,
    onReviewClick: () -> Unit,
    onNextPaymentClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onFlowClick: () -> Unit
) {
    val captureState = when {
        state.captureFailedRecentCount > 0 -> SignalStyle(
            title = "Con error",
            color = TrafficRed,
            subtitle = "${state.captureFailedRecentCount} fallos recientes"
        )
        state.captureQueuedCount > 0 -> SignalStyle(
            title = "Pendiente",
            color = TrafficYellow,
            subtitle = "${state.captureQueuedCount} por leer"
        )
        state.isNotificationPermissionGranted -> SignalStyle(
            title = "Captura activa",
            color = TrafficGreen,
            subtitle = latestCaptureText(state.latestParsedAt)
        )
        else -> SignalStyle(
            title = "Pendiente",
            color = TrafficYellow,
            subtitle = "Activa el permiso"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardSignalCard(
                title = "Por revisar",
                value = state.pendingReviewCount.toString(),
                subtitle = if (state.pendingReviewCount == 1) "Movimiento pendiente" else "Movimientos pendientes",
                icon = Icons.Default.Warning,
                color = if (state.pendingReviewCount > 0) TrafficYellow else TrafficGreen,
                onClick = onReviewClick,
                modifier = Modifier.weight(1f)
            )
            DashboardSignalCard(
                title = "Próximo pago",
                value = state.nextPaymentLabel?.let { formatCOP(state.nextPaymentAmount) } ?: "Sin pagos",
                subtitle = state.nextPaymentLabel?.let {
                    "$it · ${paymentDaysText(state.nextPaymentDays)}"
                } ?: "Tarjetas y créditos al día",
                icon = Icons.Default.Event,
                color = paymentSignalColor(state.nextPaymentDays),
                onClick = onNextPaymentClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashboardSignalCard(
                title = captureState.title,
                value = if (state.captureRecentCount > 0) "${state.captureRecentCount} leídas" else "Sin lecturas",
                subtitle = captureState.subtitle,
                icon = Icons.Default.Notifications,
                color = captureState.color,
                onClick = onCaptureClick,
                modifier = Modifier.weight(1f)
            )
            DashboardSignalCard(
                title = if (state.disposableCashFlow < 0.0) "Flujo negativo" else "Flujo OK",
                value = formatCOP(state.disposableCashFlow),
                subtitle = "Compromisos ${formatPercentage(state.debtLoadRatio * 100)}",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                color = if (state.disposableCashFlow < 0.0) TrafficRed else TrafficGreen,
                onClick = onFlowClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DashboardSignalCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = 118.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BalanceTotalCard(
    totalBalance: Double,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onToggleExpand)
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Disponible en cuentas",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Contraer" else "Expandir",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                formatCOP(totalBalance),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                lineHeight = 44.sp
            )
        }
    }
}

@Composable
private fun AccountRow(item: AccountWithBalance) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(item.account.name, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            formatCOP(item.displayBalance),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CreditCardSummaryCard(card: CreditCardSummary) {
    val barColor = when (card.usageLevel) {
        "LOW" -> TrafficGreen
        "MEDIUM" -> TrafficYellow
        else -> TrafficRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(card.account.name, style = MaterialTheme.typography.titleMedium)
                Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(12.dp))

            // Barra de uso del cupo
            Text(
                "Uso del cupo: ${formatPercentage(card.usagePercentage)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (card.usagePercentage / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = barColor,
                trackColor = barColor.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Deuda actual", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        formatCOP(card.card.currentDebt),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TrafficRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Disponible", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        formatCOP(card.availableCredit),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TrafficGreen
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Cuota mínima", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(formatCOP(card.minimumPayment), style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Próximo pago", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    val dayText = when {
                        card.daysUntilDue == 0 -> "¡Hoy!"
                        card.daysUntilDue == 1 -> "Mañana"
                        card.daysUntilDue < 0 -> "Vencida"
                        else -> "En ${card.daysUntilDue} días"
                    }
                    val dayColor = when {
                        card.daysUntilDue <= 3 -> TrafficRed
                        card.daysUntilDue <= 7 -> TrafficYellow
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(dayText, style = MaterialTheme.typography.bodyMedium, color = dayColor)
                }
            }
        }
    }
}

@Composable
private fun SpendingByCategoryList(items: List<CategorySpendingItem>) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        items.take(6).forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (item.category != null) {
                        CategoryChip(
                            name = item.category.name,
                            colorHex = item.category.color
                        )
                    } else {
                        CategoryChip(name = "Sin categoría", colorHex = "#9E9E9E")
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatCOP(item.total),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        formatPercentage(item.percentage),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(item: TransactionWithCategory) {
    val isIncome = item.transaction.type == TransactionType.INGRESO
    val isCreditCard = item.transaction.type == TransactionType.GASTO_TC ||
            item.transaction.type == TransactionType.PAGO_TC

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.transaction.merchant ?: "Movimiento",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.category != null) {
                    CategoryChip(name = item.category.name, colorHex = item.category.color)
                }
                if (item.transaction.needsReview) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Pendiente de revisión",
                        tint = TrafficYellow,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        AmountText(
            amount = item.transaction.amount,
            isIncome = isIncome,
            fontSize = 15
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun EmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Aún no hay movimientos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "FinanzApp registrará automáticamente tus transacciones cuando lleguen notificaciones de Davivienda, Nequi o Daviplata.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun DisposableCashFlowCard(
    disposableCashFlow: Double,
    totalIncomes: Double,
    totalDebt: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Después de pagos del mes",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatCOP(disposableCashFlow),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 32.sp
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Ingresos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        formatCOP(totalIncomes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TrafficGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Compromisos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        formatCOP(totalDebt),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TrafficRed
                    )
                }
            }
        }
    }
}

private data class SignalStyle(
    val title: String,
    val color: Color,
    val subtitle: String
)

private fun paymentDaysText(days: Int?): String {
    return when {
        days == null -> "Sin fecha"
        days < 0 -> "Vencido"
        days == 0 -> "Hoy"
        days == 1 -> "Mañana"
        else -> "En $days días"
    }
}

private fun paymentSignalColor(days: Int?): Color {
    return when {
        days == null -> TrafficGreen
        days <= 3 -> TrafficRed
        days <= 7 -> TrafficYellow
        else -> TrafficGreen
    }
}

private fun latestCaptureText(latestParsedAt: Long?): String {
    if (latestParsedAt == null) return "Sin lecturas recientes"
    val formatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.forLanguageTag("es-CO"))
    return "Última ${Instant.ofEpochMilli(latestParsedAt).atZone(ZoneId.systemDefault()).format(formatter)}"
}
