package com.raccoonsquad.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raccoonsquad.core.util.NodeTester
import com.raccoonsquad.core.cosmetic.CosmeticPresets
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.repository.NodeRepository
import com.raccoonsquad.data.parser.UriParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class NodeUiState(
    val id: String,
    val index: Int,
    val name: String,
    val server: String,
    val cosmetics: String = "",
    val latency: Long? = null,
    val isActive: Boolean,
    val hasFragment: Boolean,
    val hasNoise: Boolean,
    val mtu: String,
    val isFavorite: Boolean = false,
    val ratingScore: Int = 0,        // 0-100 smart rating
    val ratingStars: Int = 0,         // 1-5 stars
    val countryFlag: String = "🌐",   // Emoji flag
    val countryName: String = "",     // Country name in Russian
    val config: VlessConfig
)

class NodeViewModel(application: Application) : AndroidViewModel(application) {
    
    // Use singleton to ensure same DataStore instance
    private val repository = NodeRepository.getInstance(application)
    
    // Use Eagerly to keep Flow alive and retain data across screen navigation
    val nodes: StateFlow<List<VlessConfig>> = repository.nodes
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    
    val activeNodeId: StateFlow<String?> = repository.activeNodeId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()
    
    private val _importCount = MutableStateFlow(0)
    val importCount: StateFlow<Int> = _importCount.asStateFlow()
    
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    // Testing states
    private val _testingIds = MutableStateFlow<Set<String>>(emptySet())
    val testingUuids: StateFlow<Set<String>> = _testingIds.asStateFlow()
    
    private val _isAutoTesting = MutableStateFlow(false)
    val isAutoTesting: StateFlow<Boolean> = _isAutoTesting.asStateFlow()
    
    // Progress tracking
    private val _testProgress = MutableStateFlow<Pair<Int, Int>>(0 to 0)  // tested / total
    val testProgress: StateFlow<Pair<Int, Int>> = _testProgress.asStateFlow()
    
    // Test result for alerts
    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()
    
