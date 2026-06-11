package com.haneef.school

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.ui.components.SidebarMenu
import com.haneef.school.ui.screens.auth.AuthScreen
import com.haneef.school.ui.screens.dashboard.DashboardScreen
import com.haneef.school.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SchoolApp() {
    val navController = rememberNavController()
    val preferencesManager: PreferencesManager = koinInject()
    val isLoggedIn = remember { preferencesManager.isLoggedIn() && preferencesManager.isTokenValid() }
    
    val startDestination = if (isLoggedIn) "main" else "auth"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthScreen(
                onLoginSuccess = {
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainContent(
                onSignOut = {
                    preferencesManager.clearAuthData()
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}

@Composable
fun MainContent(
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val dashboardViewModel: DashboardViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()
    val dashboardUiState by dashboardViewModel.uiState.collectAsState()

    Row(modifier = Modifier.fillMaxSize()) {
        SidebarMenu(
            onMenuItemClick = { route ->
                navController.navigate(route) {
                    launchSingleTop = true
                }
            },
            onSignOut = onSignOut
        )
        
        Box(modifier = Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = "dashboard") {
                composable("dashboard") {
                    DashboardScreen(
                        dashboardUiState = dashboardUiState,
                        onDashboardOpened = {
                            val token = preferencesManager.getAccessToken() ?: ""
                            dashboardViewModel.loadDashboardData(token)
                        }
                    )
                }
                // Additional routes can be mapped here as they are implemented
            }
        }
    }
}
