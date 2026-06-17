package com.ivan.finanzapp.ui.assets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivan.finanzapp.data.local.entity.AssetEntity
import com.ivan.finanzapp.data.local.entity.AssetType
import com.ivan.finanzapp.ui.components.SectionTitle
import com.ivan.finanzapp.ui.components.formatCOP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    viewModel: AssetsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAsset by remember { mutableStateOf<AssetEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar Activo")
            }
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
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
                                    text = "Agrega tu sueldo, inversiones en CDT, inmuebles o vehículos presionando el botón '+'.",
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
                            onEdit = { editingAsset = asset },
                            onDelete = { viewModel.deleteAsset(asset.id) }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddAssetDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, amount, type ->
                    viewModel.addAsset(name, amount, type)
                    showAddDialog = false
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
private fun AssetItem(
    asset: AssetEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, color) = when (asset.type) {
        AssetType.SUELDO -> Icons.Default.Star to MaterialTheme.colorScheme.primary
        AssetType.INVERSION -> Icons.Default.TrendingUp to Color(0xFF1976D2)
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
                            AssetType.SUELDO -> "Sueldo / Ingreso Fijo"
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
    onDismiss: () -> Unit,
    onConfirm: (name: String, amount: Double, type: AssetType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AssetType.SUELDO) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Activo") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Activo") },
                    placeholder = { Text("Ej. Sueldo, CDT, Cripto, Casa...") },
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
                            AssetType.SUELDO -> "Sueldo / Ingreso Fijo"
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
                                            AssetType.SUELDO -> "Sueldo / Ingreso Fijo"
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
            val isValid = name.isNotBlank() && amountVal > 0.0

            Button(
                onClick = { onConfirm(name, amountVal, selectedType) },
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
                    label = { Text("Nombre del Activo") },
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
                            AssetType.SUELDO -> "Sueldo / Ingreso Fijo"
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
                                            AssetType.SUELDO -> "Sueldo / Ingreso Fijo"
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
            val isValid = name.isNotBlank() && amountVal > 0.0

            Button(
                onClick = { onConfirm(name, amountVal, selectedType) },
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
