package com.raccoonsquad.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raccoonsquad.core.vpn.RaccoonVpnService
import com.raccoonsquad.core.util.NodeTester
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
    val isImporting by viewModel.isImporting.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val testingUuids by viewModel.testingUuids.collectAsState()
    val isAutoTesting by viewModel.isAutoTesting.collectAsState()
    
    var showImportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Create UI states with proper active state
    val uiStates = remember(nodes, activeUuid, testResults) {
        nodes.mapIndexed { index, config -> 
            NodeUiState(
                id = "node_$index",
                index = index,
                name = config.getDisplayName(),
                server = "${config.serverAddress}:${config.port}",
                cosmetics = config.getCosmeticsInfo(),
                latency = testResults[config.uuid] ?: config.latency,
                isActive = config.uuid == activeUuid,
                hasFragment = config.fragmentationEnabled,
                hasNoise = config.noiseEnabled,
                mtu = config.mtu,
                config = config
            )
        }
    }
    
    val activeNode = uiStates.find { it.isActive }
    // VPN is active if there's an active node UUID
    val isVpnActive = activeUuid != null
    
    LaunchedEffect(importCount) {
        when {
            importCount > 0 -> {
                snackbarHostState.showSnackbar("✅ Импортировано $importCount нод")
                viewModel.resetImportCount()
                showImportDialog = false
            }
            importCount < 0 -> {
                snackbarHostState.showSnackbar("🗑️ Удалено ${-importCount} нерабочих нод")
                viewModel.resetImportCount()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isVpnActive) "🦝 Active" else "🦝 Raccoon Squad") },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, "Import")
                    }
                    if (uiStates.isNotEmpty()) {
                        IconButton(onClick = { showTestDialog = true }) {
                            Icon(Icons.Default.Speed, "Test")
                        }
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
                        key = { index, _ -> "node_$index" }
                    ) { _, node ->
                        NodeCard(
                            node = node,
                            isTesting = testingUuids.contains(node.config.uuid),
                            onClick = { onNodeClick(node.config.uuid) },
                            onToggle = {
                                if (node.isActive) {
                                    VpnController.stopVpn(context)
                                    viewModel.setActiveNode(null)
                                } else {
                                    connectVpn(activity, node.config, viewModel)
                                }
                            },
                            onTest = {
                                viewModel.testNode(node.config)
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    // Import Dialog
    if (showImportDialog) {
        ImportDialog(
            isImporting = isImporting,
            importError = importError,
            onImportText = { text -> viewModel.importNodes(text) },
            onImportUrl = { url -> viewModel.importFromUrl(url) },
            onPaste = {
                val clip = clipboardManager.getText()?.text ?: ""
                clip
            },
            onDismiss = {
                showImportDialog = false
                viewModel.clearImportError()
            }
        )
    }
    
    // Test Dialog
    if (showTestDialog) {
        TestDialog(
            isAutoTesting = isAutoTesting,
            onTestAllTcp = { viewModel.testAllNodes(NodeTester.TestMethod.TCP) },
            onTestAllHttp = { viewModel.testAllNodes(NodeTester.TestMethod.HTTP) },
            onAutoCleanTcp = { viewModel.autoTestAndClean(NodeTester.TestMethod.TCP) },
            onAutoCleanHttp = { viewModel.autoTestAndClean(NodeTester.TestMethod.HTTP) },
            onDismiss = { showTestDialog = false }
        )
    }
    
    // Clear Dialog
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDialog(
    isImporting: Boolean,
    importError: String?,
    onImportText: (String) -> Unit,
    onImportUrl: (String) -> Unit,
    onPaste: () -> String,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = if (isImporting) { {} } else onDismiss,
        title = { Text("Импорт VLESS") },
        text = {
            Column {
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Буфер") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Вручную") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Подписка") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                when (selectedTab) {
                    0 -> {
                        // Clipboard
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("VLESS ссылки из буфера") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            maxLines = 4,
                            isError = importError != null,
                            supportingText = importError?.let { { Text(it) } }
                        )
                        TextButton(onClick = { text = onPaste() }) {
                            Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Вставить из буфера")
                        }
                    }
                    1 -> {
                        // Manual
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("VLESS URI") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = importError != null,
                            supportingText = importError?.let { { Text(it) } }
                        )
                    }
                    2 -> {
                        // Subscription URL
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("URL подписки") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = importError != null,
                            supportingText = importError?.let { { Text(it) } },
                            leadingIcon = { Icon(Icons.Default.Link, null) }
                        )
                    }
                }
                
                if (isImporting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (selectedTab) {
                        0, 1 -> onImportText(text)
                        2 -> onImportUrl(url)
                    }
                },
                enabled = !isImporting && when (selectedTab) {
                    0, 1 -> text.isNotBlank()
                    2 -> url.isNotBlank()
                    else -> false
                }
            ) {
                Text("Импорт")
            }
        },
        dismissButton = {
            if (!isImporting) {
                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}

@Composable
fun TestDialog(
    isAutoTesting: Boolean,
    onTestAllTcp: () -> Unit,
    onTestAllHttp: () -> Unit,
    onAutoCleanTcp: () -> Unit,
    onAutoCleanHttp: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (isAutoTesting) { {} } else onDismiss,
        title = { Text("Тестирование") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Тест всех нод:", style = MaterialTheme.typography.labelMedium)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTestAllTcp, enabled = !isAutoTesting) {
                        Text("TCP Ping")
                    }
                    Button(onClick = onTestAllHttp, enabled = !isAutoTesting) {
                        Text("HTTP GET")
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text("Умная очистка (удалить нерабочие):", style = MaterialTheme.typography.labelMedium)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAutoCleanTcp,
                        enabled = !isAutoTesting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("TCP")
                    }
                    Button(
                        onClick = onAutoCleanHttp,
                        enabled = !isAutoTesting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("HTTP")
                    }
                }
                
                if (isAutoTesting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Тестирование...", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isAutoTesting) {
                Text("Готово")
            }
        }
    )
}

@Composable
fun NodeCard(
    node: NodeUiState,
    isTesting: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onTest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
                        text = "${if (node.isActive) "🟢" else "⚪"} ${node.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Ping display
                    val latency = node.latency
                    if (latency != null && latency > 0) {
                        Text(
                            text = "${latency}ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                latency < 100 -> MaterialTheme.colorScheme.primary
                                latency < 300 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    } else if (latency == -1L) {
                        Text(
                            text = "❌",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                
                Text(
                    node.server,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Show cosmetics info
                if (node.cosmetics.isNotEmpty() && node.cosmetics != "no cosmetics") {
                    Text(
                        text = node.cosmetics,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Test button
                IconButton(
                    onClick = onTest,
                    enabled = !isTesting
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Speed, "Test")
                    }
                }
                
                Switch(
                    checked = node.isActive,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}
