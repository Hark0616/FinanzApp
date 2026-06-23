package com.ivan.finanzapp.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AccountEntity
import com.ivan.finanzapp.data.local.entity.CategoryEntity
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.components.AmountText
import com.ivan.finanzapp.ui.components.CategoryChip
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.dashboard.TransactionWithCategory
import com.ivan.finanzapp.ui.theme.TrafficYellow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class TransactionFilter {
    ALL, PENDING_REVIEW, UNCLASSIFIED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    action: String? = null,
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(
        if (action == "view_unclassified") TransactionFilter.UNCLASSIFIED else TransactionFilter.ALL
    ) }

    var selectedTransactionForEdit by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var categoryDialogExpanded by remember { mutableStateOf(false) }

    var addTransactionDialogVisible by remember { mutableStateOf(action == "add_expense") }

    val filteredTransactions = remember(state.transactions, searchQuery, activeFilter) {
        state.transactions.filter { item ->
            val matchesSearch = item.transaction.merchant?.contains(searchQuery, ignoreCase = true) == true ||
                    item.category?.name?.contains(searchQuery, ignoreCase = true) == true ||
                    item.accountName?.contains(searchQuery, ignoreCase = true) == true

            val matchesFilter = when (activeFilter) {
                TransactionFilter.ALL -> true
                TransactionFilter.PENDING_REVIEW -> item.transaction.needsReview
                TransactionFilter.UNCLASSIFIED -> item.transaction.accountId == null
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addTransactionDialogVisible = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Registrar Gasto")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Buscador y filtros
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionTitle("Movimientos")

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar comercio, categoría o cuenta...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Filtros rápidos
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeFilter == TransactionFilter.ALL,
                        onClick = { activeFilter = TransactionFilter.ALL },
                        label = { Text("Todos") },
                        leadingIcon = if (activeFilter == TransactionFilter.ALL) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = activeFilter == TransactionFilter.PENDING_REVIEW,
                        onClick = { activeFilter = TransactionFilter.PENDING_REVIEW },
                        label = { Text("Revisión IA") },
                        leadingIcon = if (activeFilter == TransactionFilter.PENDING_REVIEW) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            { Icon(Icons.Default.Warning, null, tint = TrafficYellow, modifier = Modifier.size(16.dp)) }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = activeFilter == TransactionFilter.UNCLASSIFIED,
                        onClick = { activeFilter = TransactionFilter.UNCLASSIFIED },
                        label = { Text("Pendientes de cuenta") },
                        leadingIcon = if (activeFilter == TransactionFilter.UNCLASSIFIED) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            { Icon(Icons.Default.HelpOutline, null, modifier = Modifier.size(16.dp)) }
                        }
                    )
                }
            }

            // Lista de transacciones
            if (filteredTransactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No se encontraron movimientos",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(filteredTransactions, key = { it.transaction.id }) { item ->
                        TransactionItemRow(
                            item = item,
                            onClick = {
                                selectedTransactionForEdit = item
                                categoryDialogExpanded = true
                            },
                            onConfirmClick = {
                                viewModel.confirmTransaction(item.transaction.id)
                            }
                        )
                    }
                }
            }
        }

        // Diálogo para administrar movimiento (cambiar de categoría, asociar cuenta o eliminar)
        if (categoryDialogExpanded && selectedTransactionForEdit != null) {
            val transactionItem = state.transactions.find { it.transaction.id == selectedTransactionForEdit?.transaction?.id }
            if (transactionItem == null) {
                categoryDialogExpanded = false
                selectedTransactionForEdit = null
            } else {
                AlertDialog(
                    onDismissRequest = {
                        categoryDialogExpanded = false
                        selectedTransactionForEdit = null
                    },
                    title = { Text("Administrar Movimiento") },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Comercio: ${transactionItem.transaction.merchant ?: "Desconocido"}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Monto: ${formatCOP(transactionItem.transaction.amount)} (${transactionItem.transaction.type.name})"
                            )
                            Text(
                                "Cuenta actual: ${transactionItem.accountName ?: "Sin asignar (Pendiente)"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (transactionItem.transaction.accountId == null) MaterialTheme.colorScheme.error else Color.Gray
                            )

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            // Sección de Cuenta
                            Text("Asociar Cuenta:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                item {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateTransactionAccount(transactionItem.transaction.id, null)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Sin cuenta (Pendiente)", fontSize = 14.sp)
                                        if (transactionItem.transaction.accountId == null) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                                items(state.accounts) { account ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateTransactionAccount(transactionItem.transaction.id, account.id)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(account.name, fontSize = 14.sp)
                                        if (account.id == transactionItem.transaction.accountId) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // Sección de Categoría
                            Text("Seleccionar Categoría:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(4.dp)
                            ) {
                                item {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateTransactionCategory(transactionItem.transaction.id, null)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Sin categoría", fontSize = 14.sp)
                                        if (transactionItem.transaction.categoryId == null) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                                items(state.categories) { category ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.updateTransactionCategory(
                                                    transactionItem.transaction.id,
                                                    category.id
                                                )
                                            }
                                            .padding(vertical = 8.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(
                                                    color = runCatching { Color(android.graphics.Color.parseColor(category.color)) }
                                                        .getOrDefault(Color.Gray),
                                                    shape = RoundedCornerShape(50)
                                                )
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(category.name, fontSize = 14.sp)
                                        if (category.id == transactionItem.transaction.categoryId) {
                                            Spacer(Modifier.weight(1f))
                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.deleteTransaction(transactionItem.transaction.id)
                                categoryDialogExpanded = false
                                selectedTransactionForEdit = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(4.dp))
                            Text("Eliminar")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                categoryDialogExpanded = false
                                selectedTransactionForEdit = null
                            }
                        ) {
                            Text("Cerrar")
                        }
                    }
                )
            }
        }

        // Diálogo para registrar gasto manual
        if (addTransactionDialogVisible) {
            AddTransactionDialog(
                accounts = state.accounts,
                categories = state.categories,
                onDismiss = { addTransactionDialogVisible = false },
                onConfirm = { amount, merchant, typeStr, accountId, categoryId ->
                    viewModel.addManualTransaction(amount, merchant, typeStr, accountId, categoryId)
                }
            )
        }
    }
}

