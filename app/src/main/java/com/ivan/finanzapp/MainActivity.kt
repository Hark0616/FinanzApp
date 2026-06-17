package com.ivan.finanzapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ivan.finanzapp.ui.navigation.FinanzAppNavHost
import com.ivan.finanzapp.ui.navigation.Screen
import com.ivan.finanzapp.ui.navigation.bottomNavScreens
import com.ivan.finanzapp.ui.theme.FinanzAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FinanzAppTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            bottomNavScreens.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) },
                                    selected = currentDestination?.hierarchy?.any {
                                        it.route == screen.route
                                    } == true,
                                    onClick = {
                                        val isSelected = currentDestination?.hierarchy?.any {
                                            it.route == screen.route
                                        } == true
                                        
                                        if (isSelected) {
                                            navController.currentBackStackEntry?.savedStateHandle?.set("reset_root", true)
                                        } else {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    FinanzAppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
