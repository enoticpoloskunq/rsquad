package com.raccoonsquad.data.groups

import com.raccoonsquad.core.log.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Node Group system for organizing VPN nodes
 */
data class NodeGroup(
    val id: String,
    val name: String,
    val color: GroupColor,
    val icon: String,           // Emoji icon
    val nodeIds: Set<String>,   // IDs of nodes in this group
    val createdAt: Long,
    val isDefault: Boolean = false
)

enum class GroupColor(val hex: String, val displayName: String) {
    PURPLE("#7C4DFF", "Фиолетовый"),
    BLUE("#2196F3", "Синий"),
    GREEN("#00E676", "Зелёный"),
    ORANGE("#FF9800", "Оранжевый"),
    RED("#F44336", "Красный"),
    PINK("#E91E63", "Розовый"),
    CYAN("#00BCD4", "Голубой"),
    YELLOW("#FFEB3B", "Жёлтый")
}

object GroupManager {
    private const val TAG = "GroupManager"
    
    // Default groups
    private val defaultGroups = listOf(
        NodeGroup(
            id = "all",
            name = "Все ноды",
            color = GroupColor.PURPLE,
            icon = "🌐",
            nodeIds = emptySet(),
            createdAt = System.currentTimeMillis(),
            isDefault = true
        ),
        NodeGroup(
            id = "favorites",
            name = "Избранное",
            color = GroupColor.YELLOW,
            icon = "⭐",
            nodeIds = emptySet(),
            createdAt = System.currentTimeMillis(),
            isDefault = true
        ),
        NodeGroup(
            id = "working",
            name = "Рабочие",
            color = GroupColor.GREEN,
            icon = "✅",
            nodeIds = emptySet(),
            createdAt = System.currentTimeMillis(),
            isDefault = true
        )
    )
    
    private val _groups = MutableStateFlow<List<NodeGroup>>(emptyList())
    val groups: StateFlow<List<NodeGroup>> = _groups.asStateFlow()
    
    private val _selectedGroupId = MutableStateFlow<String>("all")
    val selectedGroupId: StateFlow<String> = _selectedGroupId.asStateFlow()
    
    init {
        // Initialize with default groups
        _groups.value = defaultGroups
    }
    
    fun selectGroup(groupId: String) {
        _selectedGroupId.value = groupId
        LogManager.i(TAG, "Selected group: $groupId")
    }
    
    fun getSelectedGroup(): NodeGroup? {
        return _groups.value.find { it.id == _selectedGroupId.value }
    }
    
    fun createGroup(name: String, color: GroupColor, icon: String): NodeGroup {
        val id = "group_${System.currentTimeMillis()}"
        val group = NodeGroup(
            id = id,
            name = name,
            color = color,
            icon = icon,
            nodeIds = emptySet(),
            createdAt = System.currentTimeMillis(),
            isDefault = false
        )
        
        _groups.value = _groups.value + group
        LogManager.i(TAG, "Created group: $name")
        return group
    }
    
    fun deleteGroup(groupId: String) {
        val group = _groups.value.find { it.id == groupId }
        if (group?.isDefault == true) {
            LogManager.w(TAG, "Cannot delete default group: $groupId")
            return
        }
        
        _groups.value = _groups.value.filter { it.id != groupId }
        
        if (_selectedGroupId.value == groupId) {
            _selectedGroupId.value = "all"
        }
        
        LogManager.i(TAG, "Deleted group: $groupId")
    }
    
    fun renameGroup(groupId: String, newName: String) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) group.copy(name = newName) else group
        }
        LogManager.i(TAG, "Renamed group $groupId to: $newName")
    }
    
    fun addNodeToGroup(groupId: String, nodeId: String) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) {
                group.copy(nodeIds = group.nodeIds + nodeId)
            } else {
                group
            }
        }
        LogManager.i(TAG, "Added node $nodeId to group $groupId")
    }
    
    fun removeNodeFromGroup(groupId: String, nodeId: String) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) {
                group.copy(nodeIds = group.nodeIds - nodeId)
            } else {
                group
            }
        }
        LogManager.i(TAG, "Removed node $nodeId from group $groupId")
    }
    
    fun setGroupNodes(groupId: String, nodeIds: Set<String>) {
        _groups.value = _groups.value.map { group ->
            if (group.id == groupId) {
                group.copy(nodeIds = nodeIds)
            } else {
                group
            }
        }
    }
    
    fun getNodesForGroup(groupId: String, allNodeIds: List<String>): List<String> {
        return when (groupId) {
            "all" -> allNodeIds
            "favorites" -> allNodeIds // Will be filtered by isFavorite in UI
            "working" -> allNodeIds   // Will be filtered by latency in UI
            else -> {
                val group = _groups.value.find { it.id == groupId }
                group?.nodeIds?.toList() ?: allNodeIds
            }
        }
    }
    
    fun getGroupForNode(nodeId: String): NodeGroup? {
        return _groups.value.find { it.nodeIds.contains(nodeId) && !it.isDefault }
    }
    
    // Available icons for groups
    val availableIcons = listOf(
        "🏠" to "Дом",
        "💼" to "Работа",
        "🎮" to "Игры",
        "🎬" to "Медиа",
        "🌍" to "Мир",
        "🚀" to "Быстрые",
        "💰" to "Эконом",
        "🔒" to "Приватные",
        "⚡" to "Премиум",
        "🎯" to "Целевые",
        "📱" to "Мобильные",
        "🖥️" to "Десктоп",
        "🌐" to "Общие",
        "⭐" to "Избранное",
        "✅" to "Проверенные",
        "🔥" to "Горячие"
    )
}
