package com.ivan.finanzapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinos de navegación de la app.
 */
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Inicio", Icons.Default.Home)
    object Transactions : Screen("transactions", "Movimientos", Icons.Default.SwapVert)
    object CreditCards : Screen("credit_cards", "Tarjetas", Icons.Default.CreditCard)
    object Loans : Screen("loans", "Créditos", Icons.Default.AccountBalance)
    object Assets : Screen("assets", "Balance", Icons.AutoMirrored.Filled.TrendingUp)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
    object Auth : Screen("auth", "Autenticación", Icons.Default.Lock)
}

/** Pantallas que aparecen en la barra de navegación inferior. */
val bottomNavScreens = listOf(
    Screen.Dashboard,
    Screen.Transactions,
    Screen.CreditCards,
    Screen.Loans,
    Screen.Assets
)
