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
    val testResults by viewModel.testResults.collectAsState()
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
    
    // Create UI states with proper active state, sorted
    val uiStates = remember(nodes, activeId, testResults, sortOrder) {
        val list = nodes.mapIndexed { index, config -> 
            NodeUiState(
                id = config.id,
                index = index,
                name = config.getDisplayName(),
                server = "${config.serverAddress}:${config.port}",
                cosmetics = config.getCosmeticsInfo(),
                latency = testResults[config.id] ?: config.latency,
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
    
    val activeNode = uiStates.find { it.isActive }
    // VPN is active if there's an active node ID
    val isVpnActive = activeId != null
    
    // Traffic stats
    var downloadSpeed by remember { mutableStateOf(0L) }
    var uploadSpeed by remember { mutableStateOf(0L) }
    var totalDownload by remember { mutableStateOf(0L) }
    var totalUpload by remember { mutableStateOf(0L) }
    
    // IP check state
    var exitIp by remember { mutableStateOf<String?>(null) }
    var exitCountry by remember { mutableStateOf<String?>(null) }
    var isCheckingIp by remember { mutableStateOf(false) }
    
    // Update traffic stats
    DisposableEffect(isVpnActive) {
        if (isVpnActive) {
            val listener: (Long, Long, Long, Long) -> Unit = { rx, tx, rxTotal, txTotal ->
                downloadSpeed = rx
                uploadSpeed = tx
                totalDownload = rxTotal
                totalUpload = txTotal
            }
            TrafficStats.addListener(listener)
            onDispose { TrafficStats.removeListener(listener) }
        }
        onDispose { }
    }
    
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
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                totalDownload = totalDownload,
                totalUpload = totalUpload,
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
                                val client = okhttp3.OkHttpClient.Builder()
                                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                
                                // Use ip-api.com - returns both IP and country in one request
                                // This endpoint works over HTTP and is more reliable
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
                            isTesting = testingUuids.contains(node.config.id),
                            onClick = { onNodeClick(node.config.id) },
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
                            },
                            onFavorite = {
                                viewModel.toggleFavorite(node.config.id)
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
            onTestAllHttp = { viewModel.testAllNodes(NodeTester.TestMethod.TCP) },
            onAutoCleanTcp = { viewModel.autoTestAndClean(NodeTester.TestMethod.TCP) },
            onAutoCleanHttp = { viewModel.autoTestAndClean(NodeTester.TestMethod.TCP) },
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
    downloadSpeed: Long,
    uploadSpeed: Long,
    totalDownload: Long,
    totalUpload: Long,
    exitIp: String?,
    exitCountry: String?,
    isCheckingIp: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCheckIp: () -> Unit
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
                if (isActive && (downloadSpeed > 0 || uploadSpeed > 0 || totalDownload > 0)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "↓ ${TrafficStats.formatSpeed(downloadSpeed)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "↑ ${TrafficStats.formatSpeed(uploadSpeed)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (totalDownload > 1024 || totalUpload > 1024) {
                        Text(
                            "Всего: ↓${TrafficStats.formatBytes(totalDownload)} ↑${TrafficStats.formatBytes(totalUpload)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
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
                    "⚠️ TCP Ping не гарантирует работу VPN!\nДля реальной проверки - подключитесь и нажмите Check IP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    // Favorite star
                    Text(
                        text = if (node.isFavorite) "⭐" else "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
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
                // Favorite button
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (node.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite"
                    )
                }
                
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
