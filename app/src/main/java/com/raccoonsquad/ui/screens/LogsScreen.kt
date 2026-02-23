package com.raccoonsquad.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.compat.RomCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    nodeId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    var showClearDialog by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    
    val logs = LogManager.getLogs()
    
    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📋 Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Auto-scroll toggle
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Default.KeyboardArrowDown else Icons.Default.Lock,
                            "Auto-scroll"
                        )
                    }
                    // Copy logs
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Raccoon Logs", LogManager.exportLogs())
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Логи скопированы", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy")
                    }
                    // Share logs
                    IconButton(onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, LogManager.exportLogs())
                            type = "text/plain"
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться логами")
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                    // Clear
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, "Clear")
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
            // Device info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        "📱 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "🤖 Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "🔧 ROM: ${RomCompat.romName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    // Show power warning if needed
                    RomCompat.getPowerManagementWarning()?.let { warning ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFFA000),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                warning,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFA000)
                            )
                        }
                    }
                }
            }
            
            // Log stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Записей: ${logs.size}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "MTU: ${RomCompat.getRecommendedMtu()}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            // Logs list
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Нет логов",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    ) {
                        items(logs) { log ->
                            LogEntryItem(log)
                        }
                    }
                }
            }
        }
    }
    
    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить логи?") },
            text = { Text("Все логи будут удалены") },
            confirmButton = {
                TextButton(
                    onClick = {
                        LogManager.clearLogs()
                        showClearDialog = false
                        Toast.makeText(context, "Логи очищены", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
fun LogEntryItem(log: LogManager.LogEntry) {
    val color = when (log.level) {
        android.util.Log.VERBOSE, android.util.Log.DEBUG -> 
            MaterialTheme.colorScheme.onSurfaceVariant
        android.util.Log.INFO -> 
            MaterialTheme.colorScheme.primary
        android.util.Log.WARN -> 
            Color(0xFFFFA000)
        android.util.Log.ERROR -> 
            MaterialTheme.colorScheme.error
        else -> 
            MaterialTheme.colorScheme.onSurface
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = log.formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = log.levelName,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = "/${log.tag}: ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp),
            maxLines = 1
        )
        Text(
            text = log.message,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
    
    // Show stack trace for errors
    log.throwable?.let { e ->
        android.util.Log.getStackTraceString(e).lines().take(5).forEach { line ->
            Text(
                text = "    $line",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
