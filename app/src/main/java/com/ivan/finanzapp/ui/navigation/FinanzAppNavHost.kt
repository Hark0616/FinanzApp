package com.ivan.finanzapp.ui.navigation

import android.net.Uri
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
                },
                onNavigateToReviewTransactions = {
                    navController.navigate(Uri.parse("finanzapp://transactions?action=view_review"))
                },
                onNavigateToUnclassifiedTransactions = {
                    navController.navigate(Uri.parse("finanzapp://transactions?action=view_unclassified"))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToBalance = {
                    navController.navigate(Screen.Assets.route)
                },
                onNavigateToCreditCards = {
                    navController.navigate(Screen.CreditCards.route)
                },
                onNavigateToLoans = {
                    navController.navigate(Screen.Loans.route)
                }
            )
        }

        composable(
            route = Screen.Transactions.route,
            deepLinks = listOf(
                androidx.navigation.navDeepLink {
                    uriPattern = "finanzapp://transactions?action={action}"
                }
            )
        ) { backStackEntry ->
            val action = backStackEntry.arguments?.getString("action")
            com.ivan.finanzapp.ui.transactions.TransactionsScreen(action = action)
        }


        composable(Screen.CreditCards.route) {
            com.ivan.finanzapp.ui.creditcard.CreditCardsScreen(navController = navController)
        }

        composable(Screen.Loans.route) {
            com.ivan.finanzapp.ui.loan.LoansScreen(navController = navController)
        }

        composable(Screen.Assets.route) {
            com.ivan.finanzapp.ui.assets.AssetsScreen()
        }

        composable(Screen.Settings.route) {
            com.ivan.finanzapp.ui.settings.SettingsScreen(
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route)
                }
            )
        }

        composable(Screen.Auth.route) {
            com.ivan.finanzapp.ui.auth.AuthScreen(
                onAuthSuccess = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
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
