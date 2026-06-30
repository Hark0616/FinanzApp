package com.ivan.finanzapp.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
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
import com.ivan.finanzapp.data.local.entity.PaymentMatchSuggestionEntity
import com.ivan.finanzapp.data.local.entity.PaymentMatchTargetType
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.components.ActionSheet
import com.ivan.finanzapp.ui.components.AmountText
import com.ivan.finanzapp.ui.components.CategoryChip
import com.ivan.finanzapp.ui.components.MoneyInputField
import com.ivan.finanzapp.ui.components.OverflowActionMenu
import com.ivan.finanzapp.ui.components.OverflowMenuAction
import com.ivan.finanzapp.ui.components.ProgressiveFormSheet
import com.ivan.finanzapp.ui.components.QuickSelectOption
import com.ivan.finanzapp.ui.components.QuickSelectSheet
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP
import com.ivan.finanzapp.ui.components.parseMoneyInput
import com.ivan.finanzapp.ui.dashboard.TransactionWithCategory
import com.ivan.finanzapp.ui.theme.TrafficYellow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class TransactionFilter {
    ALL, PENDING_REVIEW, UNCLASSIFIED
}

private val TransactionFilter.label: String
    get() = when (this) {
        TransactionFilter.ALL -> "Todos"
        TransactionFilter.PENDING_REVIEW -> "Revisar"
        TransactionFilter.UNCLASSIFIED -> "Sin cuenta"
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

    var selectedTransactionForReview by remember { mutableStateOf<TransactionWithCategory?>(null) }

    var addTransactionDialogVisible by remember { mutableStateOf(action == "add_expense") }

    val filteredTransactions = remember(
        state.transactions,
        state.pendingPaymentSuggestions,
        searchQuery,
        activeFilter
    ) {
        state.transactions.filter { item ->
            val matchesSearch = item.transaction.merchant?.contains(searchQuery, ignoreCase = true) == true ||
                    item.category?.name?.contains(searchQuery, ignoreCase = true) == true ||
                    item.accountName?.contains(searchQuery, ignoreCase = true) == true
            val hasPaymentSuggestion = state.pendingPaymentSuggestions[item.transaction.id].orEmpty().isNotEmpty()

            val matchesFilter = when (activeFilter) {
                TransactionFilter.ALL -> true
                TransactionFilter.PENDING_REVIEW -> item.transaction.needsReview || hasPaymentSuggestion
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

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TransactionFilter.entries.forEachIndexed { index, filter ->
                        SegmentedButton(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TransactionFilter.entries.size
                            ),
                            label = { Text(filter.label, maxLines = 1) }
                        )
                    }
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
                            paymentSuggestions = state.pendingPaymentSuggestions[item.transaction.id].orEmpty(),
                            onClick = {
                                selectedTransactionForReview = item
                            },
                            onConfirmClick = {
                                viewModel.confirmTransaction(item.transaction.id)
                            },
                            onApplyPaymentSuggestion = viewModel::acceptPaymentSuggestion,
                            onRejectPaymentSuggestion = viewModel::rejectPaymentSuggestion
                        )
                    }
                }
            }
        }

        selectedTransactionForReview?.let { selected ->
            val transactionItem = state.transactions.find { it.transaction.id == selected.transaction.id }
            if (transactionItem == null) {
                selectedTransactionForReview = null
            } else {
                TransactionReviewSheet(
                    item = transactionItem,
                    accounts = state.accounts,
                    categories = state.categories,
                    paymentSuggestions = state.pendingPaymentSuggestions[transactionItem.transaction.id].orEmpty(),
                    onDismiss = { selectedTransactionForReview = null },
                    onConfirm = {
                        viewModel.confirmTransaction(transactionItem.transaction.id)
                        selectedTransactionForReview = null
                    },
                    onChangeAccount = { accountId ->
                        viewModel.updateTransactionAccount(transactionItem.transaction.id, accountId)
                    },
                    onChangeCategory = { categoryId ->
                        viewModel.updateTransactionCategory(transactionItem.transaction.id, categoryId)
                    },
                    onDelete = {
                        viewModel.deleteTransaction(transactionItem.transaction.id)
                        selectedTransactionForReview = null
                    },
                    onApplyPaymentSuggestion = viewModel::acceptPaymentSuggestion,
                    onRejectPaymentSuggestion = viewModel::rejectPaymentSuggestion
                )
            }
        }

        // Sheet para registrar gasto manual
        if (addTransactionDialogVisible) {
            MoneyInputSheet(
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
fun MoneyInputSheet(
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

    var accountSheetOpen by remember { mutableStateOf(false) }
    var categorySheetOpen by remember { mutableStateOf(false) }
    val parsedAmount = parseMoneyInput(amountStr)
    val currentAccountName = selectedAccountId?.let { id ->
        accounts.find { it.id == id }?.name
    } ?: "Sin asignar"
    val currentCategoryName = selectedCategoryId?.let { id ->
        categories.find { it.id == id }?.name
    } ?: "Sin categoría"

    ProgressiveFormSheet(
        title = "Registrar Movimiento",
        subtitle = "Captura rápida con cuenta y categoría opcionales.",
        onDismiss = onDismiss,
        primaryActionLabel = "Registrar",
        canSubmit = (parsedAmount ?: 0.0) > 0.0 && merchant.isNotBlank(),
        onSubmit = {
            val amount = parsedAmount ?: return@ProgressiveFormSheet
            onConfirm(amount, merchant, selectedType, selectedAccountId, selectedCategoryId)
            onDismiss()
        }
    ) {
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
                MoneyInputField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = "Monto",
                    isError = amountStr.isNotBlank() && parsedAmount == null
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
                Text("Cuenta:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = { accountSheetOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(currentAccountName)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                if (accountSheetOpen) {
                    QuickSelectSheet(
                        title = "Cuenta",
                        subtitle = "Dónde se reflejó el movimiento",
                        options = accountQuickSelectOptions(
                            accounts = accounts,
                            emptyTitle = "Sin asignar",
                            emptySubtitle = "Queda para revisión"
                        ),
                        selectedValue = selectedAccountId,
                        onDismiss = { accountSheetOpen = false },
                        onSelect = { selectedAccountId = it }
                    )
                }

                // Selector de Categoría
                Text("Categoría:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(
                    onClick = { categorySheetOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(currentCategoryName)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                if (categorySheetOpen) {
                    QuickSelectSheet(
                        title = "Categoría",
                        subtitle = "Cómo quieres clasificarlo",
                        options = categoryQuickSelectOptions(
                            categories = categories,
                            emptyTitle = "Sin categoría",
                            emptySubtitle = "No clasificar todavía"
                        ),
                        selectedValue = selectedCategoryId,
                        onDismiss = { categorySheetOpen = false },
                        onSelect = { selectedCategoryId = it }
                    )
                }
            }
    }
}

@Composable
private fun TransactionReviewSheet(
    item: TransactionWithCategory,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    paymentSuggestions: List<PaymentMatchSuggestionEntity>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onChangeAccount: (String?) -> Unit,
    onChangeCategory: (String?) -> Unit,
    onDelete: () -> Unit,
    onApplyPaymentSuggestion: (String) -> Unit,
    onRejectPaymentSuggestion: (String) -> Unit
) {
    var accountSheetOpen by remember { mutableStateOf(false) }
    var categorySheetOpen by remember { mutableStateOf(false) }
    val isIncome = item.transaction.type == TransactionType.INGRESO || item.transaction.type == TransactionType.PAGO_TC

    ActionSheet(
        title = "Revisar movimiento",
        subtitle = "Confirma o corrige lo que la app detectó.",
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.transaction.merchant ?: "Movimiento sin comercio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = item.transaction.type.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AmountText(
                    amount = item.transaction.amount,
                    isIncome = isIncome,
                    fontSize = 18
                )
                Spacer(Modifier.width(4.dp))
                OverflowActionMenu(
                    actions = listOf(
                        OverflowMenuAction(
                            label = "Eliminar",
                            icon = Icons.Default.Delete,
                            destructive = true,
                            onClick = onDelete
                        )
                    )
                )
            }

            ReviewCurrentValue(
                label = "Cuenta actual",
                value = item.accountName ?: "Sin cuenta",
                isMissing = item.transaction.accountId == null
            )
            ReviewCurrentValue(
                label = "Categoría actual",
                value = item.category?.name ?: "Sin categoría",
                isMissing = item.transaction.categoryId == null,
                colorHex = item.category?.color
            )

            if (paymentSuggestions.isNotEmpty()) {
                PaymentSuggestionBlock(
                    suggestions = paymentSuggestions,
                    onApply = onApplyPaymentSuggestion,
                    onIgnore = onRejectPaymentSuggestion
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { accountSheetOpen = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cambiar cuenta")
                }
                OutlinedButton(
                    onClick = { categorySheetOpen = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cambiar categoría")
                }
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Confirmar")
            }
        }
    }

    if (accountSheetOpen) {
        QuickSelectSheet(
            title = "Cuenta",
            subtitle = "Dónde se reflejó el movimiento",
            options = accountQuickSelectOptions(
                accounts = accounts,
                emptyTitle = "Sin cuenta",
                emptySubtitle = "Dejar en revisión"
            ),
            selectedValue = item.transaction.accountId,
            onDismiss = { accountSheetOpen = false },
            onSelect = onChangeAccount
        )
    }

    if (categorySheetOpen) {
        QuickSelectSheet(
            title = "Categoría",
            subtitle = "Cómo quieres clasificarlo",
            options = categoryQuickSelectOptions(
                categories = categories,
                emptyTitle = "Sin categoría",
                emptySubtitle = "No clasificar todavía"
            ),
            selectedValue = item.transaction.categoryId,
            onDismiss = { categorySheetOpen = false },
            onSelect = onChangeCategory
        )
    }
}

@Composable
private fun ReviewCurrentValue(
    label: String,
    value: String,
    isMissing: Boolean,
    colorHex: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (isMissing) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (colorHex != null) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
                                .getOrDefault(Color.Gray),
                            shape = RoundedCornerShape(50)
                        )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMissing) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

private fun accountQuickSelectOptions(
    accounts: List<AccountEntity>,
    emptyTitle: String,
    emptySubtitle: String
): List<QuickSelectOption<String?>> {
    return listOf(
        QuickSelectOption<String?>(
            value = null,
            title = emptyTitle,
            subtitle = emptySubtitle,
            icon = Icons.Default.Clear
        )
    ) + accounts.map { account ->
        QuickSelectOption<String?>(
            value = account.id,
            title = account.name,
            subtitle = account.type.displayName,
            icon = when (account.type) {
                com.ivan.finanzapp.domain.model.AccountType.TARJETA_CREDITO -> Icons.Default.CreditCard
                else -> Icons.Default.AccountBalanceWallet
            }
        )
    }
}

private fun categoryQuickSelectOptions(
    categories: List<CategoryEntity>,
    emptyTitle: String,
    emptySubtitle: String
): List<QuickSelectOption<String?>> {
    return listOf(
        QuickSelectOption<String?>(
            value = null,
            title = emptyTitle,
            subtitle = emptySubtitle,
            icon = Icons.Default.Clear
        )
    ) + categories.map { category ->
        QuickSelectOption<String?>(
            value = category.id,
            title = category.name,
            color = runCatching {
                Color(android.graphics.Color.parseColor(category.color))
            }.getOrDefault(Color.Gray)
        )
    }
}

@Composable
private fun TransactionItemRow(
    item: TransactionWithCategory,
    paymentSuggestions: List<PaymentMatchSuggestionEntity>,
    onClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onApplyPaymentSuggestion: (String) -> Unit,
    onRejectPaymentSuggestion: (String) -> Unit
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
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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

        if (paymentSuggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PaymentSuggestionBlock(
                suggestions = paymentSuggestions,
                onApply = onApplyPaymentSuggestion,
                onIgnore = onRejectPaymentSuggestion
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun PaymentSuggestionBlock(
    suggestions: List<PaymentMatchSuggestionEntity>,
    onApply: (String) -> Unit,
    onIgnore: (String) -> Unit
) {
    val suggestion = suggestions.maxByOrNull { it.confidence } ?: return
    val targetLabel = when (suggestion.targetType) {
        PaymentMatchTargetType.CREDIT_CARD -> "tarjeta"
        PaymentMatchTargetType.LOAN -> "crédito"
    }
    val confidenceLabel = when {
        suggestion.confidence >= 0.94 -> "Coincidencia alta"
        suggestion.confidence >= 0.86 -> "Coincidencia media"
        else -> "Revisar coincidencia"
    }
    val differenceLabel = if (suggestion.differenceAmount < 1.0) {
        "diferencia $0"
    } else {
        "diferencia ${formatCOP(suggestion.differenceAmount)}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when (suggestion.targetType) {
                    PaymentMatchTargetType.CREDIT_CARD -> Icons.Default.CreditCard
                    PaymentMatchTargetType.LOAN -> Icons.Default.AccountBalance
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Conciliación sugerida",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Posible pago de $targetLabel: ${suggestion.targetName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Text(
            "$confidenceLabel · $differenceLabel · esperado ${formatCOP(suggestion.expectedAmount)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        Text(
            suggestion.reason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { onIgnore(suggestion.id) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Ignorar", fontSize = 12.sp)
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { onApply(suggestion.id) },
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Aplicar", fontSize = 12.sp)
            }
        }
    }
}