    // Test job for cancellation
    private var testJob: kotlinx.coroutines.Job? = null
    
    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        _isAutoTesting.value = false
        _testingIds.value = emptySet()
        _testProgress.value = 0 to 0
        _bruteForceState.value = BruteForceState.Idle
    }
    
    /**
     * Import nodes from text (clipboard/manual)
     */
    fun importNodes(text: String) {
        LogManager.i("ViewModel", "=== importNodes() called, text length=${text.length} ===")
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            
            try {
                LogManager.d("ViewModel", "Parsing text...")
                val configs = withContext(Dispatchers.Default) {
                    UriParser.parseMultiple(text)
                }
                
                LogManager.i("ViewModel", "Parsed ${configs.size} configs")
                
                if (configs.isEmpty()) {
                    LogManager.e("ViewModel", "No VLESS links found!")
                    _importError.value = "Не найдено VLESS ссылок"
                    _isImporting.value = false
                    return@launch
                }
                
                LogManager.d("ViewModel", "Saving to repository...")
                withContext(Dispatchers.IO) {
                    repository.addNodes(configs)
                }
                
                LogManager.i("ViewModel", "=== importNodes() SUCCESS: ${configs.size} nodes ===")
                _importCount.value = configs.size
            } catch (e: Exception) {
                LogManager.e("ViewModel", "importNodes() FAILED: ${e.message}", e)
                _importError.value = "Ошибка: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }
    
    /**
     * Import from subscription URL
     */
    fun importFromUrl(url: String) {
        LogManager.i("ViewModel", "=== importFromUrl() called, url=$url ===")
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            
            try {
                LogManager.d("ViewModel", "Fetching subscription...")
                val result = NodeTester.fetchSubscription(url)
                
                result.fold(
                    onSuccess = { content ->
                        LogManager.i("ViewModel", "Fetched ${content.length} bytes, parsing...")
                        val configs = withContext(Dispatchers.Default) {
                            UriParser.parseMultiple(content)
                        }
                        
                        LogManager.i("ViewModel", "Parsed ${configs.size} configs from subscription")
                        
                        if (configs.isEmpty()) {
                            LogManager.e("ViewModel", "No VLESS links in subscription!")
                            _importError.value = "В подписке нет VLESS ссылок"
                        } else {
                            LogManager.d("ViewModel", "Saving to repository...")
                            withContext(Dispatchers.IO) {
                                repository.addNodes(configs)
                            }
                            LogManager.i("ViewModel", "=== importFromUrl() SUCCESS: ${configs.size} nodes ===")
                            _importCount.value = configs.size
                        }
                    },
                    onFailure = { error ->
                        LogManager.e("ViewModel", "Fetch FAILED: ${error.message}")
                        _importError.value = "Ошибка загрузки: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                LogManager.e("ViewModel", "importFromUrl() CRASHED: ${e.message}", e)
                _importError.value = "Ошибка: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }
    
    /**
     * Test single node - updates latency directly in config
     */
    fun testNode(config: VlessConfig, method: NodeTester.TestMethod = NodeTester.TestMethod.TCP) {
        viewModelScope.launch {
            _testingIds.value = _testingIds.value + config.id
            
            val result = NodeTester.testNode(config.serverAddress, config.port, method)
            
            _testingIds.value = _testingIds.value - config.id
            
            // Update latency directly in the config
            val updatedConfig = config.copy(latency = if (result.success) result.latencyMs else -1L)
            withContext(Dispatchers.IO) {
                repository.updateNode(updatedConfig)
            }
        }
    }
    
    /**
     * Test all nodes - updates latency directly in configs, batch update at end
     */
    fun testAllNodes(method: NodeTester.TestMethod = NodeTester.TestMethod.TCP) {
        testJob?.cancel()  // Cancel any existing test
        testJob = viewModelScope.launch {
            val allNodes = nodes.value
            val total = allNodes.size
            _testingIds.value = allNodes.map { it.id }.toSet()
            _isAutoTesting.value = true
            _testProgress.value = 0 to total
            
            val updatedConfigs = mutableListOf<VlessConfig>()
            var tested = 0
            
            allNodes.forEach { config ->
                // Check if cancelled
                if (!isActive) {
                    LogManager.i("ViewModel", "Test cancelled by user")
                    return@forEach
                }
                
                val result = NodeTester.testNode(config.serverAddress, config.port, method)
                val updatedConfig = config.copy(latency = if (result.success) result.latencyMs else -1L)
                updatedConfigs.add(updatedConfig)
                
                // Update progress
                tested++
                _testProgress.value = tested to total
                _testingIds.value = _testingIds.value - config.id
            }
            
            // Batch update all configs at once - much more efficient!
            withContext(Dispatchers.IO) {
                repository.updateAllNodes(updatedConfigs)
            }
            
            // Calculate results
            val successCount = updatedConfigs.count { it.latency != null && it.latency > 0 }
            val failCount = updatedConfigs.size - successCount
            _testResult.value = "✅ TCP Ping завершён: $successCount работает, $failCount недоступно"
            
            _testingIds.value = emptySet()
            _isAutoTesting.value = false
            _testProgress.value = 0 to 0
            testJob = null
        }
    }
    
    /**
     * Test all nodes with URL test (connects to each node individually)
     */
    fun testAllNodesWithUrl() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            val allNodes = nodes.value
            val total = allNodes.size
            _isAutoTesting.value = true
            _testProgress.value = 0 to total
            
            val updatedConfigs = mutableListOf<VlessConfig>()
            var tested = 0
            
            allNodes.forEach { config ->
                // Check if cancelled
                if (!isActive) {
                    LogManager.i("ViewModel", "URL test cancelled by user")
                    return@forEach
                }
                
                // For each node, create a test connection
                val result = NodeTester.testNodeUrl(config)
                val updatedConfig = config.copy(
                    latency = if (result.success) result.latencyMs else -1L,
                    lastUrlLatency = if (result.success) result.latencyMs else null,
                    lastTestTime = System.currentTimeMillis()
                )
                updatedConfigs.add(updatedConfig)
                
                tested++
                _testProgress.value = tested to total
            }
            
            // Batch update
            withContext(Dispatchers.IO) {
                repository.updateAllNodes(updatedConfigs)
            }
            
            // Calculate results
            val successCount = updatedConfigs.count { it.lastUrlLatency != null && it.lastUrlLatency > 0 }
            val failCount = updatedConfigs.size - successCount
            _testResult.value = "✅ URL тест завершён: $successCount работает, $failCount недоступно"
            
            _isAutoTesting.value = false
            _testProgress.value = 0 to 0
            testJob = null
        }
    }
    
    /**
     * Parallel URL test - tests multiple nodes at once for speed
     * Uses concurrency limit to avoid overwhelming the system
     */
    fun testAllNodesWithUrlParallel(concurrency: Int = 5) {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            val allNodes = nodes.value
            val total = allNodes.size
            _isAutoTesting.value = true
            _testProgress.value = 0 to total
            
            // Use a mutex for thread-safe progress updates
            var tested = 0
            val progressLock = Any()
            
            // Partition nodes into batches for parallel processing
            val results = allNodes.mapIndexed { index, config ->
                async(Dispatchers.IO) {
                    // Check if cancelled
                    if (!isActive) {
                        null
                    } else {
                        val result = NodeTester.testNodeUrl(config)
                        val updatedConfig = config.copy(
                            latency = if (result.success) result.latencyMs else -1L,
                            lastUrlLatency = if (result.success) result.latencyMs else null,
                            lastTestTime = System.currentTimeMillis()
                        )
                        
                        // Update progress
                        synchronized(progressLock) {
                            tested++
                            _testProgress.value = tested to total
                        }
                        
                        updatedConfig
                    }
                }
            }.awaitAll().filterNotNull()
            
            // Batch update
            withContext(Dispatchers.IO) {
                repository.updateAllNodes(results)
            }
            
            // Calculate results
            val successCount = results.count { it.lastUrlLatency != null && it.lastUrlLatency > 0 }
            val failCount = results.size - successCount
            _testResult.value = "✅ URL тест (${concurrency}x): $successCount работает, $failCount недоступно"
            
            _isAutoTesting.value = false
            _testProgress.value = 0 to 0
            testJob = null
        }
    }
    
    /**
     * Test all nodes through active VPN connection
     * This tests real connectivity by switching to each node and testing
     */
    fun testAllNodesThroughVpn() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            val allNodes = nodes.value
            val total = allNodes.size
            _isAutoTesting.value = true
            _testProgress.value = 0 to total
            
            val updatedConfigs = mutableListOf<VlessConfig>()
            var tested = 0
            
            allNodes.forEach { config ->
                // Check if cancelled
                if (!isActive) {
                    LogManager.i("ViewModel", "VPN test cancelled by user")
                    return@forEach
                }
                
                // Switch to this node
                setActiveNode(config.id)
                
                // Wait for connection (handled by VPN service)
                kotlinx.coroutines.delay(2000)
                
                // Test through the active proxy
                val result = NodeTester.testThroughActiveProxy()
                val updatedConfig = config.copy(
                    latency = if (result.success) result.latencyMs else -1L,
                    lastUrlLatency = if (result.success) result.latencyMs else null,
                    lastTestTime = System.currentTimeMillis()
                )
                updatedConfigs.add(updatedConfig)
                
                tested++
                _testProgress.value = tested to total
            }
            
            // Batch update
            withContext(Dispatchers.IO) {
                repository.updateAllNodes(updatedConfigs)
            }
            
            // Calculate results
            val successCount = updatedConfigs.count { it.lastUrlLatency != null && it.lastUrlLatency > 0 }
            val failCount = updatedConfigs.size - successCount
            _testResult.value = "✅ VPN тест завершён: $successCount работает, $failCount недоступно"
            
            _isAutoTesting.value = false
            _testProgress.value = 0 to 0
            testJob = null
        }
    }
    
    /**
     * Auto-test and remove failed nodes
     */
    fun autoTestAndClean(method: NodeTester.TestMethod = NodeTester.TestMethod.TCP) {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _isAutoTesting.value = true
            
            val allNodes = nodes.value
            val total = allNodes.size
            _testProgress.value = 0 to total
            
            val failedIds = mutableListOf<String>()
            var tested = 0
            
            allNodes.forEach { config ->
                // Check if cancelled
                if (!isActive) {
                    LogManager.i("ViewModel", "Auto-clean cancelled by user")
                    return@forEach
                }
                
                val result = NodeTester.testNode(config.serverAddress, config.port, method)
                if (!result.success) {
                    failedIds.add(config.id)
                }
                
                tested++
                _testProgress.value = tested to total
            }
            
            // Remove failed nodes
            if (failedIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    failedIds.forEach { repository.deleteNode(it) }
                }
                _importCount.value = -failedIds.size // Negative to indicate removal
            }
            
            _isAutoTesting.value = false
        }
    }
    
    fun updateNode(config: VlessConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateNode(config)
        }
    }
    
    fun deleteNode(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNode(id)
        }
    }
    
    fun clearAllNodes() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }
    
    /**
     * Export all nodes as VLESS URIs (one per line)
     */
    fun exportNodes(): String {
        val allNodes = nodes.value
        return allNodes.joinToString("\n") { config ->
            com.raccoonsquad.data.parser.UriParser.generateUri(config)
        }
    }
    
    /**
     * Export nodes count
     */
    fun getExportCount(): Int = nodes.value.size
    
    fun setActiveNode(id: String?) {
        viewModelScope.launch {
            repository.setActiveNode(id)
        }
    }
    
    fun toggleFavorite(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(id)
        }
    }
    
    /**
     * Apply cosmetic preset to a single node
     */
    fun applyPreset(id: String, presetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val allNodes = nodes.value
            val node = allNodes.find { it.id == id } ?: return@launch
            val updated = CosmeticPresets.applyPreset(presetId, node)
            repository.updateNode(updated)
        }
    }
    
    /**
     * Apply cosmetic preset to all nodes
     */
    fun applyPresetToAll(presetId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val allNodes = nodes.value
            allNodes.forEach { node ->
                val updated = CosmeticPresets.applyPreset(presetId, node)
                repository.updateNode(updated)
            }
        }
    }
    
    /**
     * Randomize cosmetics for a single node
     */
    fun randomizeCosmetics(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val allNodes = nodes.value
            val node = allNodes.find { it.id == id } ?: return@launch
            val updated = CosmeticPresets.randomize(node)
            repository.updateNode(updated)
        }
    }
    
    /**
     * Randomize cosmetics for all nodes (each gets unique random settings)
     */
    fun randomizeAllCosmetics() {
        viewModelScope.launch(Dispatchers.IO) {
            val allNodes = nodes.value
            allNodes.forEach { node ->
                val updated = CosmeticPresets.randomize(node)
                repository.updateNode(updated)
            }
        }
    }
    
    fun clearImportError() {
        _importError.value = null
    }
    
    fun resetImportCount() {
        _importCount.value = 0
    }
    
    // URL test state
    private val _urlTestResult = MutableStateFlow<String?>(null)
    val urlTestResult: StateFlow<String?> = _urlTestResult.asStateFlow()
    
    /**
     * Test URL through active VPN connection and update rating stats
     */
    fun testUrlThroughVpn() {
        viewModelScope.launch {
            val activeConfig = nodes.value.find { it.id == activeNodeId.value }
            if (activeConfig == null) {
                _urlTestResult.value = "❌ Нет активной ноды"
                return@launch
            }
            
            _urlTestResult.value = "testing"
            
            withContext(Dispatchers.IO) {
                try {
                    val startTime = System.currentTimeMillis()
                    
                    val proxy = java.net.Proxy(
                        java.net.Proxy.Type.HTTP,
                        java.net.InetSocketAddress("127.0.0.1", 10809)
                    )
                    val client = okhttp3.OkHttpClient.Builder()
                        .proxy(proxy)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    
                    val request = okhttp3.Request.Builder()
                        .url("https://www.google.com/generate_204")
                        .build()
                    
                    val response = client.newCall(request).execute()
                    val latency = System.currentTimeMillis() - startTime
                    
                    val success = response.isSuccessful || response.code == 204
                    
                    // Update rating stats
                    val updatedConfig = activeConfig.copy(
                        connectionSuccess = activeConfig.connectionSuccess + if (success) 1 else 0,
                        connectionFails = activeConfig.connectionFails + if (success) 0 else 1,
                        lastUrlLatency = if (success) latency else -1L,
                        lastTestTime = System.currentTimeMillis()
                    )
                    
                    repository.updateNode(updatedConfig)
                    
                    withContext(Dispatchers.Main) {
                        if (success) {
                            _urlTestResult.value = "✅ URL тест: ${latency}ms"
                        } else {
                            _urlTestResult.value = "❌ HTTP ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    // Update failure stats
                    val updatedConfig = activeConfig.copy(
                        connectionFails = activeConfig.connectionFails + 1,
                        lastUrlLatency = -1L,
                        lastTestTime = System.currentTimeMillis()
                    )
                    repository.updateNode(updatedConfig)
                    
                    withContext(Dispatchers.Main) {
                        _urlTestResult.value = "❌ Ошибка: ${e.message?.take(30)}"
                    }
                }
            }
        }
    }
    
    fun clearUrlTestResult() {
        _urlTestResult.value = null
    }
    
    // Brute force state
    private val _bruteForceState = MutableStateFlow<BruteForceState>(BruteForceState.Idle)
    val bruteForceState: StateFlow<BruteForceState> = _bruteForceState.asStateFlow()
    
    sealed class BruteForceState {
        object Idle : BruteForceState()
        data class Running(val attempt: Int, val maxAttempts: Int, val strategy: String = "") : BruteForceState()
        data class Success(val attempt: Int, val latency: Long) : BruteForceState()
        data class Failed(val attempts: Int, val reason: String = "") : BruteForceState()
    }

    // Error pattern -> Cosmetic strategy mapping
    private object BruteForceStrategies {
        data class Strategy(
            val name: String,
            val description: String,
            val apply: (VlessConfig) -> VlessConfig
        )

        // Common Xray errors and their fixes
        // NOTE: Fingerprint changes removed - can break Reality nodes!
        val STRATEGIES = listOf(
            // Strategy 1: Fragmentation for DPI bypass (most common fix)
            Strategy("fragment", "Фрагментация (DPI bypass)") { config ->
                config.copy(
                    fragmentationEnabled = true,
                    fragmentPackets = "tlshello",
                    fragmentLength = "100-200",
                    fragmentInterval = "10-20"
                )
            },
            // Strategy 2: Random fragmentation (variety for DPI)
            Strategy("random_frag", "Случайная фрагментация") { config ->
                config.copy(
                    fragmentationEnabled = true,
                    fragmentPackets = "1-3",
                    fragmentLength = "${(5..20).random()}-${(20..50).random()}",
                    fragmentInterval = "${(10..30).random()}-${(30..60).random()}"
                )
            },
            // Strategy 3: Noise + Fragment (for aggressive DPI)
            Strategy("noise_frag", "Шум + Фрагмент") { config ->
                config.copy(
                    fragmentationEnabled = true,
                    fragmentPackets = "1-3",
                    fragmentLength = "10-20",
                    fragmentInterval = "10-20",
                    noiseEnabled = true,
                    noiseType = "random",
                    noisePacketSize = "5-10",
                    noiseDelay = "10-20"
                )
            },
            // Strategy 4: Max fragmentation (for heavy DPI)
            Strategy("max_frag", "Макс. фрагментация") { config ->
                config.copy(
                    fragmentationEnabled = true,
                    fragmentPackets = "1-5",
                    fragmentLength = "5-15",
                    fragmentInterval = "5-15"
                )
            },
            // Strategy 5: Noise only (subtle obfuscation)
            Strategy("noise", "Только шум") { config ->
                config.copy(
                    noiseEnabled = true,
                    noiseType = "random",
                    noisePacketSize = "10-20",
                    noiseDelay = "5-15"
                )
            }
        )

        // Error patterns that indicate node is DEAD (not DPI issue)
        private val DEAD_NODE_ERRORS = listOf(
            "connection refused",
            "no route to host",
            "network unreachable",
            "name or service not known",
            "dns",
            "timeout"
        )

        // Error patterns that indicate DPI/blocked
        // "timeout" is often DPI - server reachable but TLS is blocked
        private val DPI_ERRORS = listOf(
            "connection reset",
            "rst",
            "blocked",
            "tls handshake",
            "handshake failure",
            "timeout"
        )

        // Detect if node is completely dead (not a DPI issue)
        fun isNodeDead(config: VlessConfig, lastError: String? = null): Boolean {
            // Check error log for dead node patterns
            if (lastError != null) {
                val errorLower = lastError.lowercase()
                if (DEAD_NODE_ERRORS.any { errorLower.contains(it) }) {
                    return true
                }
            }
            
            // Also check stats: if 3+ tests and 0 success = dead node
            val totalTests = config.connectionSuccess + config.connectionFails
            return totalTests >= 3 && config.connectionSuccess == 0
        }

        // Detect if error is DPI-related
        fun isDPIIssue(lastError: String?): Boolean {
            if (lastError == null) return false
            val errorLower = lastError.lowercase()
            return DPI_ERRORS.any { errorLower.contains(it) }
        }

        // Get recommended strategy based on error type
        fun getStrategyForError(errorLog: String?): Strategy {
            // DPI errors -> use fragment strategies
            if (isDPIIssue(errorLog)) {
                return STRATEGIES[0] // fragment
            }
            
            // Unknown/random error -> try random strategy
            return STRATEGIES.random()
        }
        
        // Get human-readable diagnosis
        fun diagnoseError(errorLog: String?, config: VlessConfig): String {
            // Check DPI FIRST - more specific than timeout/dead node
            if (isDPIIssue(errorLog)) {
                return "DPI блокировка - косметика поможет"
            }
            
            return when {
                isNodeDead(config, errorLog) -> "Нода недоступна - сервер не отвечает"
                errorLog?.contains("reality", ignoreCase = true) == true -> "Ошибка Reality - проверьте ключи"
                errorLog?.contains("eof", ignoreCase = true) == true -> "Сервер сбросил соединение"
                errorLog?.contains("config", ignoreCase = true) == true -> "Ошибка конфигурации"
                errorLog != null -> "Ошибка: ${errorLog.take(50)}"
                else -> "Причина неизвестна"
            }
        }
    }
    
    /**
     * Brute force cosmetics - smart approach with error-based strategies
     * Requires VPN to be connected to this node first
     */
    fun bruteForceCosmetics(
        config: VlessConfig,
        onReconnectNeeded: (VlessConfig) -> Unit
    ) {
        LogManager.i("ViewModel", "bruteForceCosmetics() called for: ${config.name}")
        
        // Don't start if already running
        if (testJob?.isActive == true) {
            LogManager.w("ViewModel", "Brute force already running, ignoring duplicate call")
            return
        }
        
        testJob?.cancel()
        testJob = viewModelScope.launch {
            LogManager.i("ViewModel", "bruteForceCosmetics coroutine started")
            val maxAttempts = 10  // Reduced from 20 since we use smart strategies
            var currentConfig = config
            lastBruteForceError = null  // Reset error before starting

            // Check if node is completely dead first (dead server, not DPI)
            val isDead = BruteForceStrategies.isNodeDead(config)
            LogManager.d("ViewModel", "isNodeDead check: $isDead (success=${config.connectionSuccess}, fails=${config.connectionFails})")
            if (isDead) {
                val diagnosis = BruteForceStrategies.diagnoseError(null, config)
                LogManager.w("ViewModel", "Node is dead, diagnosis: $diagnosis")
                _bruteForceState.value = BruteForceState.Failed(0, diagnosis)
                testJob = null
                return@launch
            }

            // First attempt: test current config
            LogManager.i("ViewModel", "Starting first attempt - testing current config")
            _bruteForceState.value = BruteForceState.Running(1, maxAttempts, "Тест текущей конфигурации")
            onReconnectNeeded(currentConfig)
            kotlinx.coroutines.delay(3000)

            var result = testUrlDirect(currentConfig)
            if (result.first) {
                _bruteForceState.value = BruteForceState.Success(1, result.second)
                repository.updateNode(currentConfig)
                testJob = null
                return@launch
            }

            // Try strategies based on errors
            for (attempt in 2..maxAttempts) {
                if (!isActive) {
                    LogManager.i("ViewModel", "Brute force cancelled by user")
                    _bruteForceState.value = BruteForceState.Idle
                    return@launch
                }

                // Get strategy based on attempt number (cycle through strategies)
                val strategy = BruteForceStrategies.STRATEGIES[(attempt - 2) % BruteForceStrategies.STRATEGIES.size]
                _bruteForceState.value = BruteForceState.Running(attempt, maxAttempts, strategy.description)

                // Apply strategy
                currentConfig = strategy.apply(config)

                // Notify UI to reconnect VPN with new config
                onReconnectNeeded(currentConfig)

                // Wait for VPN to establish
                kotlinx.coroutines.delay(3000)

                // Test URL
                result = testUrlDirect(currentConfig)

                if (result.first) {
                    // Found working config!
                    repository.updateNode(currentConfig)
                    _bruteForceState.value = BruteForceState.Success(attempt, result.second)
                    testJob = null
                    return@launch
                }
            }

            // All attempts failed - provide diagnosis
            LogManager.w("ViewModel", "Brute force failed. Last error: $lastBruteForceError")
            val diagnosis = BruteForceStrategies.diagnoseError(lastBruteForceError, config)
            LogManager.w("ViewModel", "Diagnosis: $diagnosis")
            _bruteForceState.value = BruteForceState.Failed(maxAttempts, diagnosis)
            testJob = null
        }
    }
    
    /**
     * Start brute force for current active node
     */
    fun bruteForceActiveNode(onReconnectNeeded: (VlessConfig) -> Unit) {
        val activeConfig = nodes.value.find { it.id == activeNodeId.value }
        if (activeConfig != null) {
            LogManager.i("ViewModel", "Starting brute force for: ${activeConfig.name}")
            bruteForceCosmetics(activeConfig, onReconnectNeeded)
        } else {
            LogManager.w("ViewModel", "bruteForceActiveNode: No active node! activeNodeId=${activeNodeId.value}")
            _testResult.value = "❌ Сначала выберите и подключите ноду"
        }
    }
    
    private suspend fun testUrlDirect(config: VlessConfig): Pair<Boolean, Long> {
        return withContext(Dispatchers.IO) {
            // Use XrayWrapper.testConfigWithError() - tests SPECIFIC config, returns error too
            // This avoids false positives when old Xray is still running
            val (latency, error) = com.raccoonsquad.core.xray.XrayWrapper.testConfigWithError(config)
            
            // Save error for diagnosis
            lastBruteForceError = error
            LogManager.d("ViewModel", "testUrlDirect: latency=$latency, error=$error")
            
            val success = latency > 0
            
            // Update rating stats
            val updatedConfig = config.copy(
                connectionSuccess = config.connectionSuccess + if (success) 1 else 0,
                connectionFails = config.connectionFails + if (success) 0 else 1,
                lastUrlLatency = if (success) latency else -1L,
                lastTestTime = System.currentTimeMillis()
            )
            repository.updateNode(updatedConfig)
            
            Pair(success, if (success) latency else 0L)
        }
    }
    
    // Store last error for diagnosis
    private var lastBruteForceError: String? = null
    
    fun resetBruteForceState() {
        _bruteForceState.value = BruteForceState.Idle
    }
    
    fun resetTestResult() {
        _testResult.value = null
    }
    
    /**
     * Quick clean - delete nodes that are already marked as failed (latency=-1)
     * No re-testing, just removes known bad nodes
     */
    fun quickCleanFailedNodes() {
        viewModelScope.launch {
            val allNodes = nodes.value
            
            // Find nodes with failed status (latency = -1 means failed last test)
            val failedNodes = allNodes.filter { it.latency != null && it.latency < 0 }
            val untestedNodes = allNodes.filter { it.latency == null }
            
            if (failedNodes.isEmpty()) {
                _testResult.value = "✅ Нет нерабочих нод для удаления"
                return@launch
            }
            
            // Delete failed nodes
            withContext(Dispatchers.IO) {
                failedNodes.forEach { repository.deleteNode(it.id) }
            }
            
            _testResult.value = "🗑️ Удалено ${failedNodes.size} нерабочих нод (осталось ${untestedNodes.size + allNodes.count { it.latency != null && it.latency > 0 }})"
            _importCount.value = -failedNodes.size
        }
    }
    
    /**
     * Smart clean - checks TCP reachability and URL connectivity, removes all failed nodes
     * First does TCP ping for all, then URL test for TCP-passing nodes
     */
    fun smartCleanNodes() {
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _isAutoTesting.value = true
            val allNodes = nodes.value
            val total = allNodes.size
            _testProgress.value = 0 to total
            
            // Track which nodes pass each test
            val tcpPassed = mutableListOf<VlessConfig>()
            var tested = 0
            
            // Phase 1: TCP ping test
            allNodes.forEach { config ->
                if (!isActive) {
                    LogManager.i("ViewModel", "Smart clean cancelled during TCP phase")
                    return@launch
                }
                
                val result = NodeTester.testNode(config.serverAddress, config.port, NodeTester.TestMethod.TCP)
                if (result.success) {
                    tcpPassed.add(config)
                }
                
                tested++
                _testProgress.value = tested to total
            }
            
            // Phase 2: URL test for TCP-passing nodes
            val urlPassed = mutableListOf<VlessConfig>()
            tcpPassed.forEach { config ->
                if (!isActive) {
                    LogManager.i("ViewModel", "Smart clean cancelled during URL phase")
                    return@launch
                }
                
                val result = NodeTester.testNodeUrl(config)
                if (result.success) {
                    urlPassed.add(config.copy(
                        latency = result.latencyMs,
                        lastUrlLatency = result.latencyMs,
                        lastTestTime = System.currentTimeMillis()
                    ))
                }
                
                tested++
                _testProgress.value = tested to total
            }
            
            // Calculate nodes to remove
            val failedIds = allNodes.filter { node ->
                !urlPassed.any { it.id == node.id }
            }.map { it.id }
            
            // Remove failed nodes
            if (failedIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    failedIds.forEach { repository.deleteNode(it) }
                }
                _importCount.value = -failedIds.size
            }
            
            // Update passed nodes with new latency data
            if (urlPassed.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.updateAllNodes(urlPassed)
                }
            }
            
            _testResult.value = "🗑️ Удалено ${failedIds.size} нерабочих нод (осталось ${urlPassed.size})"
            _isAutoTesting.value = false
            _testProgress.value = 0 to 0
            testJob = null
        }
    }
    
    fun toUiState(config: VlessConfig, activeId: String?, index: Int): NodeUiState {
        val countryCode = com.raccoonsquad.core.util.CountryFlags.detectCountry(config.serverAddress)
        return NodeUiState(
            id = config.id,
            index = index,
            name = config.getDisplayName(),
            server = "${config.serverAddress}:${config.port}",
            cosmetics = config.getCosmeticsInfo(),
            latency = config.latency,
            isActive = config.id == activeId,
            hasFragment = config.fragmentationEnabled,
            hasNoise = config.noiseEnabled,
            mtu = config.mtu,
            isFavorite = config.isFavorite,
            ratingScore = config.calculateScore(),
            ratingStars = config.getRatingStars(),
            countryFlag = com.raccoonsquad.core.util.CountryFlags.getFlag(countryCode),
            countryName = com.raccoonsquad.core.util.CountryFlags.getCountryName(countryCode),
            config = config
        )
    }
    
    /**
     * Get the best node for auto-connection
     * Priority: Working nodes > Favorites > Best rating
     */
    fun getBestNode(): VlessConfig? {
        val allNodes = nodes.value
        if (allNodes.isEmpty()) return null
        
        // Filter working nodes (latency > 0 means tested and working)
        val workingNodes = allNodes.filter { node ->
            node.latency != null && node.latency > 0
        }
        
        // If no tested nodes, return first favorite or first node
        if (workingNodes.isEmpty()) {
            return allNodes.firstOrNull { it.isFavorite } ?: allNodes.first()
        }
        
        // Sort working nodes by: favorite first, then by score (descending), then by latency (ascending)
        return workingNodes.sortedWith(
            compareByDescending<VlessConfig> { it.isFavorite }
                .thenByDescending { it.calculateScore() }
                .thenBy { it.latency ?: Long.MAX_VALUE }
        ).first()
    }
}
