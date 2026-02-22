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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.ui.viewmodel.NodeViewModel
import com.raccoonsquad.ui.viewmodel.NodeUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NodeViewModel = viewModel(),
    onAddNode: () -> Unit,
    onNodeClick: (String) -> Unit
) {
    val nodes by viewModel.nodes.collectAsState()
    val activeUuid by viewModel.activeNodeUuid.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val lastImported by viewModel.lastImportedNode.collectAsState()
    
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<VlessConfig?>(null) }
    
    val clipboardManager = LocalClipboardManager.current
    
    // Auto-paste from clipboard
    LaunchedEffect(showImportDialog) {
        if (showImportDialog) {
            val clip = clipboardManager.getText()?.text ?: ""
            if (clip.startsWith("vless://") && importText.isEmpty()) {
                importText = clip
            }
        }
    }
    
    // Show success message
    LaunchedEffect(lastImported) {
        if (lastImported != null) {
            showImportDialog = false
            importText = ""
            viewModel.clearLastImported()
        }
    }
    
    val uiStates = remember(nodes, activeUuid) {
        nodes.map { viewModel.toUiState(it, activeUuid) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🦝 Raccoon Squad") },
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
        if (uiStates.isEmpty()) {
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
                items(uiStates, key = { it.id }) { node ->
                    NodeCard(
                        node = node,
                        onClick = { onNodeClick(node.id) },
                        onToggle = { 
                            viewModel.setActiveNode(if (node.isActive) null else node.id)
                        },
                        onDelete = { showDeleteDialog = node.config }
                    )
                }
            }
        }
    }
    
    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportDialog = false
                viewModel.clearImportError()
            },
            title = { Text("Import VLESS URI") },
            text = {
                Column {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text("vless://...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 5,
                        isError = importError != null,
                        supportingText = importError?.let { { Text(it) } }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            val clip = clipboardManager.getText()?.text ?: ""
                            if (clip.startsWith("vless://")) {
                                importText = clip
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste")
                        }
                        
                        if (importText.startsWith("vless://")) {
                            TextButton(onClick = {
                                viewModel.importNode(importText)
                            }) {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (importText.startsWith("vless://")) {
                        viewModel.importNode(importText)
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportDialog = false
                    importText = ""
                    viewModel.clearImportError()
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete confirmation
    showDeleteDialog?.let { node ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete node?") },
            text = { Text("Delete \"${node.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNode(node.uuid)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
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
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (node.hasFragment) {
                            Text(
                                "🔧 Fragment",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (node.hasNoise) {
                            Text(
                                "📡 Noise",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (node.mtu != "default") {
                            Text(
                                "📏 MTU ${node.mtu}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
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
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Switch(
                        checked = node.isActive,
                        onCheckedChange = { onToggle() }
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
