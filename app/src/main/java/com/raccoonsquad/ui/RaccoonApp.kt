package com.raccoonsquad.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object NodeEditor : Screen("node_editor")
    object Logs : Screen("logs")
    object Settings : Screen("settings")
}

// Easter egg: Raccoon particles
data class RaccoonParticle(
    val id: Int,
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val alpha: Float
)

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
    
    // Easter egg state
    var raccoonTapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var showRaccoonRain by remember { mutableStateOf(false) }
    var raccoonParticles by remember { mutableStateOf(listOf<RaccoonParticle>()) }
    
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
    
    // Easter egg: Raccoon rain animation
    LaunchedEffect(showRaccoonRain) {
        if (showRaccoonRain) {
            // Generate particles
            repeat(30) { i ->
                delay(Random.nextLong(50, 200))
                raccoonParticles = raccoonParticles + RaccoonParticle(
                    id = i,
                    x = Random.nextFloat(),
                    y = -0.1f,
                    size = Random.nextFloat() * 30f + 20f,
                    rotation = Random.nextFloat() * 360f,
                    alpha = Random.nextFloat() * 0.5f + 0.5f
                )
            }
            
            // Animate falling
            repeat(50) {
                delay(100)
                raccoonParticles = raccoonParticles.mapNotNull { p ->
                    val newY = p.y + 0.03f
                    if (newY > 1.2f) null else p.copy(y = newY, rotation = p.rotation + 5f)
                }
            }
            
            showRaccoonRain = false
            raccoonParticles = emptyList()
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
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = navController.currentDestination?.route == Screen.Home.route,
                            onClick = {
                                navController.navigate(Screen.Home.route)
                                // Easter egg: detect rapid taps
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 500) {
                                    raccoonTapCount++
                                    if (raccoonTapCount >= 7) {
                                        showRaccoonRain = true
                                        raccoonTapCount = 0
                                    }
                                } else {
                                    raccoonTapCount = 1
                                }
                                lastTapTime = now
                            },
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
                            settingsManager = settingsManager,
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
            
            // Easter egg: Raccoon rain overlay
            raccoonParticles.forEach { particle ->
                RaccoonParticleView(
                    particle = particle,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun RaccoonParticleView(
    particle: RaccoonParticle,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .absoluteOffset(
                x = (particle.x * 400).dp - 200.dp,
                y = (particle.y * 1000).dp
            )
            .alpha(particle.alpha)
            .scale(particle.size / 50f)
    ) {
        Text(
            text = "🦝",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.graphicsLayer { rotationZ = particle.rotation }
        )
    }
}

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
