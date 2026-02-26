package com.raccoonsquad.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raccoonsquad.core.splittunneling.*

/**
 * Split Tunneling Screen - choose which apps go through VPN
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by SplitTunnelingManager.config.collectAsState()
    val apps by SplitTunnelingManager.installedApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    // Load apps on first launch
    LaunchedEffect(Unit) {
        SplitTunnelingManager.loadInstalledApps(context.packageManager)
    }
    
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { 
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Tunneling") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode selector
            item {
                ModeSelectorCard(
                    currentMode = config.mode,
                    onModeSelected = { SplitTunnelingManager.setMode(it) }
                )
            }
            
            // Mode description
            item {
                AnimatedContent(
                    targetState = config.mode,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "mode_description"
                ) { mode ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (mode) {
                                    SplitTunnelingMode.DISABLED -> Icons.Default.VpnLock
                                    SplitTunnelingMode.INCLUDE_MODE -> Icons.Default.FilterList
                                    SplitTunnelingMode.EXCLUDE_MODE -> Icons.Default.Block
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                when (mode) {
                                    SplitTunnelingMode.DISABLED -> 
                                        "Весь трафик идёт через VPN"
                                    SplitTunnelingMode.INCLUDE_MODE -> 
                                        "Только выбранные приложения через VPN"
                                    SplitTunnelingMode.EXCLUDE_MODE -> 
                                        "Все приложения через VPN, кроме выбранных"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // Only show app selection when not disabled
            if (config.mode != SplitTunnelingMode.DISABLED) {
                // Presets
                item {
                    Text(
                        "Быстрый выбор",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SplitTunnelingManager.presets.forEach { preset ->
                            FilterChip(
                                selected = false,
                                onClick = { SplitTunnelingManager.applyPreset(preset) },
                                label = { Text(preset.name) },
                                leadingIcon = {
                                    when (preset.name) {
                                        "Соцсети" -> Text("📱", modifier = Modifier.padding(end = 4.dp))
                                        "Мессенджеры" -> Text("💬", modifier = Modifier.padding(end = 4.dp))
                                        "Медиа" -> Text("🎬", modifier = Modifier.padding(end = 4.dp))
                                        "Браузеры" -> Text("🌐", modifier = Modifier.padding(end = 4.dp))
                                        else -> null
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Selected apps counter
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Выбрано: ${config.selectedApps.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { SplitTunnelingManager.selectAllApps() }) {
                                Text("Выбрать все")
                            }
                            TextButton(onClick = { SplitTunnelingManager.clearSelectedApps() }) {
                                Text("Очистить")
                            }
                        }
                    }
                }
                
                // Search
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Поиск приложений...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, "Clear")
                                }
                            }
                        },
                        singleLine = true
                    )
                }
                
                // Apps list
                items(filteredApps, key = { it.packageName }) { app ->
                    AppSelectionItem(
                        app = app,
                        isSelected = app.packageName in config.selectedApps,
                        onToggle = { SplitTunnelingManager.toggleApp(app.packageName) }
                    )
                }
            }
            
            // Low battery mode
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card {
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
                                    Icons.Default.BatterySaver,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column {
                                    Text("Режим энергосбережения", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Оптимизирует VPN для экономии батареи",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Switch(
                            checked = config.isLowBatteryMode,
                            onCheckedChange = { SplitTunnelingManager.setLowBatteryMode(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelectorCard(
    currentMode: SplitTunnelingMode,
    onModeSelected: (SplitTunnelingMode) -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Режим работы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.VpnLock,
                    label = "Все через VPN",
                    isSelected = currentMode == SplitTunnelingMode.DISABLED,
                    onClick = { onModeSelected(SplitTunnelingMode.DISABLED) }
                )
                
                ModeButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.FilterList,
                    label = "Включить",
                    isSelected = currentMode == SplitTunnelingMode.INCLUDE_MODE,
                    onClick = { onModeSelected(SplitTunnelingMode.INCLUDE_MODE) }
                )
                
                ModeButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Block,
                    label = "Исключить",
                    isSelected = currentMode == SplitTunnelingMode.EXCLUDE_MODE,
                    onClick = { onModeSelected(SplitTunnelingMode.EXCLUDE_MODE) }
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppSelectionItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            val bitmap = remember(app.packageName) {
                (app.icon as? BitmapDrawable)?.bitmap
            }
            
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
