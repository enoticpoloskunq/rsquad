package com.raccoonsquad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raccoonsquad.ui.screens.HomeScreen
import com.raccoonsquad.ui.screens.LogsScreen
import com.raccoonsquad.ui.screens.NodeEditorScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object NodeEditor : Screen("node_editor")
    object Logs : Screen("logs")
}

@Composable
fun RaccoonApp(
    navController: NavHostController = rememberNavController()
) {
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = { navController.navigate(Screen.Home.route) },
                    icon = { Text("🦝") },
                    label = { Text("Nodes") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Logs.route) },
                    icon = { Text("📋") },
                    label = { Text("Logs") }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onAddNode = { navController.navigate(Screen.NodeEditor.route) },
                    onNodeClick = { nodeId ->
                        selectedNodeId = nodeId
                        navController.navigate(Screen.Logs.route)
                    }
                )
            }
            composable(Screen.NodeEditor.route) {
                NodeEditorScreen(
                    onBack = { navController.popBackStack() },
                    onSave = { navController.popBackStack() }
                )
            }
            composable(Screen.Logs.route) {
                LogsScreen(
                    nodeId = selectedNodeId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
