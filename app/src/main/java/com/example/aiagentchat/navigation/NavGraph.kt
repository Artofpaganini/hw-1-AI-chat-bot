package com.example.aiagentchat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.aiagentchat.feature.home.presentation.HomeScreen
import com.example.aiagentchat.feature.settings.presentation.SettingsScreen
import com.example.aiagentchat.feature.patients.presentation.PatientsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Patients : Screen("patients")
    data object Settings : Screen("settings")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
        composable(Screen.Patients.route) {
            PatientsScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}

