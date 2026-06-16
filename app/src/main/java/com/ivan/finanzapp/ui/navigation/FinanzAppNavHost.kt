package com.ivan.finanzapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ivan.finanzapp.ui.dashboard.DashboardScreen

/**
 * Grafo de navegación principal de la app.
 *
 * Las pantallas de Movimientos, Tarjetas, Categorías y Ajustes muestran
 * un placeholder para que la app compile y sea navegable. Se implementan
 * en Fase 2+.
 */
@Composable
fun FinanzAppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToTransactions = {
                    navController.navigate(Screen.Transactions.route)
                }
            )
        }

        composable(Screen.Transactions.route) {
            PlaceholderScreen(
                title = "Movimientos",
                description = "Historial completo de transacciones (Fase 2)"
            )
        }

        composable(Screen.CreditCards.route) {
            PlaceholderScreen(
                title = "Tarjetas",
                description = "Detalle y gestión de tarjetas de crédito (Fase 2)"
            )
        }

        composable(Screen.Categories.route) {
            PlaceholderScreen(
                title = "Categorías",
                description = "Gestión de categorías y presupuestos (Fase 2)"
            )
        }

        composable(Screen.Settings.route) {
            PlaceholderScreen(
                title = "Ajustes",
                description = "Cuentas, API key de OpenRouter y backup (Fase 3)"
            )
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
