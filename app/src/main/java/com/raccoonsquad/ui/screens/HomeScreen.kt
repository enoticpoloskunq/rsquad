package com.raccoonsquad.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raccoonsquad.core.vpn.RaccoonVpnService
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.ui.viewmodel.NodeViewModel
import com.raccoonsquad.ui.viewmodel.NodeUiState

object VpnController {
    var pendingConfig: VlessConfig? = null
    var pendingViewModel: NodeViewModel? = null
    
    fun onPermissionGranted(context: android.content.Context) {
        pendingConfig?.let { config ->
            startVpn(context, config)
            pendingViewModel?.setActiveNode(config.uuid)
        }
        clear()
    }
    
    fun clear() {
        pendingConfig = null
        pendingViewModel = null
    }
    
    fun startVpn(context: android.content.Context, config: VlessConfig) {
        val intent = Intent(context, RaccoonVpnService::class.java).apply {
            action = RaccoonVpnService.ACTION_CONNECT
            putExtra("config", config)
        }
        context.startService(intent)
    }
    
    fun stopVpn(context: android.content.Context) {
        val intent = Intent(context, RaccoonVpnService::class.java).apply {
            action = RaccoonVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NodeViewModel = viewModel(),
    onAddNode: () -> Unit,
    onNodeClick: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val nodes by viewModel.nodes.collectAsState()
    val activeUuid by viewModel.activeNodeUuid.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importCount by viewModel.importCount.collectAsState()
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<VlessConfig?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(importCount) {
        if (importCount > 0) {
            snackbarHostState.showSnackbar("✅ Импортировано ${importCount} нод")
            viewModel.resetImportCount()
            showImportDialog = false
            importText = ""
        }
    }
    
    val uiStates = remember(nodes, activeUuid) {
        nodes.map { viewModel.toUiState(it, activeUuid) }
    }
    
    val activeNode = uiStates.find { it.isActive }
    val isVpnActive = RaccoonVpnService.isActive
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (isVpnActive) "🦝 Active" else "🦝 Raccoon Squad") 
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, "Import")
                    }
                    if (uiStates.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, "Clear")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            
            VpnStatusBanner(
                isActive = isVpnActive,
                activeNode = activeNode,
                nodeCount = uiStates.size,
                onConnect = {
                    if (uiStates.isNotEmpty()) {
                        connectVpn(activity, uiStates.first().config, viewModel)
                    }
                },
                onDisconnect = {
                    VpnController.stopVpn(context)
                    viewModel.setActiveNode(null)
                }
            )
            
            if (uiStates.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = uiStates,
                        key = { _, item -> item.id }
                    ) { _, node ->
                        NodeCard(
                            node = node,
                            onClick = { onNodeClick(node.id) },
                            onToggle = {
                                if (node.isActive) {
                                    VpnController.stopVpn(context)
                                    viewModel.setActiveNode(null)
                                } else {
                                    connectVpn(activity, node.config, viewModel)
                                }
                            },
                            onDelete = { showDeleteDialog = node.config }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    if (showImportDialog) {
        ImportDialog(
            importText = importText,
            importError = importError,
            onImportTextChange = { importText = it },
            onPaste = {
                val clip = clipboardManager.getText()?.text ?: ""
                if (clip.isNotEmpty()) importText = clip
            },
            onImport = { viewModel.importNodes(importText) },
            onDismiss = {
                showImportDialog = false
                importText = ""
                viewModel.clearImportError()
            }
        )
    }
    
    showDeleteDialog?.let { node ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Удалить?") },
            text = { Text(node.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNode(node.uuid)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Отмена") }
            }
        )
    }
    
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить все?") },
            text = { Text("Удалить ${uiStates.size} нод?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllNodes()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удалить все") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }
}

private fun connectVpn(
    activity: Activity?,
    config: VlessConfig,
    viewModel: NodeViewModel
) {
    if (activity == null) return
    
    val intent = VpnService.prepare(activity)
    if (intent != null) {
        VpnController.pendingConfig = config
        VpnController.pendingViewModel = viewModel
        activity.startActivityForResult(intent, 1234)
    } else {
        VpnController.startVpn(activity, config)
        viewModel.setActiveNode(config.uuid)
    }
}

fun onVpnPermissionResult(granted: Boolean, context: android.content.Context) {
    if (granted) {
        VpnController.onPermissionGranted(context)
    } else {
        VpnController.clear()
    }
}

@Composable
fun VpnStatusBanner(
    isActive: Boolean,
    activeNode: NodeUiState?,
    nodeCount: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    if (isActive) "🟢 VPN активен" else "⚪ VPN отключен",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    activeNode?.name ?: "$nodeCount нод",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (isActive) {
                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Стоп")
                }
            } else if (nodeCount > 0) {
                Button(onClick = onConnect) {
                    Text("Старт")
                }
            }
        }
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
        Text("Нет нод", style = MaterialTheme.typography.titleLarge)
        Text(
            "Нажмите + для импорта",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ImportDialog(
    importText: String,
    importError: String?,
    onImportTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт VLESS") },
        text = {
            Column {
                OutlinedTextField(
                    value = importText,
                    onValueChange = onImportTextChange,
                    label = { Text("VLESS URI или подписка") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    isError = importError != null,
                    supportingText = importError?.let { { Text(it) } }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(onClick = onPaste) {
                    Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Вставить")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport) {
                Text("Импорт")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (node.isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (node.isActive) "🟢" else "⚪",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        node.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    node.server,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (node.hasFragment) {
                        Text("🔧", fontSize = 12.sp)
                    }
                    if (node.hasNoise) {
                        Text("📡", fontSize = 12.sp)
                    }
                    if (node.mtu != "default") {
                        Text("📏${node.mtu}", fontSize = 12.sp)
                    }
                }
            }
            
            Switch(
                checked = node.isActive,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
