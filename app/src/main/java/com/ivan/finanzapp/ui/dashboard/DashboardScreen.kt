package com.ivan.finanzapp.ui.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun DashboardScreen(
    onNavigateToTransactions: () -> Unit = {},
    onNavigateToReviewTransactions: () -> Unit = onNavigateToTransactions,
    onNavigateToUnclassifiedTransactions: () -> Unit = onNavigateToTransactions,
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
        item {
            HomeHeader(
                latestParsedAt = state.latestParsedAt,
                onSettingsClick = onNavigateToSettings
            )
        }

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

        item {
            HomeFinancialHero(
                state = state,
                onPrimaryAction = onNavigateToBalance
            )
        }

        item {
            NextCommitmentSection(
                state = state,
                onClick = {
                    when (state.nextPaymentTarget) {
                        NextPaymentTarget.CREDIT_CARD -> onNavigateToCreditCards()
                        NextPaymentTarget.LOAN -> onNavigateToLoans()
                        null -> onNavigateToBalance()
                    }
                }
            )
        }

        item {
            MonthPulseSection(
                totalIncomes = state.totalIncomesThisMonth,
                totalDebt = state.totalDebtInstallmentsThisMonth
            )
        }

        item {
            HomeSignalsSection(
                state = state,
                onReviewClick = onNavigateToReviewTransactions,
                onUnclassifiedClick = onNavigateToUnclassifiedTransactions,
                onCaptureClick = onNavigateToSettings,
                onSpendingClick = onNavigateToTransactions
            )
        }

        item {
            HomeSummarySection(state = state)
        }

        if (state.recentTransactions.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 26.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Actividad reciente",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToTransactions) {
                        Text("Ver todos")
                    }
                }
            }
            items(state.recentTransactions.take(3)) { item ->
                TransactionRow(item)
            }
        }

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
private fun HomeHeader(
    latestParsedAt: Long?,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Inicio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = latestUpdatedText(latestParsedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onSettingsClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
        }
    }
}

