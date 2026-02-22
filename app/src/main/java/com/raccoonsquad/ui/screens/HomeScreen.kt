package com.raccoonsquad.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class NodeUiState(
    val id: String,
    val name: String,
    val server: String,
    val latency: Long?,
    val isActive: Boolean,
    val hasFragment: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddNode: () -> Unit,
    onNodeClick: (String) -> Unit
) {
    // TODO: Get from ViewModel
    var nodes by remember { 
        mutableStateOf(listOf<NodeUiState>()) 
    }
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("🦝 Raccoon Squad") 
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, "Import node")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showImportDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Node") }
            )
        }
    ) { padding ->
        if (nodes.isEmpty()) {
            EmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodes) { node ->
                    NodeCard(
                        node = node,
                        onClick = { onNodeClick(node.id) }
                    )
                }
            }
        }
    }
    
    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import VLESS URI") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("vless://...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // TODO: Parse and add node
                    showImportDialog = false
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
        Text(
            "No nodes yet",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "Tap + to import a VLESS URI",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeCard(
    node: NodeUiState,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(node.name) },
            supportingContent = {
                Column {
                    Text(node.server)
                    if (node.hasFragment) {
                        Text(
                            "🔧 Fragment enabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    node.latency?.let { latency ->
                        Text(
                            "${latency}ms",
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                latency < 100 -> MaterialTheme.colorScheme.primary
                                latency < 300 -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = node.isActive,
                        onCheckedChange = { /* TODO */ }
                    )
                }
            },
            leadingContent = {
                Text(
                    if (node.isActive) "🟢" else "⚪",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        )
    }
}
