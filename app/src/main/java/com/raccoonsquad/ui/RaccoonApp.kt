package com.raccoonsquad.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raccoonsquad.App
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.data.settings.SettingsManager
import com.raccoonsquad.ui.screens.HomeScreen
import com.raccoonsquad.ui.screens.LogsScreen
import com.raccoonsquad.ui.screens.NodeEditorScreen
import com.raccoonsquad.ui.screens.SettingsScreen
import com.raccoonsquad.ui.theme.AppTheme
import com.raccoonsquad.ui.theme.RaccoonSquadTheme
import kotlinx.coroutines.launch
import java.io.File

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object NodeEditor : Screen("node_editor")
    object Logs : Screen("logs")
    object Settings : Screen("settings")
}

@Composable
fun RaccoonApp(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(context) }
    
    // Load theme from settings
    val appTheme by settingsManager.theme.collectAsState(initial = AppTheme.PURPLE)
    
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var showCrashDialog by remember { mutableStateOf(App.hasPendingCrashExport) }
    
    // Request notification permission on Android 13+
    var hasNotificationPermission by remember { mutableStateOf(checkNotificationPermission(context)) }
    
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }
    
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // Crash export dialog
    if (showCrashDialog) {
        CrashExportDialog(
            onDismiss = {
                showCrashDialog = false
                App.markCrashExported()
                LogManager.clearCrash()
            },
            onExport = {
                exportCrashLog(context)
                showCrashDialog = false
                App.markCrashExported()
                LogManager.clearCrash()
            }
        )
    }
    
    RaccoonSquadTheme(theme = appTheme) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = navController.currentDestination?.route == Screen.Home.route,
                        onClick = { navController.navigate(Screen.Home.route) },
                        icon = { Text("🦝") },
                        label = { Text("Nodes") }
                    )
                    NavigationBarItem(
                        selected = navController.currentDestination?.route == Screen.Logs.route,
                        onClick = { navController.navigate(Screen.Logs.route) },
                        icon = { Text("📋") },
                        label = { Text("Logs") }
                    )
                    NavigationBarItem(
                        selected = navController.currentDestination?.route == Screen.Settings.route,
                        onClick = { navController.navigate(Screen.Settings.route) },
                        icon = { Text("⚙️") },
                        label = { Text("Settings") }
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
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

@Composable
private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

@Composable
fun CrashExportDialog(
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("⚠️", style = MaterialTheme.typography.headlineMedium) },
        title = { 
            Text(
                "Crash Detected",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "The app crashed last time. Would you like to export the crash log?",
                    textAlign = TextAlign.Center
                )
                Text(
                    "This will help diagnose the issue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = onExport) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

/**
 * Export crash log via Share Intent (works on all Android versions without permissions)
 */
private fun exportCrashLog(context: Context) {
    try {
        // Create temp file with crash log
        val crashLog = LogManager.getLastCrash() ?: return
        val fullLog = LogManager.exportLogs()
        
        val tempFile = File(context.cacheDir, "raccoon_crash_log_${System.currentTimeMillis()}.txt")
        tempFile.writeText(buildString {
            append("=== RACCOON SQUAD VPN CRASH LOG ===\n\n")
            append(fullLog)
            append("\n\n=== CRASH DETAILS ===\n")
            append(crashLog)
        })
        
        // Share via intent
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Raccoon Squad Crash Log")
            putExtra(Intent.EXTRA_TEXT, "Crash log from Raccoon Squad VPN")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(shareIntent, "Export Crash Log"))
        
        LogManager.i("App", "Crash log shared successfully")
    } catch (e: Exception) {
        LogManager.e("App", "Failed to export crash log", e)
    }
}
