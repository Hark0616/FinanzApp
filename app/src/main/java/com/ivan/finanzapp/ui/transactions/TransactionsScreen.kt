package com.ivan.finanzapp.ui.transactions

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var filterPendingOnly by remember { mutableStateOf(false) }

    var selectedTransactionForEdit by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var categoryDialogExpanded by remember { mutableStateOf(false) }

    val filteredTransactions = remember(state.transactions, searchQuery, filterPendingOnly) {
        state.transactions.filter { item ->
            val matchesSearch = item.transaction.merchant?.contains(searchQuery, ignoreCase = true) == true ||
                    item.category?.name?.contains(searchQuery, ignoreCase = true) == true
            val matchesPending = !filterPendingOnly || item.transaction.needsReview
            matchesSearch && matchesPending
        }
    }

    Scaffold { innerPadding ->
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
                    label = { Text("Buscar comercio o categoría...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Filtro rápido para transacciones pendientes de revisión
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !filterPendingOnly,
                        onClick = { filterPendingOnly = false },
                        label = { Text("Todos") },
                        leadingIcon = if (!filterPendingOnly) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = filterPendingOnly,
                        onClick = { filterPendingOnly = true },
                        label = { Text("Pendientes de revisión") },
                        leadingIcon = if (filterPendingOnly) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else {
                            { Icon(Icons.Default.Warning, null, tint = TrafficYellow, modifier = Modifier.size(16.dp)) }
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

        // Diálogo para cambiar de categoría o eliminar transacción
        if (categoryDialogExpanded && selectedTransactionForEdit != null) {
            val transactionItem = selectedTransactionForEdit!!
            AlertDialog(
                onDismissRequest = {
                    categoryDialogExpanded = false
                    selectedTransactionForEdit = null
                },
                title = { Text("Administrar Movimiento") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            "Cuenta: ${transactionItem.accountName ?: "Sin asignar"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(8.dp))

                        Text("Seleccionar Categoría:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                        // Lista rápida de categorías
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            item {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.updateTransactionCategory(transactionItem.transaction.id, null)
                                            categoryDialogExpanded = false
                                            selectedTransactionForEdit = null
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Clear, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Sin categoría")
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
                                            categoryDialogExpanded = false
                                            selectedTransactionForEdit = null
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
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
                                    Text(category.name)
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
}

@Composable
private fun TransactionItemRow(
    item: TransactionWithCategory,
    onClick: () -> Unit,
    onConfirmClick: () -> Unit
) {
    val isIncome = item.transaction.type == TransactionType.INGRESO
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
                        item.accountName ?: "Sin cuenta",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
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
