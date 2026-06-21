package com.ivan.finanzapp.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ivan.finanzapp.data.local.entity.CustomRuleEntity
import com.ivan.finanzapp.domain.model.BankSource
import com.ivan.finanzapp.domain.model.TransactionType
import com.ivan.finanzapp.ui.components.formatCOP
import java.util.UUID

@Composable
fun SimpleFlowRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        var currentRowWidth = 0
        var currentRowHeight = 0
        var totalHeight = 0
        val rows = mutableListOf<List<Placeable>>()
        val rowHeights = mutableListOf<Int>()
        var currentRow = mutableListOf<Placeable>()
        val spacingPx = spacing.roundToPx()

        for (measurable in measurables) {
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            if (currentRowWidth + placeable.width > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                rowHeights.add(currentRowHeight)
                totalHeight += currentRowHeight + spacingPx
                currentRow = mutableListOf()
                currentRowWidth = 0
                currentRowHeight = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + spacingPx
            currentRowHeight = maxOf(currentRowHeight, placeable.height)
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
            rowHeights.add(currentRowHeight)
            totalHeight += currentRowHeight
        }

        layout(constraints.maxWidth, maxOf(totalHeight, constraints.minHeight)) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + spacingPx
                }
                y += rowHeights[rowIndex] + spacingPx
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRulesDialog(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onSaveRule: (CustomRuleEntity) -> Unit,
    onDeleteRule: (String) -> Unit
) {
    var showTrainer by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reglas Personalizadas",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatiza bancos y mensajes no estándar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content list
                Box(modifier = Modifier.weight(1f)) {
                    if (state.customRules.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Sin reglas personalizadas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Agrega reglas personalizadas entrenando la app con un mensaje de ejemplo. La app aprenderá a extraer el monto y comercio automáticamente.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.customRules, key = { it.id }) { rule ->
                                RuleItemCard(rule = rule, onDeleteClick = { onDeleteRule(rule.id) })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Regresar")
                    }
                    Button(
                        onClick = { showTrainer = true },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Entrenar Regla")
                    }
                }
            }
        }
    }

    if (showTrainer) {
        RuleTrainerDialog(
            onDismiss = { showTrainer = false },
            onSaveRule = { rule ->
                onSaveRule(rule)
                showTrainer = false
            }
        )
    }
}

