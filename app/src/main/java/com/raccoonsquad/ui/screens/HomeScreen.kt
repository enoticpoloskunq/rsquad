package com.raccoonsquad.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raccoonsquad.core.vpn.RaccoonVpnService
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.ui.viewmodel.NodeViewModel
import com.raccoonsquad.ui.viewmodel.NodeUiState

private const val VPN_REQUEST_CODE = 1000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NodeViewModel = viewModel(),
    onAddNode: () -> Unit,
    onNodeClick: (String) -> Unit
) {
    val context = LocalContext.current
    val nodes by viewModel.nodes.collectAsState()
    val activeUuid by viewModel.activeNodeUuid.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importCount by viewModel.importCount.collectAsState()
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<VlessConfig?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingVpnConfig by remember { mutableStateOf<VlessConfig?>(null) }
    
    val clipboardManager = LocalClipboardManager.current
    
    // Auto-paste from clipboard
    LaunchedEffect(showImportDialog) {
        if (showImportDialog) {
            val clip = clipboardManager.getText()?.text ?: ""
            if (clip.startsWith("vless://") && importText.isEmpty()) {
                importText = clip
            }
        }
    }
    
    // Show success snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(importCount) {
        if (importCount > 0) {
            snackbarHostState.showSnackbar("Импортировано ${importCount} нод")
            viewModel.resetImportCount()
            showImportDialog = false
            importText = ""
        }
    }
    
    val uiStates = remember(nodes, activeUuid) {
        nodes.map { viewModel.toUiState(it, activeUuid) }
    }
    
    val activeNode = uiStates.find { it.isActive }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🦝 Raccoon Squad") },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, "Import")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, "Clear all")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // VPN Status Card
            if (activeNode != null) {
                VpnStatusCard(
                    node = activeNode,
                    onDisconnect = { viewModel.setActiveNode(null) }
                )
            } else if (uiStates.isNotEmpty()) {
                VpnDisabledCard(
                    hasNodes = uiStates.isNotEmpty(),
                    onConnect = {
                        // Will connect to first node
                        pendingVpnConfig = uiStates.firstOrNull()?.config
                    }
                )
            }
            
            // Nodes list
            if (uiStates.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiStates, key = { it.id }) { node ->
                        NodeCard(
                            node = node,
                            onClick = { onNodeClick(node.id) },
                            onToggle = {
                                if (node.isActive) {
                                    viewModel.setActiveNode(null)
                                } else {
                                    pendingVpnConfig = node.config
                                }
                            },
                            onDelete = { showDeleteDialog = node.config }
                        )
                    }
                }
            }
        }
    }
    
    // VPN Permission Request
    LaunchedEffect(pendingVpnConfig) {
        pendingVpnConfig?.let { config ->
            val intent = VpnService.prepare(context)
            if (intent != null) {
                // Need permission - will handle in callback
                (context as? Activity)?.startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                // Already have permission, start VPN
                startVpn(context, config)
                viewModel.setActiveNode(config.uuid)
                pendingVpnConfig = null
            }
        }
    }
    
    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                viewModel.clearImportError()
            },
            title = { Text("Импорт VLESS") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("VLESS URI или подписка") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 10,
                        isError = importError != null,
                        supportingText = importError?.let { { Text(it) } }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = {
                            val clip = clipboardManager.getText()?.text ?: ""
                            if (clip.isNotEmpty()) {
                                importText = clip
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Вставить")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importText.isNotEmpty()) {
                        viewModel.importNodes(importText)
                    }
                }) {
                    Text("Импорт")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportDialog = false
                    importText = ""
                    viewModel.clearImportError()
                }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Delete confirmation
    showDeleteDialog?.let { node ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Удалить ноду?") },
            text = { Text("«${node.name}» будет удалена") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNode(node.uuid)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }
    
    // Clear all confirmation
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить все?") },
            text = { Text("Все ноды (${uiStates.size}) будут удалены") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllNodes()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun startVpn(context: android.content.Context, config: VlessConfig) {
    val intent = Intent(context, RaccoonVpnService::class.java).apply {
        action = RaccoonVpnService.ACTION_CONNECT
        putExtra("config", config)
    }
    context.startService(intent)
}

@Composable
fun VpnStatusCard(
    node: NodeUiState,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        ListItem(
            headlineContent = { 
                Text("🟢 Подключено") 
            },
            supportingContent = { 
                Text(node.name) 
            },
            trailingContent = {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Отключить")
                }
            }
        )
    }
}

@Composable
fun VpnDisabledCard(
    hasNodes: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        ListItem(
            headlineContent = { 
                Text("⚪ VPN отключен") 
            },
            supportingContent = { 
                Text(if (hasNodes) "Выберите ноду для подключения" else "Добавьте ноды") 
            },
            trailingContent = {
                if (hasNodes) {
                    Button(onClick = onConnect) {
                        Text("Подключить")
                    }
                }
            }
        )
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🦝", style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Нет нод",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Нажмите + для импорта",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeCard(
    node: NodeUiState,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(node.name) },
            supportingContent = {
                Column {
                    Text(node.server)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (node.hasFragment) {
                            Text(
                                "🔧 Fragment",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (node.hasNoise) {
                            Text(
                                "📡 Noise",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (node.mtu != "default") {
                            Text(
                                "📏 ${node.mtu}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Switch(
                    checked = node.isActive,
                    onCheckedChange = { onToggle() }
                )
            },
            leadingContent = {
                Text(
                    if (node.isActive) "🟢" else "⚪",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        )
    }
}
