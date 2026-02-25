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
    
    // Test job for cancellation
    private var testJob: kotlinx.coroutines.Job? = null
    
    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        _isAutoTesting.value = false
        _testingIds.value = emptySet()
        _testProgress.value = 0 to 0
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
    
    fun toUiState(config: VlessConfig, activeId: String?, index: Int): NodeUiState {
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
            config = config
        )
    }
}
