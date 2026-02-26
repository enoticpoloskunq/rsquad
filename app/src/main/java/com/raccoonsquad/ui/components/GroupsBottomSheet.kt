package com.raccoonsquad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raccoonsquad.data.groups.*

/**
 * Groups Bottom Sheet for organizing nodes
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsBottomSheet(
    onDismiss: () -> Unit,
    onCreateGroup: (name: String, color: GroupColor, icon: String) -> Unit,
    onSelectGroup: (groupId: String) -> Unit,
    onDeleteGroup: (groupId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups by GroupManager.groups.collectAsState()
    val selectedGroupId by GroupManager.selectedGroupId.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<NodeGroup?>(null) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Группы",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Create group")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Groups list
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups, key = { it.id }) { group ->
                GroupItem(
                    group = group,
                    isSelected = group.id == selectedGroupId,
                    onSelect = { 
                        onSelectGroup(group.id)
                        onDismiss()
                    },
                    onEdit = { editingGroup = group },
                    onDelete = if (group.isDefault) null else { { onDeleteGroup(group.id) } }
                )
            }
            
            // Create new group button
            item {
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать группу")
                }
            }
        }
    }
    
    // Create group dialog
    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, color, icon ->
                onCreateGroup(name, color, icon)
                showCreateDialog = false
            }
        )
    }
    
    // Edit group dialog
    if (editingGroup != null) {
        EditGroupDialog(
            group = editingGroup!!,
            onDismiss = { editingGroup = null },
            onSave = { name ->
                GroupManager.renameGroup(editingGroup!!.id, name)
                editingGroup = null
            }
        )
    }
}

@Composable
private fun GroupItem(
    group: NodeGroup,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
            else 
                MaterialTheme.colorScheme.surface
        ),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parseColor(group.color.hex))
            ) {
                Text(
                    group.icon,
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (group.nodeIds.isNotEmpty()) {
                    Text(
                        "${group.nodeIds.size} нод",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Menu for non-default groups
            if (!group.isDefault) {
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Options")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Редактировать") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Удалить") },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, color: GroupColor, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(GroupColor.PURPLE) }
    var selectedIcon by remember { mutableStateOf("🏠") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать группу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Color selection
                Text("Цвет:", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupColor.values().forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(parseColor(color.hex))
                                .clickable { selectedColor = color }
                        ) {
                            if (color == selectedColor) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.align(Alignment.Center),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
                
                // Icon selection
                Text("Иконка:", style = MaterialTheme.typography.labelMedium)
                LazyColumn(
                    modifier = Modifier.height(120.dp)
                ) {
                    items(GroupManager.availableIcons.chunked(4)) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { (icon, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (icon == selectedIcon) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { selectedIcon = icon },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(icon, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedColor, selectedIcon) },
                enabled = name.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun EditGroupDialog(
    group: NodeGroup,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать группу") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }
}
