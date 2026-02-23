package com.raccoonsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raccoonsquad.core.util.NodeTester
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.repository.NodeRepository
import com.raccoonsquad.data.parser.UriParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    
    private val repository = NodeRepository(application)
    
    val nodes: StateFlow<List<VlessConfig>> = repository.nodes
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val activeNodeUuid: StateFlow<String?> = repository.activeNodeUuid
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()
    
    private val _importCount = MutableStateFlow(0)
    val importCount: StateFlow<Int> = _importCount.asStateFlow()
    
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()
    
    // Testing states
    private val _testingUuids = MutableStateFlow<Set<String>>(emptySet())
    val testingUuids: StateFlow<Set<String>> = _testingUuids.asStateFlow()
    
    private val _testResults = MutableStateFlow<Map<String, Long>>(emptyMap())
    val testResults: StateFlow<Map<String, Long>> = _testResults.asStateFlow()
    
    private val _isAutoTesting = MutableStateFlow(false)
    val isAutoTesting: StateFlow<Boolean> = _isAutoTesting.asStateFlow()
    
    /**
     * Import nodes from text (clipboard/manual)
     */
    fun importNodes(text: String) {
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            
            try {
                val configs = withContext(Dispatchers.Default) {
                    UriParser.parseMultiple(text)
                }
                
                if (configs.isEmpty()) {
                    _importError.value = "Не найдено VLESS ссылок"
                    _isImporting.value = false
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    repository.addNodes(configs)
                }
                
                _importCount.value = configs.size
            } catch (e: Exception) {
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
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            
            try {
                val result = NodeTester.fetchSubscription(url)
                
                result.fold(
                    onSuccess = { content ->
                        val configs = withContext(Dispatchers.Default) {
                            UriParser.parseMultiple(content)
                        }
                        
                        if (configs.isEmpty()) {
                            _importError.value = "В подписке нет VLESS ссылок"
                        } else {
                            withContext(Dispatchers.IO) {
                                repository.addNodes(configs)
                            }
                            _importCount.value = configs.size
                        }
                    },
                    onFailure = { error ->
                        _importError.value = "Ошибка загрузки: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _importError.value = "Ошибка: ${e.message}"
            } finally {
                _isImporting.value = false
            }
        }
    }
    
    /**
     * Test single node
     */
    fun testNode(config: VlessConfig, method: NodeTester.TestMethod = NodeTester.TestMethod.TCP) {
        viewModelScope.launch {
            _testingUuids.value = _testingUuids.value + config.uuid
            
            val result = NodeTester.testNode(config.serverAddress, config.port, method)
            
            _testingUuids.value = _testingUuids.value - config.uuid
            
            if (result.success) {
                _testResults.value = _testResults.value + (config.uuid to result.latencyMs)
            } else {
                _testResults.value = _testResults.value + (config.uuid to -1L)
            }
        }
    }
    
    /**
     * Test all nodes
     */
    fun testAllNodes(method: NodeTester.TestMethod = NodeTester.TestMethod.TCP) {
        viewModelScope.launch {
            val allNodes = nodes.value
            _testingUuids.value = allNodes.map { it.uuid }.toSet()
            
            val results = mutableMapOf<String, Long>()
            
            allNodes.forEach { config ->
                val result = NodeTester.testNode(config.serverAddress, config.port, method)
                results[config.uuid] = if (result.success) result.latencyMs else -1L
                _testResults.value = results.toMap()
            }
            
            _testingUuids.value = emptySet()
        }
    }
    
    /**
     * Auto-test and remove failed nodes
     */
    fun autoTestAndClean(method: NodeTester.TestMethod = NodeTester.TestMethod.TCP) {
        viewModelScope.launch {
            _isAutoTesting.value = true
            
            val allNodes = nodes.value
            val failedUuids = mutableListOf<String>()
            
            allNodes.forEach { config ->
                val result = NodeTester.testNode(config.serverAddress, config.port, method)
                if (!result.success) {
                    failedUuids.add(config.uuid)
                }
            }
            
            // Remove failed nodes
            if (failedUuids.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    failedUuids.forEach { repository.deleteNode(it) }
                }
                _importCount.value = -failedUuids.size // Negative to indicate removal
            }
            
            _isAutoTesting.value = false
        }
    }
    
    fun deleteNode(uuid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNode(uuid)
        }
    }
    
    fun clearAllNodes() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }
    
    fun setActiveNode(uuid: String?) {
        viewModelScope.launch {
            repository.setActiveNode(uuid)
        }
    }
    
    fun toggleFavorite(uuid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(uuid)
        }
    }
    
    fun clearImportError() {
        _importError.value = null
    }
    
    fun resetImportCount() {
        _importCount.value = 0
    }
    
    fun toUiState(config: VlessConfig, activeUuid: String?, index: Int): NodeUiState {
        return NodeUiState(
            id = "${config.uuid}_$index",
            index = index,
            name = config.getDisplayName(),
            server = "${config.serverAddress}:${config.port}",
            cosmetics = config.getCosmeticsInfo(),
            latency = _testResults.value[config.uuid] ?: config.latency,
            isActive = config.uuid == activeUuid,
            hasFragment = config.fragmentationEnabled,
            hasNoise = config.noiseEnabled,
            mtu = config.mtu,
            isFavorite = config.isFavorite,
            config = config
        )
    }
}