@Composable
private fun HomeFinancialHero(
    state: DashboardUiState,
    onPrimaryAction: () -> Unit
) {
    val isShort = state.disposableCashFlow < 0.0
    val statusText = if (isShort) "Mes por cubrir" else "Con margen"
    val projectionTitle = if (isShort) "Faltante para compromisos" else "Margen después de compromisos"
    val projectionCopy = if (isShort) {
        "Si cubres tus compromisos programados, faltan ${formatCOP(abs(state.disposableCashFlow))}."
    } else {
        "Después de compromisos, te queda ${formatCOP(state.disposableCashFlow)}."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Disponible ahora",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = if (isShort) TrafficYellow.copy(alpha = 0.18f) else TrafficGreen.copy(alpha = 0.18f),
                contentColor = if (isShort) TrafficYellow else TrafficGreen,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = formatCOP(state.totalBalance),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = projectionCopy,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = projectionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ingresos ${formatCOP(state.totalIncomesThisMonth)} · Compromisos ${formatCOP(state.totalDebtInstallmentsThisMonth)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                )
            }
            Text(
                text = formatCOP(state.disposableCashFlow),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isShort) TrafficRed else TrafficGreen
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onPrimaryAction,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.height(46.dp)
        ) {
            Text("Ver plan del mes", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NextCommitmentSection(
    state: DashboardUiState,
    onClick: () -> Unit
) {
    val label = state.nextPaymentLabel
    HomeSectionHeader("Siguiente acción")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = nextPaymentTypeText(state.nextPaymentTarget),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = label ?: "Sin compromisos próximos",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (label == null) {
                            "Tarjetas y créditos al día"
                        } else {
                            paymentDaysText(state.nextPaymentDays)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = label?.let { formatCOP(state.nextPaymentAmount) } ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (label != null) {
                        Text(
                            text = "Prioridad",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = paymentSignalColor(state.nextPaymentDays)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onClick,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(nextPaymentActionText(state.nextPaymentTarget), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MonthPulseSection(
    totalIncomes: Double,
    totalDebt: Double
) {
    val total = totalIncomes + totalDebt
    val incomeRatio = if (total > 0.0) (totalIncomes / total).toFloat().coerceIn(0f, 1f) else 0f

    HomeSectionHeader("Pulso del mes")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(16.dp)) {
            LinearProgressIndicator(
                progress = { incomeRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = TrafficGreen,
                trackColor = TrafficYellow.copy(alpha = 0.45f)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PulseLabel("Ingresos", formatCOP(totalIncomes))
                PulseLabel("Compromisos", formatCOP(totalDebt), alignEnd = true)
            }
        }
    }
}

@Composable
private fun PulseLabel(
    label: String,
    value: String,
    alignEnd: Boolean = false
) {
    Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HomeSignalsSection(
    state: DashboardUiState,
    onReviewClick: () -> Unit,
    onUnclassifiedClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onSpendingClick: () -> Unit
) {
    val signals = buildList {
        when {
            state.captureFailedRecentCount > 0 -> add(
                HomeSignal(
                    title = "Captura con error",
                    subtitle = "${state.captureFailedRecentCount} fallos recientes",
                    action = "Ajustes",
                    color = TrafficRed,
                    onClick = onCaptureClick
                )
            )
            state.captureQueuedCount > 0 -> add(
                HomeSignal(
                    title = "Lecturas pendientes",
                    subtitle = "${state.captureQueuedCount} por leer",
                    action = "Ajustes",
                    color = TrafficYellow,
                    onClick = onCaptureClick
                )
            )
        }

        if (state.unclassifiedTransactionCount > 0) {
            add(
                HomeSignal(
                    title = "Movimientos sin cuenta",
                    subtitle = "${state.unclassifiedTransactionCount} por asociar",
                    action = "Asociar",
                    color = TrafficYellow,
                    onClick = onUnclassifiedClick
                )
            )
        }

        if (state.pendingReviewCount > 0) {
            add(
                HomeSignal(
                    title = "Movimientos por revisar",
                    subtitle = "${state.pendingReviewCount} pendientes",
                    action = "Revisar",
                    color = TrafficYellow,
                    onClick = onReviewClick
                )
            )
        }

        if (
            state.dominantSpendingCategoryName.equals("Otros", ignoreCase = true) &&
            state.dominantSpendingPercentage > 50.0
        ) {
            add(
                HomeSignal(
                    title = "Revisar categoría Otros",
                    subtitle = "Concentra ${formatPercentage(state.dominantSpendingPercentage)} del gasto registrado",
                    action = "Revisar",
                    color = TrafficYellow,
                    onClick = onSpendingClick
                )
            )
        }
    }.take(2).ifEmpty {
        listOf(
            HomeSignal(
                title = "Todo al día",
                subtitle = "Sin señales críticas por ahora",
                action = "Listo",
                color = TrafficGreen,
                onClick = null
            )
        )
    }

    HomeSectionHeader("Señales")
    Column(
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        signals.forEachIndexed { index, signal ->
            HomeSignalRow(signal)
            if (index < signals.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun HomeSignalRow(signal: HomeSignal) {
    val clickableModifier = signal.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = signal.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = signal.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = signal.action,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = signal.color
        )
    }
}

@Composable
private fun HomeSummarySection(state: DashboardUiState) {
    HomeSectionHeader("Resumen")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        ),
        shape = RoundedCornerShape(22.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            HomeSummaryRow(
                title = "Cuentas",
                subtitle = "${state.accounts.size} cuentas activas",
                value = formatCOP(state.totalBalance)
            )
            HomeSummaryRow(
                title = "Saldo en tarjetas",
                subtitle = "${state.creditCards.size} tarjetas activas",
                value = formatCOP(state.totalCreditCardDebt)
            )
            HomeSummaryRow(
                title = "Créditos pendientes",
                subtitle = "Capital por pagar",
                value = formatCOP(state.totalLoanRemaining)
            )
            HomeSummaryRow(
                title = "Gastos registrados",
                subtitle = state.dominantSpendingCategoryName?.let { "$it domina la lectura" } ?: "Sin gastos este mes",
                value = formatCOP(state.monthlySpendingTotal),
                showDivider = false
            )
        }
    }
}

@Composable
private fun HomeSummaryRow(
    title: String,
    subtitle: String,
    value: String,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    }
}

@Composable
private fun HomeSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 26.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

private data class HomeSignal(
    val title: String,
    val subtitle: String,
    val action: String,
    val color: Color,
    val onClick: (() -> Unit)?
)

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

private fun paymentDaysText(days: Int?): String {
    return when {
        days == null -> "Sin fecha"
        days < 0 -> "Vencido"
        days == 0 -> "Hoy"
        days == 1 -> "Mañana"
        else -> "En $days días"
    }
}

private fun nextPaymentTypeText(target: NextPaymentTarget?): String {
    return when (target) {
        NextPaymentTarget.CREDIT_CARD -> "Tarjeta por pagar"
        NextPaymentTarget.LOAN -> "Crédito por pagar"
        null -> "Sin pagos próximos"
    }
}

private fun nextPaymentActionText(target: NextPaymentTarget?): String {
    return when (target) {
        NextPaymentTarget.CREDIT_CARD -> "Ver tarjeta"
        NextPaymentTarget.LOAN -> "Preparar pago"
        null -> "Ver análisis"
    }
}

private fun latestUpdatedText(latestParsedAt: Long?): String {
    if (latestParsedAt == null) return "Captura pendiente"
    val formatter = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.forLanguageTag("es-CO"))
    val formatted = Instant.ofEpochMilli(latestParsedAt)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
    return "Actualizado $formatted"
}

private fun paymentSignalColor(days: Int?): Color {
    return when {
        days == null -> TrafficGreen
        days <= 3 -> TrafficRed
        days <= 7 -> TrafficYellow
        else -> TrafficGreen
    }
}
