package com.raccoonsquad.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

enum class LogLevel {
    INFO, WARN, ERROR, DEBUG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    nodeId: String?,
    onBack: () -> Unit
) {
    // TODO: Get from ViewModel
    var isConnected by remember { mutableStateOf(false) }
    var logs by remember { 
        mutableStateOf(
            listOf(
                LogEntry(System.currentTimeMillis(), LogLevel.INFO, "Waiting to connect..."),
                LogEntry(System.currentTimeMillis(), LogLevel.DEBUG, "Node: $nodeId")
            )
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connection Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isConnected = !isConnected
                        // TODO: Start/stop connection
                    }) {
                        Icon(
                            if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
                            if (isConnected) "Stop" else "Connect"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Status bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        if (isConnected) "🟢 Connected" else "⚪ Disconnected",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Raccoon Squad v1.0",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            // Logs
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(logs) { log ->
                    LogEntryItem(log)
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(log: LogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    val color = when (log.level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.WARN -> Color(0xFFFFA000)
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Row(
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = timeFormat.format(Date(log.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "[${log.level.name}]",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}
