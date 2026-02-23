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
    val listState = rememberLazyListState()
    
    var showClearDialog by remember { mutableStateOf(false) }
    var autoScroll by remember { mutableStateOf(true) }
    var showCrashDialog by remember { mutableStateOf(false) }
    
    val logs = LogManager.getLogs()
    val hasCrash = LogManager.hasLastCrash()
    
    // Show crash dialog if exists
    LaunchedEffect(hasCrash) {
        if (hasCrash) {
            showCrashDialog = true
        }
    }
    
    // Auto-scroll
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
                    IconButton(onClick = { autoScroll = !autoScroll }) {
                        Icon(
                            if (autoScroll) Icons.Default.KeyboardArrowDown else Icons.Default.Lock,
                            "Auto-scroll"
                        )
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Raccoon Logs", LogManager.exportLogs())
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Логи скопированы", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy")
                    }
                    IconButton(onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, LogManager.exportLogs())
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Поделиться"))
                    }) {
                        Icon(Icons.Default.Share, "Share")
                    }
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
            // Crash warning
            if (hasCrash) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Обнаружен краш! Нажмите для просмотра",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showCrashDialog = true }) {
                            Icon(Icons.Default.ArrowForward, "View crash", 
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            
            // Device info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("📱 ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("🤖 Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.bodySmall)
                    Text("🔧 ROM: ${RomCompat.romName}",
                        style = MaterialTheme.typography.bodySmall)
                    
                    RomCompat.getPowerManagementWarning()?.let { warning ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, 
                                tint = Color(0xFFFFA000), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(warning, style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFA000))
                        }
                    }
                }
            }
            
            // Log count
            Text("Записей: ${logs.size}", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp))
            
            // Logs
            Card(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Нет логов", style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        items(logs) { log -> LogEntryItem(log) }
                    }
                }
            }
        }
    }
    
    // Crash dialog
    if (showCrashDialog) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false },
            title = { Text("💥 Последний краш") },
            text = {
                val crashLog = LogManager.getLastCrash() ?: "Нет данных"
                Column {
                    Text(
                        crashLog.take(2000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.height(300.dp)
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Crash", LogManager.getLastCrash()))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    }) { Text("Копировать") }
                    TextButton(onClick = {
                        LogManager.clearCrash()
                        showCrashDialog = false
                    }) { Text("Очистить") }
                    TextButton(onClick = { showCrashDialog = false }) { Text("Закрыть") }
                }
            }
        )
    }
    
    // Clear dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить логи?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        LogManager.clearLogs()
                        showClearDialog = false
                        Toast.makeText(context, "Очищено", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
        android.util.Log.VERBOSE, android.util.Log.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        android.util.Log.INFO -> MaterialTheme.colorScheme.primary
        android.util.Log.WARN -> Color(0xFFFFA000)
        android.util.Log.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(log.formattedTime, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp))
        Text(log.levelName, style = MaterialTheme.typography.labelSmall,
            color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.width(16.dp))
        Text("/${log.tag}: ", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(60.dp), maxLines = 1)
        Text(log.message, style = MaterialTheme.typography.labelSmall,
            color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
    }
}