@Composable
fun AddTransactionDialog(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, merchant: String, typeStr: String, accountId: String?, categoryId: String?) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Gasto") } // "Gasto" o "Ingreso"
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Movimiento", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(type)
                        }
                    }
                }

                // Campo de Monto
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Monto ($)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
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
                Text("Cuenta:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { accountDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(currentAccountName)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = accountDropdownExpanded,
                        onDismissRequest = { accountDropdownExpanded = false }
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

                // Selector de Categoría
                val currentCategoryName = selectedCategoryId?.let { id -> categories.find { it.id == id }?.name } ?: "Sin categoría"
                Text("Categoría:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { categoryDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(currentCategoryName)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount > 0 && merchant.isNotBlank()) {
                        onConfirm(amount, merchant, selectedType, selectedAccountId, selectedCategoryId)
                        onDismiss()
                    }
                },
                enabled = amountStr.isNotBlank() && merchant.isNotBlank()
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

@Composable
private fun TransactionItemRow(
    item: TransactionWithCategory,
    onClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val isIncome = item.transaction.type == TransactionType.INGRESO || item.transaction.type == TransactionType.PAGO_TC
    val date = Instant.ofEpochMilli(item.transaction.timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()

    val formattedDate = date.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a"))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.transaction.merchant ?: "Transacción",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.category != null) {
                        CategoryChip(name = item.category.name, colorHex = item.category.color)
                    }
                    Text(
                        item.accountName ?: "Sin clasificar (Cuenta)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.transaction.accountId == null) MaterialTheme.colorScheme.error else Color.Gray,
                        fontWeight = if (item.transaction.accountId == null) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amount = item.transaction.amount,
                    isIncome = isIncome,
                    fontSize = 16
                )
                Text(
                    formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }

        // Mostrar alerta de que no tiene cuenta asignada
        if (item.transaction.accountId == null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HelpOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Pendiente por clasificar (Sin Cuenta)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Text(
                    "Asociar cuenta",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onClick() }
                )
            }
        }

        // Mostrar botones de acción si requiere revisión
        if (item.transaction.needsReview) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrafficYellow.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = TrafficYellow, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Clasificado por IA - Necesita revisión",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Button(
                    onClick = onConfirmClick,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Confirmar", fontSize = 11.sp)
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
