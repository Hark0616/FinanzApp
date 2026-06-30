package com.ivan.finanzapp.ui.assets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.entity.AssetType
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.ui.components.ActionSheet
import com.ivan.finanzapp.ui.components.AmountText
import com.ivan.finanzapp.ui.components.MoneyInputField
import com.ivan.finanzapp.ui.components.QuickSelectOption
import com.ivan.finanzapp.ui.components.QuickSelectSheet
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatEditableAmount
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.components.parseMoneyInput
import com.ivan.finanzapp.ui.dashboard.TransactionWithCategory
import com.ivan.finanzapp.ui.theme.TrafficGreen
import com.ivan.finanzapp.ui.theme.TrafficRed
import com.ivan.finanzapp.ui.theme.TrafficYellow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    viewModel: AssetsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCategoryForAdd by remember { mutableStateOf<AssetType?>(null) }
    var editingAsset by remember { mutableStateOf<AssetEntity?>(null) }
    var isAddIncomeDialogVisible by remember { mutableStateOf(false) }
    var isAssetTypeSheetVisible by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Flujo", "Patrimonio")

    Scaffold(
        floatingActionButton = {
            when {
                selectedTab == 0 -> {
                    FloatingActionButton(
                        onClick = { isAddIncomeDialogVisible = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar ingreso",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                selectedTab == 1 && state.assets.isNotEmpty() -> {
                    FloatingActionButton(
                        onClick = { isAssetTypeSheetVisible = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar activo",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Selector de pestaña superior
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                if (selectedTab == 0) {
                    // Vista 1: Flujo de Caja (Ingresos vs Egresos Comprometidos)
                    CashFlowTabContent(
                        state = state
                    )
                } else {
                    // Vista 2: Patrimonio e Inmuebles (Activos)
                    AssetsTabContent(
                        state = state,
                        onAddAssetType = { selectedCategoryForAdd = it },
                        onEditAsset = { editingAsset = it },
                        onDeleteAsset = { viewModel.deleteAsset(it.id) }
                    )
                }
            }
        }

        // Diálogos
        if (isAddIncomeDialogVisible) {
            AddIncomeSheet(
                accounts = state.accounts,
                categories = state.categories,
                onDismiss = { isAddIncomeDialogVisible = false },
                onConfirm = { description, amount, accountId, categoryId ->
                    viewModel.addIncomeTransaction(description, amount, accountId, categoryId)
                    isAddIncomeDialogVisible = false
                }
            )
        }

        if (isAssetTypeSheetVisible) {
            AssetTypeActionSheet(
                onDismiss = { isAssetTypeSheetVisible = false },
                onSelect = { type ->
                    selectedCategoryForAdd = type
                    isAssetTypeSheetVisible = false
                }
            )
        }

        selectedCategoryForAdd?.let { category ->
            AddAssetSheet(
                category = category,
                onDismiss = { selectedCategoryForAdd = null },
                onConfirm = { name, amount, type ->
                    viewModel.addAsset(name, amount, type)
                    selectedCategoryForAdd = null
                }
            )
        }

        editingAsset?.let { asset ->
            EditAssetSheet(
                asset = asset,
                onDismiss = { editingAsset = null },
                onConfirm = { name, amount, type ->
                    viewModel.updateAsset(asset.id, name, amount, type)
                    editingAsset = null
                }
            )
        }
    }
}

@Composable
private fun CashFlowTabContent(
    state: BalanceUiState
) {
    val totalDebtInstallments = state.totalCreditCardInstallments + state.totalLoanInstallments
    val dtiRatio = if (state.totalIncomesThisMonth > 0) totalDebtInstallments / state.totalIncomesThisMonth else 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            SectionTitle("Flujo de Caja del Mes")
        }

        // Resumen General Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Flujo de Caja Disponible",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatCOP(state.disposableCashFlow),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "(+) Ingresos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                formatCOP(state.totalIncomesThisMonth),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TrafficGreen
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "(-) Deuda Comprometida",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Text(
                                formatCOP(totalDebtInstallments),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TrafficRed
                            )
                        }
                    }
                }
            }
        }

        // Relación Deuda-Ingreso (DTI) Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Carga de Deuda Mensual (DTI)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    state.totalIncomesThisMonth <= 0 -> "Registra ingresos para calcular tu salud"
                                    dtiRatio <= 0.3 -> "Nivel Saludable"
                                    dtiRatio <= 0.4 -> "Nivel Moderado"
                                    else -> "Carga de deudas muy alta"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    state.totalIncomesThisMonth <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                    dtiRatio <= 0.3 -> TrafficGreen
                                    dtiRatio <= 0.4 -> TrafficYellow
                                    else -> TrafficRed
                                }
                            )
                        }
                        Text(
                            text = if (state.totalIncomesThisMonth > 0) "${(dtiRatio * 100).toInt()}%" else "--",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                state.totalIncomesThisMonth <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                                dtiRatio <= 0.3 -> TrafficGreen
                                dtiRatio <= 0.4 -> TrafficYellow
                                else -> TrafficRed
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { if (state.totalIncomesThisMonth > 0) dtiRatio.coerceIn(0.0, 1.0).toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = when {
                            state.totalIncomesThisMonth <= 0 -> MaterialTheme.colorScheme.outlineVariant
                            dtiRatio <= 0.3 -> TrafficGreen
                            dtiRatio <= 0.4 -> TrafficYellow
                            else -> TrafficRed
                        },
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "La relación deuda-ingreso ideal debe ser menor al 30%. Si supera el 40%, estás en una zona de riesgo financiero.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Desglose de Obligaciones Comprometidas
        item {
            Text(
                "Desglose de Egresos Comprometidos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cuotas de Tarjetas de Crédito", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(formatCOP(state.totalCreditCardInstallments), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Cuotas de Créditos / Préstamos", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(formatCOP(state.totalLoanInstallments), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // Historial de Ingresos del Mes
        item {
            Text(
                "Ingresos de este Mes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.monthlyIncomes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No has registrado ingresos este mes",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Presiona el botón '+' para agregar un ingreso manual (salario, arriendo, transferencias).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(state.monthlyIncomes, key = { it.transaction.id }) { item ->
                IncomeItemRow(item = item)
            }
        }
    }
}

@Composable
private fun IncomeItemRow(item: TransactionWithCategory) {
    val date = Instant.ofEpochMilli(item.transaction.timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    val formattedDate = date.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TrafficGreen.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = TrafficGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.transaction.merchant ?: "Ingreso",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "• ${item.accountName ?: "Sin cuenta"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            AmountText(
                amount = item.transaction.amount,
                isIncome = true,
                fontSize = 16
            )
        }
    }
}

@Composable
private fun AssetsTabContent(
    state: BalanceUiState,
    onAddAssetType: (AssetType) -> Unit,
    onEditAsset: (AssetEntity) -> Unit,
    onDeleteAsset: (AssetEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            SectionTitle("Patrimonio y Activos")
        }

        // Card de Resumen de Patrimonio Total
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Patrimonio Total Estimado",
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        formatCOP(state.totalAssets),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Cuentas Liquidables (Ahorros y Billeteras)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Efectivo y Cuentas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Saldo combinado de ahorros/billeteras",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = formatCOP(state.liquidCash),
                        modifier = Modifier.widthIn(min = 112.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // Título de Activos Personalizados
        item {
            Text(
                text = "Mis Activos e Inversiones",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (state.assets.isEmpty()) {
            item {
                EmptyAssetTypes(onAddAssetType = onAddAssetType)
            }
        } else {
            items(state.assets, key = { it.id }) { asset ->
                AssetItem(
                    asset = asset,
                    onEdit = { onEditAsset(asset) },
                    onDelete = { onDeleteAsset(asset) }
                )
            }
        }
    }
}

@Composable
private fun EmptyAssetTypes(
    onAddAssetType: (AssetType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Agrega un activo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        AssetType.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { type ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 108.dp)
                            .clickable { onAddAssetType(type) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = assetTypeIcon(type),
                                contentDescription = null,
                                tint = assetTypeColor(type),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = assetTypeLabel(type),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AssetItem(
    asset: AssetEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val icon = assetTypeIcon(asset.type)
    val color = assetTypeColor(asset.type)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = assetTypeLabel(asset.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatCOP(asset.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AssetTypeActionSheet(
    onDismiss: () -> Unit,
    onSelect: (AssetType) -> Unit
) {
    ActionSheet(
        title = "Agregar activo",
        onDismiss = onDismiss
    ) {
        AssetType.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(type) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = assetTypeIcon(type),
                    contentDescription = null,
                    tint = assetTypeColor(type),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = assetTypeLabel(type),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun AddAssetSheet(
    category: AssetType,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, type: AssetType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val amountVal = parseMoneyInput(amount)
    val defaultName = assetTypeLabel(category)

    ActionSheet(
        title = "Registrar $defaultName",
        onDismiss = onDismiss
    ) {
        MoneyInputField(
            value = amount,
            onValueChange = { amount = it },
            label = "Valor",
            isError = amount.isNotBlank() && amountVal == null,
            supportingText = if (amount.isNotBlank() && amountVal == null) "Monto inválido" else null
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            placeholder = { Text(defaultName) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        SheetActions(
            primaryLabel = "Guardar",
            canSubmit = amountVal != null && amountVal > 0.0,
            onDismiss = onDismiss,
            onSubmit = {
                onConfirm(name.trim().ifBlank { defaultName }, amountVal ?: return@SheetActions, category)
            }
        )
    }
}

@Composable
private fun EditAssetSheet(
    asset: AssetEntity,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, type: AssetType) -> Unit
) {
    var name by remember(asset.id) { mutableStateOf(asset.name) }
    var amount by remember(asset.id) { mutableStateOf(formatEditableAmount(asset.amount)) }
    var selectedType by remember(asset.id) { mutableStateOf(asset.type) }
    var typeSheetOpen by remember { mutableStateOf(false) }
    val amountVal = parseMoneyInput(amount)

    ActionSheet(
        title = "Editar activo",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        MoneyInputField(
            value = amount,
            onValueChange = { amount = it },
            label = "Valor",
            isError = amount.isNotBlank() && amountVal == null,
            supportingText = if (amount.isNotBlank() && amountVal == null) "Monto inválido" else null
        )
        OutlinedButton(
            onClick = { typeSheetOpen = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(assetTypeIcon(selectedType), contentDescription = null, tint = assetTypeColor(selectedType))
            Spacer(Modifier.width(8.dp))
            Text(assetTypeLabel(selectedType), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        SheetActions(
            primaryLabel = "Guardar",
            canSubmit = amountVal != null && amountVal > 0.0,
            onDismiss = onDismiss,
            onSubmit = {
                val defaultName = assetTypeLabel(selectedType)
                onConfirm(name.trim().ifBlank { defaultName }, amountVal ?: return@SheetActions, selectedType)
            }
        )
    }

    if (typeSheetOpen) {
        QuickSelectSheet(
            title = "Tipo de activo",
            options = assetTypeOptions(),
            selectedValue = selectedType,
            onDismiss = { typeSheetOpen = false },
            onSelect = { selectedType = it }
        )
    }
}

@Composable
private fun AddIncomeSheet(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (description: String, amount: Double, accountId: String, categoryId: String?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val amountVal = parseMoneyInput(amount)
    val depositAccounts = remember(accounts) {
        accounts.filter { it.type != com.ivan.finanzapp.domain.model.AccountType.TARJETA_CREDITO }
    }
    var selectedAccountId by remember(depositAccounts) { mutableStateOf(depositAccounts.firstOrNull()?.id) }
    val defaultCategory = categories.find { it.id == "cat_ingresos" }
    var selectedCategoryId by remember(categories) { mutableStateOf(defaultCategory?.id ?: categories.firstOrNull()?.id) }
    var accountSheetOpen by remember { mutableStateOf(false) }
    var categorySheetOpen by remember { mutableStateOf(false) }
    val selectedAccount = depositAccounts.firstOrNull { it.id == selectedAccountId }
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }

    ActionSheet(
        title = "Registrar ingreso",
        onDismiss = onDismiss
    ) {
        MoneyInputField(
            value = amount,
            onValueChange = { amount = it },
            label = "Valor",
            isError = amount.isNotBlank() && amountVal == null,
            supportingText = if (amount.isNotBlank() && amountVal == null) "Monto inválido" else null
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Fuente") },
            placeholder = { Text("Salario, arriendo, transferencia") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        SelectButton(
            label = "Cuenta",
            value = selectedAccount?.name ?: "Seleccionar cuenta",
            icon = Icons.Default.AccountBalanceWallet,
            onClick = { accountSheetOpen = true }
        )
        SelectButton(
            label = "Categoría",
            value = selectedCategory?.name ?: "Sin categoría",
            icon = Icons.Default.Category,
            onClick = { categorySheetOpen = true }
        )
        SheetActions(
            primaryLabel = "Registrar",
            canSubmit = amountVal != null && amountVal > 0.0 && selectedAccountId != null,
            onDismiss = onDismiss,
            onSubmit = {
                onConfirm(
                    description.trim().ifBlank { "Ingreso manual" },
                    amountVal ?: return@SheetActions,
                    selectedAccountId ?: return@SheetActions,
                    selectedCategoryId
                )
            }
        )
    }

    if (accountSheetOpen) {
        QuickSelectSheet(
            title = "Cuenta",
            options = depositAccounts.map { account ->
                QuickSelectOption(
                    value = account.id,
                    title = account.name,
                    subtitle = account.type.displayName,
                    icon = Icons.Default.AccountBalanceWallet
                )
            },
            selectedValue = selectedAccountId,
            onDismiss = { accountSheetOpen = false },
            onSelect = { selectedAccountId = it }
        )
    }

    if (categorySheetOpen) {
        QuickSelectSheet(
            title = "Categoría",
            options = listOf(
                QuickSelectOption<String?>(
                    value = null,
                    title = "Sin categoría",
                    icon = Icons.Default.Clear
                )
            ) + categories.map { category ->
                QuickSelectOption<String?>(
                    value = category.id,
                    title = category.name,
                    color = runCatching { Color(android.graphics.Color.parseColor(category.color)) }
                        .getOrDefault(Color.Gray)
                )
            },
            selectedValue = selectedCategoryId,
            onDismiss = { categorySheetOpen = false },
            onSelect = { selectedCategoryId = it }
        )
    }
}

@Composable
private fun SelectButton(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
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
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        ) {
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

private fun assetTypeOptions(): List<QuickSelectOption<AssetType>> {
    return AssetType.entries.map { type ->
        QuickSelectOption(
            value = type,
            title = assetTypeLabel(type),
            icon = assetTypeIcon(type),
            color = assetTypeColor(type)
        )
    }
}

private fun assetTypeLabel(type: AssetType): String {
    return when (type) {
        AssetType.INVERSION -> "Inversión"
        AssetType.INMUEBLE -> "Inmueble"
        AssetType.VEHICULO -> "Vehículo"
        AssetType.OTRO -> "Otro"
    }
}

private fun assetTypeIcon(type: AssetType): ImageVector {
    return when (type) {
        AssetType.INVERSION -> Icons.AutoMirrored.Filled.TrendingUp
        AssetType.INMUEBLE -> Icons.Default.Home
        AssetType.VEHICULO -> Icons.Default.Build
        AssetType.OTRO -> Icons.Default.Folder
    }
}

private fun assetTypeColor(type: AssetType): Color {
    return when (type) {
        AssetType.INVERSION -> Color(0xFF1976D2)
        AssetType.INMUEBLE -> Color(0xFF7B1FA2)
        AssetType.VEHICULO -> Color(0xFFE64A19)
        AssetType.OTRO -> Color(0xFF616161)
    }
}
