package com.raccoonsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.repository.NodeRepository
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
    val latency: Long?,
    val isActive: Boolean,
    val hasFragment: Boolean,
    val hasNoise: Boolean,
    val mtu: String,
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
    
    fun importNodes(text: String) {
        viewModelScope.launch {
            _isImporting.value = true
            _importError.value = null
            
            try {
                val configs = withContext(Dispatchers.Default) {
                    repository.parseMultiple(text)
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
    
    fun importNode(uri: String) {
        viewModelScope.launch {
            _importError.value = null
            
            val config = withContext(Dispatchers.Default) {
                repository.importFromUri(uri)
            }
            
            if (config != null) {
                repository.addNode(config)
                _importCount.value = 1
            } else {
                _importError.value = "Неверный VLESS URI"
            }
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
            latency = config.latency,
            isActive = config.uuid == activeUuid,
            hasFragment = config.fragmentationEnabled,
            hasNoise = config.noiseEnabled,
            mtu = config.mtu,
            config = config
        )
    }
}
