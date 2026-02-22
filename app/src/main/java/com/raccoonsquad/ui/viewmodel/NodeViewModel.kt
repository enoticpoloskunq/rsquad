package com.raccoonsquad.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.repository.NodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NodeUiState(
    val id: String,
    val name: String,
    val server: String,
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
    
    private val _lastImportedNode = MutableStateFlow<VlessConfig?>(null)
    val lastImportedNode: StateFlow<VlessConfig?> = _lastImportedNode.asStateFlow()
    
    fun importNode(uri: String) {
        viewModelScope.launch {
            _importError.value = null
            
            val config = repository.importFromUri(uri)
            if (config != null) {
                repository.addNode(config)
                _lastImportedNode.value = config
            } else {
                _importError.value = "Неверный VLESS URI"
            }
        }
    }
    
    fun deleteNode(uuid: String) {
        viewModelScope.launch {
            repository.deleteNode(uuid)
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
    
    fun clearLastImported() {
        _lastImportedNode.value = null
    }
    
    fun toUiState(config: VlessConfig, activeUuid: String?): NodeUiState {
        return NodeUiState(
            id = config.uuid,
            name = config.name,
            server = "${config.serverAddress}:${config.port}",
            latency = config.latency,
            isActive = config.uuid == activeUuid,
            hasFragment = config.fragmentationEnabled,
            hasNoise = config.noiseEnabled,
            mtu = config.mtu,
            config = config
        )
    }
}
