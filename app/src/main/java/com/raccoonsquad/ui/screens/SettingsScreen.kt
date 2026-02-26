package com.raccoonsquad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.raccoonsquad.data.settings.SettingsManager
import com.raccoonsquad.ui.theme.*
import com.raccoonsquad.core.update.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentTheme by settingsManager.theme.collectAsState(initial = AppTheme.PURPLE)
    val developerMode by settingsManager.developerMode.collectAsState(initial = false)
    val killSwitch by settingsManager.killSwitch.collectAsState(initial = false)
    val autoReconnect by settingsManager.autoReconnect.collectAsState(initial = true)

    var showThemeDialog by remember { mutableStateOf(false) }
    var showDevOptions by remember { mutableStateOf(false) }  // Hidden dev mode unlock
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.raccoonsquad.core.update.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Appearance section
            item {
                Text(
                    "Внешний вид",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showThemeDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, "Theme", modifier = Modifier.padding(end = 12.dp))
                            Column {
                                Text("Цветовая тема", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    currentTheme.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, "Select")
                    }
                }
            }
            
            // Theme preview
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Превью темы", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppTheme.values().take(4).forEach { theme ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(getThemePrimaryColor(theme))
                                )
                            }
                        }
                    }
                }
            }

            // VPN section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "VPN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Kill Switch
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Security,
                                    "Kill Switch",
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text("Kill Switch", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Блокировать интернет при отключении VPN",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = killSwitch,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsManager.setKillSwitch(enabled)
                                }
                            }
                        )
                    }
                }
            }

            // Auto-reconnect
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Refresh,
                                    "Auto-reconnect",
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text("Авто-переподключение", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Переподключаться при разрыве связи",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = autoReconnect,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsManager.setAutoReconnect(enabled)
                                }
                            }
                        )
                    }
                }
            }

            // Developer section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Для разработчиков",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Code,
                                    "Developer",
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text("Режим разработчика", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (developerMode) "Показываются все логи" else "Только важные сообщения",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = developerMode,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsManager.setDeveloperMode(enabled)
                                }
                            }
                        )
                    }
                }
            }

            // Developer mode info
            if (developerMode) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Developer Mode Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Text(
                                "• All logs (DEBUG, VERBOSE) visible\n" +
                                "• Technical details in diagnostics\n" +
                                "• Extended error information",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
            
            // Info section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Информация",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Pets,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Raccoon Squad", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "VPN клиент на базе Xray-core",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Version: 1.3.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdates = true
                                    val result = com.raccoonsquad.core.update.UpdateChecker.checkForUpdates()
                                    isCheckingUpdates = false
                                    result.onSuccess { info ->
                                        updateInfo = info
                                        showUpdateDialog = true
                                    }.onFailure { e ->
                                        // Show error snackbar
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCheckingUpdates
                        ) {
                            if (isCheckingUpdates) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isCheckingUpdates) "Проверка..." else "Проверить обновления")
                        }
                    }
                }
            }
        }
    }
    
    // Update dialog
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (updateInfo!!.isUpdateAvailable) Icons.Default.NewReleases else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (updateInfo!!.isUpdateAvailable) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (updateInfo!!.isUpdateAvailable) "Update Available" else "Up to Date")
                }
            },
            text = {
                Column {
                    Text("Текущая версия: ${updateInfo!!.currentVersion}")
                    Text("Последняя версия: ${updateInfo!!.latestVersion}")
                    if (updateInfo!!.isUpdateAvailable) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Изменения:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            updateInfo!!.changelog.take(300) + if (updateInfo!!.changelog.length > 300) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                if (updateInfo!!.isUpdateAvailable) {
                    Button(onClick = {
                        com.raccoonsquad.core.update.UpdateChecker.openDownloadPage(context, updateInfo!!.downloadUrl)
                        showUpdateDialog = false
                    }) {
                        Text("Скачать")
                    }
                } else {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme ->
                scope.launch {
                    settingsManager.setTheme(theme)
                }
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите тему") },
        text = {
            LazyColumn {
                items(AppTheme.values()) { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color preview circle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(getThemePrimaryColor(theme))
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                theme.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (theme == AppTheme.AMOLED) {
                                Text(
                                    "Идеально для OLED экранов",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        if (theme == currentTheme) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun getThemePrimaryColor(theme: AppTheme): Color {
    return when (theme) {
        AppTheme.PURPLE -> PurplePrimary
        AppTheme.BLUE -> BluePrimary
        AppTheme.GREEN -> GreenPrimary
        AppTheme.ORANGE -> OrangePrimary
        AppTheme.RED -> RedPrimary
        AppTheme.DARK -> DarkPrimary
        AppTheme.AMOLED -> Color(0xFF333333)
    }
}
