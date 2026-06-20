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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.entity.AssetType
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.ui.components.AmountText
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
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

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Flujo de Caja", "Patrimonio (Activos)")

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                // FAB contextual para Ingresos: Botón "+" simple que abre el diálogo de registro manual
                FloatingActionButton(
                    onClick = { isAddIncomeDialogVisible = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar Ingreso",
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // FAB contextual para Activos: El Speed Dial animado y centrado
                var menuExpanded by remember { mutableStateOf(false) }
                
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Contenedor de botones individuales con animación en cascada
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        AssetType.entries.forEachIndexed { index, type ->
                            val (icon, color, label) = when (type) {
                                AssetType.INVERSION -> Triple(Icons.AutoMirrored.Filled.TrendingUp, Color(0xFF1976D2), "Inversión")
                                AssetType.INMUEBLE -> Triple(Icons.Default.Home, Color(0xFF7B1FA2), "Inmueble")
                                AssetType.VEHICULO -> Triple(Icons.Default.Build, Color(0xFFE64A19), "Vehículo")
                                AssetType.OTRO -> Triple(Icons.Default.Folder, Color(0xFF616161), "Otro Activo")
                            }

                            // Delay progresivo para efecto de cascada
                            val delay = (AssetType.entries.size - 1 - index) * 100

                            AnimatedVisibility(
                                visible = menuExpanded,
                                modifier = Modifier.align(Alignment.End),
                                enter = fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 350,
                                        delayMillis = delay,
                                        easing = FastOutSlowInEasing
                                    )
                                ) + slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = tween(
                                        durationMillis = 350,
                                        delayMillis = delay,
                                        easing = FastOutSlowInEasing
                                    )
                                ),
                                exit = fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutLinearInEasing
                                    )
                                ) + slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(
                                        durationMillis = 200,
                                        easing = FastOutLinearInEasing
                                    )
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            selectedCategoryForAdd = type
                                            menuExpanded = false
                                        }
                                ) {
                                    // Etiqueta de texto sutil flotante
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Botón circular del mismo tamaño que el principal, color sólido e icono blanco
                                    FloatingActionButton(
                                        onClick = {
                                            selectedCategoryForAdd = type
                                            menuExpanded = false
                                        },
                                        containerColor = color,
                                        contentColor = Color.White,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Botón principal rotatorio (+ / X)
                    val rotation by animateFloatAsState(
                        targetValue = if (menuExpanded) 45f else 0f,
                        label = "Rotation"
                    )
                    
                    FloatingActionButton(
                        onClick = { menuExpanded = !menuExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(56.dp)
                            .rotate(rotation)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Agregar Activo",
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
                        state = state,
                        onAddIncomeClick = { isAddIncomeDialogVisible = true }
                    )
                } else {
                    // Vista 2: Patrimonio e Inmuebles (Activos)
                    AssetsTabContent(
                        state = state,
                        onEditAsset = { editingAsset = it },
                        onDeleteAsset = { viewModel.deleteAsset(it.id) }
                    )
                }
            }
        }

        // Diálogos
        if (isAddIncomeDialogVisible) {
            AddIncomeDialog(
                accounts = state.accounts,
                categories = state.categories,
                onDismiss = { isAddIncomeDialogVisible = false },
                onConfirm = { description, amount, accountId, categoryId ->
                    viewModel.addIncomeTransaction(description, amount, accountId, categoryId)
                    isAddIncomeDialogVisible = false
                }
            )
        }

        selectedCategoryForAdd?.let { category ->
            AddAssetDialog(
                category = category,
                onDismiss = { selectedCategoryForAdd = null },
                onConfirm = { name, amount, type ->
                    viewModel.addAsset(name, amount, type)
                    selectedCategoryForAdd = null
                }
            )
        }

        editingAsset?.let { asset ->
            EditAssetDialog(
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
    state: BalanceUiState,
    onAddIncomeClick: () -> Unit
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Efectivo y Cuentas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Saldo combinado de ahorros/billeteras",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = formatCOP(state.liquidCash),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No tienes activos personalizados",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Agrega tus inversiones en CDT, inmuebles o vehículos presionando el botón '+'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
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
private fun AssetItem(
    asset: AssetEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, color) = when (asset.type) {
        AssetType.INVERSION -> Icons.AutoMirrored.Filled.TrendingUp to Color(0xFF1976D2)
        AssetType.INMUEBLE -> Icons.Default.Home to Color(0xFF7B1FA2)
        AssetType.VEHICULO -> Icons.Default.Build to Color(0xFFE64A19)
        AssetType.OTRO -> Icons.Default.Folder to Color(0xFF616161)
    }

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
                        text = when (asset.type) {
                            AssetType.INVERSION -> "Inversión"
                            AssetType.INMUEBLE -> "Inmueble"
                            AssetType.VEHICULO -> "Vehículo"
                            AssetType.OTRO -> "Otro Activo"
                        },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAssetDialog(
    category: AssetType,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, type: AssetType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    val defaultName = when (category) {
        AssetType.INVERSION -> "Inversión"
        AssetType.INMUEBLE -> "Inmueble"
        AssetType.VEHICULO -> "Vehículo"
        AssetType.OTRO -> "Otro Activo"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar $defaultName") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ingresa el valor del activo. El nombre es opcional.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor / Monto ($)") },
                    placeholder = { Text("Ej. 1.500.000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (Opcional)") },
                    placeholder = { Text("Por defecto: $defaultName") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            val amountVal = amount.toDoubleOrNull() ?: 0.0
            val isValid = amountVal > 0.0

            Button(
                onClick = { 
                    val finalName = name.trim().ifBlank { defaultName }
                    onConfirm(finalName, amountVal, category) 
                },
                enabled = isValid
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAssetDialog(
    asset: AssetEntity,
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, type: AssetType) -> Unit
) {
    var name by remember { mutableStateOf(asset.name) }
    var amount by remember { mutableStateOf(asset.amount.toString()) }
    var selectedType by remember { mutableStateOf(asset.type) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Activo") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (Opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor / Monto ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = when (selectedType) {
                            AssetType.INVERSION -> "Inversión"
                            AssetType.INMUEBLE -> "Inmueble"
                            AssetType.VEHICULO -> "Vehículo"
                            AssetType.OTRO -> "Otro Activo"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de Activo") },
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
                        AssetType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            AssetType.INVERSION -> "Inversión"
                                            AssetType.INMUEBLE -> "Inmueble"
                                            AssetType.VEHICULO -> "Vehículo"
                                            AssetType.OTRO -> "Otro Activo"
                                        }
                                    )
                                },
                                onClick = {
                                    selectedType = type
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amountVal = amount.toDoubleOrNull() ?: 0.0
            val isValid = amountVal > 0.0

            Button(
                onClick = { 
                    val defaultName = when (selectedType) {
                        AssetType.INVERSION -> "Inversión"
                        AssetType.INMUEBLE -> "Inmueble"
                        AssetType.VEHICULO -> "Vehículo"
                        AssetType.OTRO -> "Otro Activo"
                    }
                    val finalName = name.trim().ifBlank { defaultName }
                    onConfirm(finalName, amountVal, selectedType) 
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddIncomeDialog(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (description: String, amount: Double, accountId: String, categoryId: String?) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    val defaultCategory = categories.find { it.id == "cat_ingresos" }
    var selectedCategory by remember { mutableStateOf<CategoryEntity?>(defaultCategory ?: categories.firstOrNull()) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // Filtrar cuentas para depositar (no tarjetas de crédito)
    val depositAccounts = remember(accounts) {
        accounts.filter { it.type != com.ivan.finanzapp.domain.model.AccountType.TARJETA_CREDITO }
    }
    var selectedAccount by remember { mutableStateOf<AccountEntity?>(depositAccounts.firstOrNull()) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Ingreso Manual") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Registra un ingreso que hayas recibido. Este movimiento se guardará en tu historial y sumará al saldo de la cuenta elegida.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Valor / Monto ($)") },
                    placeholder = { Text("Ej. 1.200.000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Fuente / Descripción") },
                    placeholder = { Text("Ej. Salario, Rendimientos, Pago de amigo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Dropdown de Cuenta de Destino
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedAccount?.name ?: "Seleccionar cuenta",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Cuenta de Destino") },
                        trailingIcon = {
                            IconButton(onClick = { accountDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { accountDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        depositAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccount = account
                                    accountDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Dropdown de Categoría
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "Seleccionar categoría",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = {
                            IconButton(onClick = { categoryDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryDropdownExpanded = true }
                    )

                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val amountVal = amount.toDoubleOrNull() ?: 0.0
            val isValid = amountVal > 0.0 && selectedAccount != null

            Button(
                onClick = { 
                    val finalDescription = description.trim().ifBlank { "Ingreso manual" }
                    onConfirm(finalDescription, amountVal, selectedAccount!!.id, selectedCategory?.id) 
                },
                enabled = isValid
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
