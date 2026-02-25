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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raccoonsquad.core.vpn.RaccoonVpnService
import com.raccoonsquad.core.util.NodeTester
import com.raccoonsquad.core.stats.TrafficStats
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.ui.viewmodel.NodeViewModel
import com.raccoonsquad.ui.viewmodel.NodeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class SortOrder {
    FAVORITES_FIRST, NAME_ASC, NAME_DESC, PING_ASC, PING_DESC
}

object VpnController {
    var pendingConfig: VlessConfig? = null
    var pendingViewModel: NodeViewModel? = null
    
    fun onPermissionGranted(context: android.content.Context) {
        pendingConfig?.let { config ->
            startVpn(context, config)
            pendingViewModel?.setActiveNode(config.id)
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
    val activeId by viewModel.activeNodeId.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importCount by viewModel.importCount.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val testingUuids by viewModel.testingUuids.collectAsState()
    val isAutoTesting by viewModel.isAutoTesting.collectAsState()
    
    var showImportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCosmeticDialog by remember { mutableStateOf(false) }
    
    var sortOrder by remember { mutableStateOf(SortOrder.FAVORITES_FIRST) }
    
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Warm-up state - show loading briefly while Compose initializes
    var isListReady by remember { mutableStateOf(false) }
    var visibleNodeCount by remember { mutableStateOf(0) }
    
    // Warm up the list with progressive loading
    LaunchedEffect(nodes.size) {
        if (nodes.isNotEmpty() && !isListReady) {
            // Longer warm-up for large lists
            val warmupTime = if (nodes.size > 100) 500L else 300L
            kotlinx.coroutines.delay(warmupTime)
            
            // Progressive load: start with first 50, then add more smoothly
            val batchSize = 50
            visibleNodeCount = minOf(batchSize, nodes.size)
            isListReady = true
            
            // Load rest in batches with small delays (invisible to user)
            if (nodes.size > batchSize) {
                while (visibleNodeCount < nodes.size) {
                    kotlinx.coroutines.delay(50) // tiny delay between batches
                    visibleNodeCount = minOf(visibleNodeCount + batchSize, nodes.size)
                }
            }
        } else if (nodes.isEmpty()) {
            isListReady = true
            visibleNodeCount = 0
        }
    }
    
    // Create UI states - use derivedStateOf to prevent unnecessary recompositions
    // Note: latency is stored directly in VlessConfig, not in separate testResults map
    val uiStates by remember {
        derivedStateOf {
            val list = nodes.take(visibleNodeCount).mapIndexed { index, config -> 
                NodeUiState(
                    id = config.id,
                    index = index,
                    name = config.getDisplayName(),
                    server = "${config.serverAddress}:${config.port}",
                    cosmetics = config.getCosmeticsInfo(),
                    latency = config.latency,  // Use latency directly from config
                    isActive = config.id == activeId,
                    hasFragment = config.fragmentationEnabled,
                    hasNoise = config.noiseEnabled,
                    mtu = config.mtu,
                    isFavorite = config.isFavorite,
                    config = config
                )
            }
            
            when (sortOrder) {
                SortOrder.FAVORITES_FIRST -> list.sortedByDescending { it.isFavorite }
                SortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                SortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                SortOrder.PING_ASC -> list.sortedBy { it.latency ?: Long.MAX_VALUE }
                SortOrder.PING_DESC -> list.sortedByDescending { it.latency ?: Long.MIN_VALUE }
            }
        }
    }
    
    val activeNode = uiStates.find { it.isActive }
    // VPN is active if there's an active node ID
    val isVpnActive = activeId != null
    
    // IP check state
    var exitIp by remember { mutableStateOf<String?>(null) }
    var exitCountry by remember { mutableStateOf<String?>(null) }
    var isCheckingIp by remember { mutableStateOf(false) }
    
    // Handle import completion
    LaunchedEffect(importCount) {
        if (importCount != 0) {
            if (importCount > 0) {
                snackbarHostState.showSnackbar("✅ Импортировано $importCount нод")
            } else if (importCount < 0) {
                snackbarHostState.showSnackbar("🗑️ Удалено ${-importCount} нерабочих нод")
            }
            showImportDialog = false
            viewModel.resetImportCount()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isVpnActive) "🦝 Active" else "🦝 Raccoon Squad")
                        if (uiStates.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "(${uiStates.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (uiStates.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("⭐ Избранное") },
                                    onClick = {
                                        sortOrder = SortOrder.FAVORITES_FIRST
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("А-Я ↑") },
                                    onClick = {
                                        sortOrder = SortOrder.NAME_ASC
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Я-А ↓") },
                                    onClick = {
                                        sortOrder = SortOrder.NAME_DESC
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Пинг ↑") },
                                    onClick = {
                                        sortOrder = SortOrder.PING_ASC
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Пинг ↓") },
                                    onClick = {
                                        sortOrder = SortOrder.PING_DESC
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, "Import")
                    }
                    if (uiStates.isNotEmpty()) {
                        IconButton(onClick = { showCosmeticDialog = true }) {
                            Icon(Icons.Default.AutoFixHigh, "Cosmetics")
                        }
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
                exitIp = exitIp,
                exitCountry = exitCountry,
                isCheckingIp = isCheckingIp,
                onConnect = {
                    if (uiStates.isNotEmpty()) {
                        connectVpn(activity, uiStates.first().config, viewModel)
                    }
                },
                onDisconnect = {
                    VpnController.stopVpn(context)
                    viewModel.setActiveNode(null)
                    exitIp = null
                    exitCountry = null
                },
                onCheckIp = {
                    if (isVpnActive && !isCheckingIp) {
                        isCheckingIp = true
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                // Use SOCKS5 proxy provided by Xray (127.0.0.1:10808)
                                // This ensures the request goes through VPN tunnel
                                val proxy = java.net.Proxy(
                                    java.net.Proxy.Type.SOCKS,
                                    java.net.InetSocketAddress("127.0.0.1", 10808)
                                )
                                
                                val client = okhttp3.OkHttpClient.Builder()
                                    .proxy(proxy)
                                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                
                                // Use ip-api.com - returns both IP and country in one request
                                val request = okhttp3.Request.Builder()
                                    .url("http://ip-api.com/json/")
                                    .get()
                                    .build()
                                
                                val response = client.newCall(request).execute()
                                val body = response.body?.string()
                                
                                if (body != null) {
                                    val json = org.json.JSONObject(body)
                                    val ip = json.optString("query", "unknown")
                                    val country = json.optString("country", "")
                                    val city = json.optString("city", "")
                                    val isp = json.optString("isp", "")
                                    
                                    withContext(Dispatchers.Main) {
                                        exitIp = ip
                                        exitCountry = if (country.isNotEmpty()) "$country ($city)" else null
                                        isCheckingIp = false
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        exitIp = "No response"
                                        exitCountry = null
                                        isCheckingIp = false
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    exitIp = "Error: ${e.message?.take(30)}"
                                    exitCountry = null
                                    isCheckingIp = false
                                }
                            }
                        }
                    }
                }
            )
            
            if (uiStates.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else if (!isListReady) {
                // Minimal loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🦝 ${nodes.size}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = uiStates,
                        key = { _, node -> node.id },
                        contentType = { _, _ -> "node" }
                    ) { _, node ->
                        NodeCard(
                            node = node,
                            isTesting = false,
                            onClick = { onNodeClick(node.config.id) },
                            onToggle = {
                                if (node.isActive) {
                                    VpnController.stopVpn(context)
                                    viewModel.setActiveNode(null)
                                } else {
                                    connectVpn(activity, node.config, viewModel)
                                }
                            },
                            onTest = { },
                            onFavorite = { }
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
            isVpnActive = isVpnActive,
            onTestAllTcp = { viewModel.testAllNodes(NodeTester.TestMethod.TCP) },
            onTestUrl = {
                // URL test through active VPN - measures real latency
                GlobalScope.launch(Dispatchers.IO) {
                    val result = NodeTester.testThroughActiveProxy()
                    withContext(Dispatchers.Main) {
                        if (result.success) {
                            snackbarHostState.showSnackbar("✅ URL тест: ${result.latencyMs}ms")
                        } else {
                            snackbarHostState.showSnackbar("❌ ${result.error?.take(30) ?: "Ошибка"}")
                        }
                    }
                }
            },
            onAutoCleanTcp = { viewModel.autoTestAndClean(NodeTester.TestMethod.TCP) },
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
    
    // Cosmetic Dialog
    if (showCosmeticDialog) {
        CosmeticDialog(
            onDismiss = { showCosmeticDialog = false },
            onApplyPreset = { presetId ->
                viewModel.applyPresetToAll(presetId)
                showCosmeticDialog = false
            },
            onRandomize = {
                viewModel.randomizeAllCosmetics()
                showCosmeticDialog = false
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
        viewModel.setActiveNode(config.id)
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
    exitIp: String?,
    exitCountry: String?,
    isCheckingIp: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCheckIp: () -> Unit
) {
    // Read traffic stats directly to avoid parent recomposition
    var trafficText by remember { mutableStateOf("") }
    
    DisposableEffect(isActive) {
        if (isActive) {
            val listener: (Long, Long, Long, Long) -> Unit = { rx, tx, _, _ ->
                trafficText = "↓ ${TrafficStats.formatSpeed(rx)}  ↑ ${TrafficStats.formatSpeed(tx)}"
            }
            TrafficStats.addListener(listener)
            onDispose { TrafficStats.removeListener(listener) }
        } else {
            trafficText = ""
            onDispose { }
        }
    }
    
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isActive) "🟢 VPN активен" else "⚪ VPN отключен",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    activeNode?.name ?: "$nodeCount нод",
                    style = MaterialTheme.typography.bodySmall
                )
                
                // Show exit IP if available
                if (isActive && exitIp != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "🌐 $exitIp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (exitCountry != null) {
                            Text(
                                "($exitCountry)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                // Show traffic stats when connected
                if (isActive && trafficText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        trafficText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            if (isActive) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCheckIp,
                        enabled = !isCheckingIp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        if (isCheckingIp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        } else {
                            Text("Check IP")
                        }
                    }
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Стоп")
                    }
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
        onDismissRequest = onDismiss,
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
    isVpnActive: Boolean = false,
    onTestAllTcp: () -> Unit,
    onTestUrl: () -> Unit = {},
    onAutoCleanTcp: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (isAutoTesting) { {} } else onDismiss,
        title = { Text("Тестирование") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // TCP Ping section
                Text("Быстрая проверка (TCP Ping):", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Проверяет только доступность порта сервера",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = onTestAllTcp,
                    enabled = !isAutoTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 TCP Ping всех нод")
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // URL Test section - only when VPN is active
                if (isVpnActive) {
                    Text("Реальная проверка:", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Проверяет через активный VPN туннель",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Button(
                        onClick = onTestUrl,
                        enabled = !isAutoTesting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("🌐 URL тест через VPN")
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
                
                // Auto-clean section
                Text("Умная очистка:", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Удаляет ноды с недоступными портами",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                
                Button(
                    onClick = onAutoCleanTcp,
                    enabled = !isAutoTesting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("🗑️ Удалить недоступные")
                }
                
                if (isAutoTesting) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Тестирование...", style = MaterialTheme.typography.bodySmall)
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    if (isVpnActive) 
                        "✅ VPN активен - можно проверить через туннель"
                    else 
                        "⚠️ TCP Ping не гарантирует работу VPN!\nПодключитесь для реальной проверки",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isVpnActive) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    onTest: () -> Unit,
    onFavorite: () -> Unit
) {
    // Stable colors calculated once
    val latencyColor = when {
        node.latency == null -> MaterialTheme.colorScheme.onSurfaceVariant
        node.latency < 100 -> Color(0xFF4CAF50)
        node.latency < 300 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    
    // Fixed height for predictable layout (faster recomposition)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (node.isActive) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (node.isActive) 2.dp else 0.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main content
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Status + Name + Ping
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (node.isActive) "🟢" else "⚪",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (node.isFavorite) {
                        Text(" ⭐", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = " ${node.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (node.isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    // Ping
                    node.latency?.let { latency ->
                        Text(
                            text = if (latency > 0) " $latency ms" else " ✗",
                            style = MaterialTheme.typography.labelSmall,
                            color = latencyColor
                        )
                    }
                }
                
                // Line 2: Server address
                Text(
                    text = node.server,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            // Connect/Disconnect button
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(36.dp)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (node.isActive) "⏹" else "▶",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (node.isActive) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CosmeticDialog(
    onDismiss: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onRandomize: () -> Unit
) {
    val presets = listOf(
        Triple("minimal", "🛡️ Минимальная", "Без маскировки"),
        Triple("mobile", "📱 Для мобильных", "Оптимизация для мобильных сетей"),
        Triple("tspu", "🚫 Против ТСПУ", "Обход DPI России"),
        Triple("maximum", "🔥 Максимальный", "Максимальная маскировка")
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎨 Косметика для нод") },
        text = {
            Column {
                Text(
                    "Применить ко всем нодам:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                presets.forEach { (id, name, desc) ->
                    TextButton(
                        onClick = { onApplyPreset(id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    "Рандомизация против ИИ-анализа:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = onRandomize,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🎲 Рандомизировать все")
                }
                
                Text(
                    "Каждая нода получит уникальные случайные настройки",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
