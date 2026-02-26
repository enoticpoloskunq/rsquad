package com.raccoonsquad.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.raccoonsquad.core.diagnosis.Doctor
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.settings.SettingsManager
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
    val testProgress by viewModel.testProgress.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    
    var showImportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCosmeticDialog by remember { mutableStateOf(false) }
    var showDoctorDialog by remember { mutableStateOf(false) }
    
    var sortOrder by remember { mutableStateOf(SortOrder.FAVORITES_FIRST) }
    
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Simple state - just track if we've shown nodes at least once
    var hasLoadedNodes by remember { mutableStateOf(false) }
    
    // Update hasLoadedNodes when nodes appear
    LaunchedEffect(nodes.size) {
        if (nodes.isNotEmpty()) {
            hasLoadedNodes = true
        }
    }
    
    // Use all nodes directly - Compose handles large lists efficiently with LazyColumn
    val visibleNodeCount = nodes.size
    
    // Create UI states - simple mapping without derivedStateOf to ensure updates
    val uiStates = nodes.take(visibleNodeCount).mapIndexed { index, config -> 
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
            ratingScore = config.calculateScore(),
            ratingStars = config.getRatingStars(),
            config = config
        )
    }.let { list ->
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
    
    // Handle test result alerts
    LaunchedEffect(testResult) {
        testResult?.let { result ->
            snackbarHostState.showSnackbar(result)
            viewModel.resetTestResult()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = if (isVpnActive) "🦝 Active" else "🦝 Raccoon Squad",
                            maxLines = 1
                        )
                        if (uiStates.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${uiStates.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
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
                        IconButton(onClick = { showDoctorDialog = true }) {
                            Icon(Icons.Default.Healing, "Doctor")
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
                                // Use HTTP proxy provided by Xray (127.0.0.1:10809) - more reliable than SOCKS5
                                val proxy = java.net.Proxy(
                                    java.net.Proxy.Type.HTTP,
                                    java.net.InetSocketAddress("127.0.0.1", 10809)
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
                            onFavorite = { viewModel.toggleFavorite(node.id) }
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
    
    // Test Dialog - simplified, only TCP Ping and URL test
    if (showTestDialog) {
        TestDialog(
            isAutoTesting = isAutoTesting,
            testProgress = testProgress,
            onTestAllTcp = {
                viewModel.testAllNodes(NodeTester.TestMethod.TCP)
                showTestDialog = false
            },
            onTestAllUrl = {
                viewModel.testAllNodesWithUrlParallel(5)
                showTestDialog = false
            },
            onCancelTest = {
                viewModel.cancelTest()
            },
            onQuickClean = {
                viewModel.quickCleanFailedNodes()
            },
            onSmartClean = {
                viewModel.smartCleanNodes()
                showTestDialog = false
            },
            onDismiss = {
                showTestDialog = false
            }
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
    
    // Doctor Dialog
    if (showDoctorDialog) {
        val bruteForceState by viewModel.bruteForceState.collectAsState()
        
        DoctorDialog(
            activeConfig = activeNode?.config,
            isVpnActive = isVpnActive,
            bruteForceState = bruteForceState,
            onBruteForce = {
                LogManager.i("HomeScreen", "Brute force button clicked! activeId=$activeId")
                viewModel.bruteForceActiveNode { updatedConfig ->
                    LogManager.i("HomeScreen", "Brute force reconnecting with config: ${updatedConfig.name}")
                    // Reconnect VPN with new config
                    VpnController.stopVpn(context)
                    viewModel.setActiveNode(null)
                    GlobalScope.launch {
                        kotlinx.coroutines.delay(500)
                        VpnController.startVpn(context, updatedConfig)
                        viewModel.setActiveNode(updatedConfig.id)
                    }
                }
            },
            onDismiss = { 
                showDoctorDialog = false
                viewModel.resetBruteForceState()
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
    testProgress: Pair<Int, Int> = 0 to 0,
    onTestAllTcp: () -> Unit,
    onTestAllUrl: () -> Unit,
    onCancelTest: () -> Unit = {},
    onQuickClean: () -> Unit = {},
    onSmartClean: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val (tested, total) = testProgress
    val isTesting = isAutoTesting || total > 0
    
    AlertDialog(
        onDismissRequest = if (isTesting) { {} } else onDismiss,
        title = { Text("Тестирование") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Progress indicator
                if (isTesting) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Тестирование: $tested / $total",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { if (total > 0) tested.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onCancelTest,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("❌ Отменить")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // TCP Ping section
                Text("Быстрая проверка (TCP Ping):", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Проверяет только доступность порта сервера",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Button(
                    onClick = onTestAllTcp,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔍 TCP Ping всех нод")
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // URL Test section
                Text("Реальная проверка:", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Проверяет подключение к серверу",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Button(
                    onClick = onTestAllUrl,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("🌐 URL тест всех нод")
                }
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Clean section
                Text("Очистка:", style = MaterialTheme.typography.labelMedium)
                
                Button(
                    onClick = onQuickClean,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("🗑️ Удалить нерабочие (быстро)")
                }
                
                Text(
                    "Удаляет ноды с latency = ✗",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Button(
                    onClick = onSmartClean,
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text("🧹 Умная очистка")
                }
                
                Text(
                    "TCP + URL проверка, удаление нерабочих",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (!isTesting) {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        },
        dismissButton = {}
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
    // Animate background color when active state changes
    val backgroundColor by animateColorAsState(
        targetValue = if (node.isActive) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else 
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "bgColor"
    )
    
    // Animate elevation when active
    val elevation by animateDpAsState(
        targetValue = if (node.isActive) 4.dp else 1.dp,
        animationSpec = tween(300),
        label = "elevation"
    )
    
    // Animate scale for active node
    val scale by animateDpAsState(
        targetValue = if (node.isActive) 0.dp else 0.dp,
        animationSpec = tween(150),
        label = "scale"
    )
    
    // Stable colors calculated once
    val latencyColor = when {
        node.latency == null -> MaterialTheme.colorScheme.onSurfaceVariant
        node.latency < 100 -> Color(0xFF4CAF50)
        node.latency < 300 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
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
                    // Animated status indicator
                    Text(
                        text = if (node.isActive) "🟢" else "⚪",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.graphicsLayer {
                            alpha = if (node.isActive) 1f else 0.6f
                        }
                    )
                    Text(
                        text = " ${node.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (node.isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    // Ping - always show placeholder or value
                    Text(
                        text = node.latency?.let { latency ->
                            if (latency > 0) "$latency ms" else "✗"
                        } ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (node.latency != null && node.latency > 0) latencyColor 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                
                // Line 2: Server address + Rating
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = node.server,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    // Rating stars
                    if (node.ratingStars > 0) {
                        Text(
                            text = "⭐".repeat(node.ratingStars),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            
            // Favorite button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onFavorite() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (node.isFavorite) "⭐" else "☆",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (node.isFavorite) 
                        Color(0xFFFFC107) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            // Connect/Disconnect button with animation
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
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

@Composable
fun DoctorDialog(
    activeConfig: VlessConfig?,
    isVpnActive: Boolean,
    bruteForceState: NodeViewModel.BruteForceState = NodeViewModel.BruteForceState.Idle,
    onBruteForce: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Doctor.DiagnosisResult?>(null) }
    
    val isBruteForcing = bruteForceState is NodeViewModel.BruteForceState.Running
    val bruteForceDone = bruteForceState is NodeViewModel.BruteForceState.Success || 
                          bruteForceState is NodeViewModel.BruteForceState.Failed
    
    LaunchedEffect(Unit) {
        isRunning = true
        result = Doctor.diagnose(activeConfig, isVpnActive)
        isRunning = false
    }
    
    AlertDialog(
        onDismissRequest = if (isBruteForcing) { {} } else onDismiss,
        icon = { Text("🩺", style = MaterialTheme.typography.headlineMedium) },
        title = { Text("Диагностика") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Brute Force Progress
                when (val state = bruteForceState) {
                    is NodeViewModel.BruteForceState.Running -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF9C27B0).copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "🎲 Подбор косметики: ${state.attempt}/${state.maxAttempts}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (state.strategy.isNotEmpty()) {
                                    Text(
                                        state.strategy,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    is NodeViewModel.BruteForceState.Success -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "✅ Успех! Попытка ${state.attempt}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF4CAF50)
                                )
                                Text("Latency: ${state.latency}ms")
                            }
                        }
                    }
                    is NodeViewModel.BruteForceState.Failed -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "❌ Не удалось за ${state.attempts} попыток",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (state.reason.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Диагноз: ${state.reason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                    else -> {}
                }
                
                // Diagnosis Results
                if (isRunning) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Проверка...")
                    }
                } else if (result != null) {
                    result!!.checks.forEach { check ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (check.success) "✅" else "❌")
                            Column {
                                Text(
                                    check.type.name,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    check.message,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result!!.overallSuccess)
                                Color(0xFF1B5E20).copy(alpha = 0.3f)
                            else
                                Color(0xFFB71C1C).copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            result!!.recommendation,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // Brute Force Button (only if VPN active and not currently running)
                    if (isVpnActive && activeConfig != null && !isBruteForcing && !bruteForceDone) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Text("Подбор косметики:", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Пробует разные настройки маскировки для обхода DPI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onBruteForce,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9C27B0)
                            )
                        ) {
                            Text("🎲 Подобрать косметику")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isBruteForcing) {
                Text("OK")
            }
        }
    )
}