@Composable
private fun RuleItemCard(
    rule: CustomRuleEntity,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val txFriendly = when (rule.transactionType) {
                        "INGRESO" -> "Ingreso 💵"
                        "GASTO" -> "Gasto 🛒"
                        "GASTO_TC" -> "Compra TC 💳"
                        "TRANSFERENCIA" -> "Transferencia 📤"
                        "PAGO_TC" -> "Pago TC 📥"
                        else -> rule.transactionType
                    }
                    val formatName = when (rule.amountFormatType) {
                        0 -> "LatAm"
                        1 -> "US/Anglo"
                        2 -> "Plano"
                        else -> "Por defecto"
                    }

                    SimpleFlowRow(
                        spacing = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Banco
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = rule.bankSource,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        // Tipo de Transacción
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = txFriendly,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // Formato de Monto
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = formatName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar Regla",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTrainerDialog(
    onDismiss: () -> Unit,
    onSaveRule: (CustomRuleEntity) -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }

    // Inputs
    var rawText by remember { mutableStateOf("") }
    var ruleName by remember { mutableStateOf("") }
    var bankSource by remember { mutableStateOf(BankSource.DESCONOCIDO) }
    var transactionType by remember { mutableStateOf(TransactionType.GASTO) }
    var amountFormatType by remember { mutableStateOf(0) } // 0 = LatAm, 1 = US, 2 = Plano

    // Selection UI: indices in rawText split by space
    val tokens = remember(rawText) { rawText.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() } }
    var selectedAmountIndices by remember(rawText) { mutableStateOf(guessAmountIndex(tokens)) }
    var selectedMerchantIndices by remember(rawText) { mutableStateOf(guessMerchantIndices(rawText, tokens)) }

    // Dropdowns
    var bankExpanded by remember { mutableStateOf(false) }
    var txTypeExpanded by remember { mutableStateOf(false) }

    // Regex and parse output
    val generatedRegex = if (selectedAmountIndices.isEmpty()) ""
    else generateRegexFromTokens(tokens, selectedAmountIndices, selectedMerchantIndices)

    val amountText = selectedAmountIndices.sorted().joinToString(" ") { tokens[it] }
    val merchantText = selectedMerchantIndices.sorted().joinToString(" ") { tokens[it] }

    // Parsed amount in real-time
    val parsedAmount = if (amountText.isBlank()) null
    else when (amountFormatType) {
        0 -> parseLatAmAmount(amountText)
        1 -> parseUSAmount(amountText)
        2 -> parsePlainAmount(amountText)
        else -> null
    }

    // Auto set rule name
    LaunchedEffect(bankSource, transactionType, rawText) {
        if (ruleName.isBlank() || ruleName.startsWith("Regla ")) {
            val bankPart = bankSource.name.lowercase().replaceFirstChar { it.uppercase() }
            val txPart = when(transactionType) {
                TransactionType.INGRESO -> "Ingreso"
                TransactionType.GASTO -> "Gasto"
                TransactionType.GASTO_TC -> "Compra TC"
                TransactionType.TRANSFERENCIA -> "Transferencia"
                TransactionType.PAGO_TC -> "Pago TC"
            }
            ruleName = "Regla $bankPart $txPart"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Title and Step indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Entrenador de Notificaciones",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Paso $currentStep de 4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable container for step contents
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentStep) {
                        1 -> Step1InputMessage(
                            rawText = rawText,
                            onRawTextChange = { rawText = it }
                        )
                        2 -> Step2HighlightTokens(
                            tokens = tokens,
                            selectedAmountIndices = selectedAmountIndices,
                            onAmountIndicesChange = { selectedAmountIndices = it },
                            selectedMerchantIndices = selectedMerchantIndices,
                            onMerchantIndicesChange = { selectedMerchantIndices = it },
                            amountText = amountText,
                            merchantText = merchantText
                        )
                        3 -> Step3Details(
                            ruleName = ruleName,
                            onRuleNameChange = { ruleName = it },
                            bankSource = bankSource,
                            onBankSourceChange = { bankSource = it },
                            bankExpanded = bankExpanded,
                            onBankExpandedChange = { bankExpanded = it },
                            transactionType = transactionType,
                            onTransactionTypeChange = { transactionType = it },
                            txTypeExpanded = txTypeExpanded,
                            onTxTypeExpandedChange = { txTypeExpanded = it }
                        )
                        4 -> Step4PreviewSave(
                            amountText = amountText,
                            merchantText = merchantText,
                            parsedAmount = parsedAmount,
                            amountFormatType = amountFormatType,
                            onFormatTypeChange = { amountFormatType = it },
                            generatedRegex = generatedRegex
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Navigation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Atrás")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    val nextButtonEnabled = when (currentStep) {
                        1 -> rawText.isNotBlank()
                        2 -> selectedAmountIndices.isNotEmpty()
                        3 -> ruleName.isNotBlank()
                        4 -> parsedAmount != null && generatedRegex.isNotBlank()
                        else -> false
                    }

                    Button(
                        onClick = {
                            if (currentStep < 4) {
                                currentStep++
                            } else {
                                // Save
                                val entity = CustomRuleEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = ruleName.trim(),
                                    regexPattern = generatedRegex,
                                    transactionType = transactionType.name,
                                    bankSource = bankSource.name,
                                    amountFormatType = amountFormatType,
                                    createdAt = System.currentTimeMillis()
                                )
                                onSaveRule(entity)
                            }
                        },
                        enabled = nextButtonEnabled,
                        modifier = Modifier.weight(1.5f)
                    ) {
                        if (currentStep < 4) {
                            Text("Siguiente")
                        } else {
                            Text("Guardar Regla")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Step1InputMessage(
    rawText: String,
    onRawTextChange: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = rawText.isBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Paso 1: Copia y pega un mensaje real",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ingresa la notificación tal como llega a tu celular (incluyendo título si es posible). La usaremos como plantilla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Ejemplos rápidos:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onRawTextChange("AVVillas. 19/06/26 20:48 COMPRA CON TU TARJETA CREDITO 5039 POR $ 408,123 EN ALIEXPRESS COM")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("AVVillas TC", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            onRawTextChange("Bancolombia: Compra por $50.000,00 en D1 SAS. 21/06/26 08:12.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bancolombia Gasto", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            onRawTextChange("Davivienda: COMPRA POR USD 15.50 EN AMAZON.COM DE TARJETA 9876.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Davivienda USD", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        OutlinedTextField(
            value = rawText,
            onValueChange = onRawTextChange,
            label = { Text("Texto de la notificación") },
            placeholder = { Text("Ej: AVVillas. 19/06/26 20:48 COMPRA POR $ 408,123...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            maxLines = 5
        )
    }
}

@Composable
private fun Step2HighlightTokens(
    tokens: List<String>,
    selectedAmountIndices: Set<Int>,
    onAmountIndicesChange: (Set<Int>) -> Unit,
    selectedMerchantIndices: Set<Int>,
    onMerchantIndicesChange: (Set<Int>) -> Unit,
    amountText: String,
    merchantText: String
) {
    var activeSelectionMode by remember { mutableStateOf(0) } // 0 = Monto, 1 = Comercio

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Paso 2: Selecciona los campos clave",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Toca abajo para elegir qué campo quieres editar (Monto o Comercio), luego toca las palabras del mensaje arriba para marcarlas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SimpleFlowRow(
                        spacing = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tokens.forEachIndexed { index, token ->
                            val isAmount = selectedAmountIndices.contains(index)
                            val isMerchant = selectedMerchantIndices.contains(index)

                            Box(
                                modifier = Modifier
                                    .background(
                                        color = when {
                                            isAmount -> Color(0xFFC8E6C9)
                                            isMerchant -> Color(0xFFBBDEFB)
                                            else -> MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isAmount || isMerchant) 2.dp else 1.dp,
                                        color = when {
                                            isAmount -> Color(0xFF2E7D32)
                                            isMerchant -> Color(0xFF1565C0)
                                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (activeSelectionMode == 0) {
                                            if (isAmount) {
                                                onAmountIndicesChange(emptySet())
                                            } else {
                                                onAmountIndicesChange(setOf(index))
                                                onMerchantIndicesChange(selectedMerchantIndices - index)
                                            }
                                        } else {
                                            if (isMerchant) {
                                                onMerchantIndicesChange(selectedMerchantIndices - index)
                                            } else {
                                                onMerchantIndicesChange(selectedMerchantIndices + index)
                                                onAmountIndicesChange(selectedAmountIndices - index)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = token,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isAmount || isMerchant) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isAmount -> Color(0xFF1B5E20)
                                        isMerchant -> Color(0xFF0D47A1)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Campos a Extraer:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Tarjeta para seleccionar Monto
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeSelectionMode = 0 },
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSelectionMode == 0) Color(0xFFC8E6C9).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (activeSelectionMode == 0) 2.dp else 1.dp,
                    color = if (activeSelectionMode == 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = "Monto",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Monto de Transacción",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (activeSelectionMode == 0) Color(0xFF1B5E20) else MaterialTheme.colorScheme.onSurface
                            )
                            if (activeSelectionMode == 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "EDITANDO",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (amountText.isBlank()) "Toca una palabra arriba para asignar el monto" else amountText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (amountText.isBlank()) FontWeight.Normal else FontWeight.Bold,
                            color = if (amountText.isBlank()) Color.Gray else Color(0xFF2E7D32)
                        )
                    }
                    if (amountText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onAmountIndicesChange(emptySet())
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpiar monto",
                                tint = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        // Tarjeta para seleccionar Comercio
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeSelectionMode = 1 },
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSelectionMode == 1) Color(0xFFBBDEFB).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = if (activeSelectionMode == 1) 2.dp else 1.dp,
                    color = if (activeSelectionMode == 1) Color(0xFF1565C0) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Comercio",
                                tint = Color(0xFF1565C0),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Comercio / Establecimiento",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (activeSelectionMode == 1) Color(0xFF0D47A1) else MaterialTheme.colorScheme.onSurface
                            )
                            if (activeSelectionMode == 1) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1565C0)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "EDITANDO",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (merchantText.isBlank()) "Toca palabras arriba para construir el nombre del comercio" else merchantText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (merchantText.isBlank()) FontWeight.Normal else FontWeight.Bold,
                            color = if (merchantText.isBlank()) Color.Gray else Color(0xFF1565C0)
                        )
                    }
                    if (merchantText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                onMerchantIndicesChange(emptySet())
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpiar comercio",
                                tint = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun borderStrokeForMode(selected: Boolean, mode: Int): androidx.compose.foundation.BorderStroke {
    if (!selected) return androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    val color = when (mode) {
        0 -> Color(0xFF2E7D32)
        1 -> Color(0xFF1565C0)
        else -> Color(0xFFE65100)
    }
    return androidx.compose.foundation.BorderStroke(2.dp, color)
}

@Composable
private fun Step3Details(
    ruleName: String,
    onRuleNameChange: (String) -> Unit,
    bankSource: BankSource,
    onBankSourceChange: (BankSource) -> Unit,
    bankExpanded: Boolean,
    onBankExpandedChange: (Boolean) -> Unit,
    transactionType: TransactionType,
    onTransactionTypeChange: (TransactionType) -> Unit,
    txTypeExpanded: Boolean,
    onTxTypeExpandedChange: (Boolean) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Paso 3: Detalles de la regla",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Asigna un nombre descriptivo a esta regla y define de qué entidad proviene y cómo debe interpretarse.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedTextField(
                value = ruleName,
                onValueChange = onRuleNameChange,
                label = { Text("Nombre de la Regla") },
                placeholder = { Text("Ej: Regla AVVillas Gasto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        item {
            Text("Entidad Emisora (Banco)", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = bankSource.name,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { onBankExpandedChange(true) }) {
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBankExpandedChange(true) }
                )

                DropdownMenu(
                    expanded = bankExpanded,
                    onDismissRequest = { onBankExpandedChange(false) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    BankSource.entries.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank.name) },
                            onClick = {
                                onBankSourceChange(bank)
                                onBankExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }

        item {
            Text("Tipo de Movimiento", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = when(transactionType) {
                        TransactionType.INGRESO -> "Ingreso 💵"
                        TransactionType.GASTO -> "Gasto General 🛒"
                        TransactionType.GASTO_TC -> "Compra Tarjeta Crédito 💳"
                        TransactionType.TRANSFERENCIA -> "Transferencia Saliente 📤"
                        TransactionType.PAGO_TC -> "Abono / Pago TC 📥"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { onTxTypeExpandedChange(true) }) {
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTxTypeExpandedChange(true) }
                )

                DropdownMenu(
                    expanded = txTypeExpanded,
                    onDismissRequest = { onTxTypeExpandedChange(false) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    TransactionType.entries.forEach { type ->
                        val display = when(type) {
                            TransactionType.INGRESO -> "Ingreso 💵"
                            TransactionType.GASTO -> "Gasto General 🛒"
                            TransactionType.GASTO_TC -> "Compra Tarjeta Crédito 💳"
                            TransactionType.TRANSFERENCIA -> "Transferencia Saliente 📤"
                            TransactionType.PAGO_TC -> "Abono / Pago TC 📥"
                        }
                        DropdownMenuItem(
                            text = { Text(display) },
                            onClick = {
                                onTransactionTypeChange(type)
                                onTxTypeExpandedChange(false)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step4PreviewSave(
    amountText: String,
    merchantText: String,
    parsedAmount: Double?,
    amountFormatType: Int,
    onFormatTypeChange: (Int) -> Unit,
    generatedRegex: String
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Paso 4: Vista previa e Interpretación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Valida cómo interpreta FinanzApp el monto seleccionado. Si el valor es incorrecto, selecciona otro formato.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Text(
                text = "Formato de Miles y Decimales:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "Punto de miles / Coma decimal (LatAm - ej. 123.456,78)",
                    "Coma de miles / Punto decimal (US/Anglo - ej. 123,456.78)",
                    "Sin separadores / Solo enteros (ej. 123456)"
                ).forEachIndexed { index, label ->
                    val isSelected = amountFormatType == index
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFormatTypeChange(index) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder().takeIf { !isSelected }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onFormatTypeChange(index) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Resultados del Procesamiento (Vista Previa)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Texto seleccionado:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = amountText,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Monto interpretado:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (parsedAmount != null) formatCOP(parsedAmount, showCents = true) else "Error al parsear",
                            fontWeight = FontWeight.Bold,
                            color = if (parsedAmount != null) MaterialTheme.colorScheme.primary else Color.Red,
                            fontSize = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Comercio:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (merchantText.isNotBlank()) merchantText else "Sin especificar",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (parsedAmount != null && parsedAmount > 0.0 && parsedAmount < 1000.0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(12.dp),
                    border = borderStrokeForMode(true, 2)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerta",
                            tint = Color(0xFFE65100),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Monto interpretado sospechosamente bajo",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                text = "El valor parseado es menor a $1.000 COP (${formatCOP(parsedAmount, showCents = true)}). Si esto es un valor de miles (ej: 408 mil pesos), intenta cambiar el formato de miles a US/Anglo o Plano.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100).copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

    }
}

private fun escapeRegex(text: String): String {
    return text.replace(Regex("""[\\^$.|?*+()\[\]{}]""")) { "\\${it.value}" }
}

private fun generateRegexFromTokens(
    tokens: List<String>,
    amountIndices: Set<Int>,
    merchantIndices: Set<Int>
): String {
    val builder = StringBuilder()
    var i = 0
    val n = tokens.size

    while (i < n) {
        if (i > 0) {
            builder.append("""\s+""")
        }

        when {
            i in amountIndices -> {
                var end = i
                while (end + 1 < n && (end + 1) in amountIndices) {
                    end++
                }
                builder.append("""(?<amount>[\$]?[\d.,]+)""")
                i = end
            }
            i in merchantIndices -> {
                var end = i
                while (end + 1 < n && (end + 1) in merchantIndices) {
                    end++
                }
                builder.append("""(?<merchant>[A-Za-z0-9 .*\-_]+)""")
                i = end
            }
            else -> {
                val token = tokens[i]
                when {
                    token.matches(Regex("""\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}""")) -> {
                        builder.append("""\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}""")
                    }
                    token.matches(Regex("""\d{1,2}:\d{2}(?::\d{2})?""")) -> {
                        builder.append("""\d{1,2}:\d{2}(?::\d{2})?""")
                    }
                    token.matches(Regex("""\d+""")) -> {
                        builder.append("""\d+""")
                    }
                    else -> {
                        builder.append(escapeRegex(token))
                    }
                }
            }
        }
        i++
    }
    return builder.toString()
}

private fun parseLatAmAmount(raw: String): Double? {
    val clean = raw.replace(Regex("""[^\d.,]"""), "")
    val dotIndex = clean.lastIndexOf('.')
    val commaIndex = clean.lastIndexOf(',')
    return if (commaIndex > dotIndex) {
        clean.replace(".", "").replace(",", ".").toDoubleOrNull()
    } else if (commaIndex == -1 && dotIndex != -1) {
        clean.replace(".", "").toDoubleOrNull()
    } else {
        clean.replace(",", ".").toDoubleOrNull()
    }
}

private fun parseUSAmount(raw: String): Double? {
    val clean = raw.replace(Regex("""[^\d.,]"""), "")
    val dotIndex = clean.lastIndexOf('.')
    val commaIndex = clean.lastIndexOf(',')
    return if (dotIndex > commaIndex) {
        clean.replace(",", "").toDoubleOrNull()
    } else if (dotIndex == -1 && commaIndex != -1) {
        clean.replace(",", "").toDoubleOrNull()
    } else {
        clean.toDoubleOrNull()
    }
}

private fun parsePlainAmount(raw: String): Double? {
    val clean = raw.replace(Regex("""[^\d]"""), "")
    return clean.toDoubleOrNull()
}

// Guessing Helpers for Rule Trainer Auto-Configuration
private fun guessAmountIndex(tokens: List<String>): Set<Int> {
    var bestIndex = -1
    var bestScore = -1

    tokens.forEachIndexed { index, token ->
        val hasDigits = token.any { it.isDigit() }
        if (!hasDigits) return@forEachIndexed

        // Ignorar fechas
        if (token.matches(Regex("""\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}"""))) return@forEachIndexed

        // Ignorar horas
        if (token.matches(Regex("""\d{1,2}:\d{2}(?::\d{2})?"""))) return@forEachIndexed

        var score = 10

        if (token.contains('.') || token.contains(',')) {
            score += 20
        }

        if (token.contains('$') || token.contains("USD", ignoreCase = true) || token.contains("COP", ignoreCase = true)) {
            score += 50
        }

        if (index > 0 && tokens[index - 1] == "$") {
            score += 50
        }

        if (token.length == 4 && token.all { it.isDigit() }) {
            for (offset in 1..2) {
                if (index - offset >= 0) {
                    val prev = tokens[index - offset].lowercase()
                    if (prev.contains("tarjeta") || prev.contains("credito") || prev.contains("cuenta") || prev.contains("terminada") || prev.contains("tc")) {
                        score -= 40
                    }
                }
            }
        }

        if (score > bestScore) {
            bestScore = score
            bestIndex = index
        }
    }

    return if (bestIndex != -1 && bestScore > 0) setOf(bestIndex) else emptySet()
}

private fun guessMerchantIndices(rawText: String, tokens: List<String>): Set<Int> {
    val merchantStr = com.ivan.finanzapp.data.notification.parsers.ParserUtils.extractMerchant(rawText) ?: return emptySet()
    val merchantWords = merchantStr.trim().split(Regex("""\s+"""))
        .map { it.replace(Regex("""[^\w]"""), "").lowercase() }
        .filter { it.length >= 2 }
    if (merchantWords.isEmpty()) return emptySet()

    val guessed = mutableSetOf<Int>()
    tokens.forEachIndexed { index, token ->
        val cleanToken = token.replace(Regex("""[^\w]"""), "").lowercase()
        if (cleanToken.isEmpty()) return@forEachIndexed

        // Skip common words to prevent false positives in merchant matching
        val stopWords = setOf("compra", "venta", "pago", "retiro", "transferencia", "con", "por", "para", "del", "las", "los", "una", "uno", "tarjeta", "credito", "debito", "cuenta", "ahorros", "banco")
        if (cleanToken in stopWords) return@forEachIndexed

        for (word in merchantWords) {
            if (cleanToken == word) {
                guessed.add(index)
                break
            }
        }
    }
    return guessed
}
