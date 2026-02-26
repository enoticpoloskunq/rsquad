package com.raccoonsquad.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raccoonsquad.core.speedtest.*
import kotlinx.coroutines.launch

/**
 * Speed Test Screen with animated results
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(
    onBack: () -> Unit,
    isVpnActive: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(SpeedTestProgress(TestPhase.IDLE, 0f, 0, 0)) }
    var result by remember { mutableStateOf<SpeedTestResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var testHistory by remember { mutableStateOf<List<SpeedTestResult>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        SpeedTest.setProgressCallback { progress = it }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Speed Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // VPN Status warning
            if (!isVpnActive) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Подключите VPN для корректного теста скорости",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // Main speed test card
            item {
                SpeedTestCard(
                    isRunning = isRunning,
                    progress = progress,
                    result = result,
                    error = error,
                    onStartTest = {
                        scope.launch {
                            isRunning = true
                            error = null
                            result = null
                            
                            SpeedTest.runTest().fold(
                                onSuccess = { 
                                    result = it
                                    testHistory = testHistory + it
                                },
                                onFailure = { error = it.message }
                            )
                            
                            isRunning = false
                        }
                    },
                    onCancelTest = {
                        SpeedTest.stopTest()
                        isRunning = false
                    }
                )
            }
            
            // Progress details
            if (isRunning) {
                item {
                    ProgressDetailsCard(progress)
                }
            }
            
            // Results history
            if (testHistory.isNotEmpty()) {
                item {
                    Text(
                        "История тестов",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(testHistory.reversed()) { testResult ->
                    TestResultHistoryItem(testResult)
                }
            }
            
            // Info card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "О тесте скорости",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Тест измеряет реальную скорость через ваш VPN туннель. " +
                            "Результаты зависят от выбранной ноды и загруженности сервера.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedTestCard(
    isRunning: Boolean,
    progress: SpeedTestProgress,
    result: SpeedTestResult?,
    error: String?,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Speed gauge
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                // Circular progress indicator
                val animatedProgress by animateFloatAsState(
                    targetValue = progress.progress,
                    animationSpec = tween(300),
                    label = "progress"
                )
                
                CircularProgressIndicator(
                    progress = { if (isRunning) animatedProgress else 0f },
                    modifier = Modifier.size(200.dp),
                    color = when (progress.phase) {
                        TestPhase.LATENCY_TEST -> MaterialTheme.colorScheme.tertiary
                        TestPhase.DOWNLOAD_TEST -> MaterialTheme.colorScheme.primary
                        TestPhase.UPLOAD_TEST -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                // Center content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        result != null -> {
                            Text(
                                text = result.qualityEmoji,
                                style = MaterialTheme.typography.displayMedium
                            )
                            Text(
                                text = result.downloadSpeedFormatted,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = result.quality.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        isRunning -> {
                            Text(
                                text = when (progress.phase) {
                                    TestPhase.LATENCY_TEST -> "🏓"
                                    TestPhase.DOWNLOAD_TEST -> "⬇️"
                                    TestPhase.UPLOAD_TEST -> "⬆️"
                                    else -> "🔄"
                                },
                                style = MaterialTheme.typography.displayMedium
                            )
                            Text(
                                text = formatSpeed(progress.currentSpeed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        else -> {
                            Text(
                                text = "🚀",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Text(
                                text = "Готов к тесту",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Error message
            AnimatedVisibility(visible = error != null) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Results grid
            AnimatedVisibility(visible = result != null) {
                Column {
                    ResultGrid(result!!)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            // Action button
            Button(
                onClick = { if (isRunning) onCancelTest() else onStartTest() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRunning) "Остановить" else "Начать тест")
            }
        }
    }
}

@Composable
private fun ResultGrid(result: SpeedTestResult) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResultItem(
                modifier = Modifier.weight(1f),
                emoji = "⬇️",
                label = "Download",
                value = result.downloadSpeedFormatted
            )
            ResultItem(
                modifier = Modifier.weight(1f),
                emoji = "⬆️",
                label = "Upload",
                value = result.uploadSpeedFormatted
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResultItem(
                modifier = Modifier.weight(1f),
                emoji = "🏓",
                label = "Latency",
                value = "${result.latency} ms"
            )
            ResultItem(
                modifier = Modifier.weight(1f),
                emoji = "📊",
                label = "Jitter",
                value = "${result.jitter} ms"
            )
        }
    }
}

@Composable
private fun ResultItem(
    modifier: Modifier = Modifier,
    emoji: String,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressDetailsCard(progress: SpeedTestProgress) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (progress.phase) {
                        TestPhase.LATENCY_TEST -> "🏓 Тест задержки"
                        TestPhase.DOWNLOAD_TEST -> "⬇️ Тест загрузки"
                        TestPhase.UPLOAD_TEST -> "⬆️ Тест отдачи"
                        else -> "Подготовка..."
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${(progress.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            if (progress.bytesTransferred > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Передано: ${formatBytes(progress.bytesTransferred)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TestResultHistoryItem(result: SpeedTestResult) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.qualityEmoji,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = result.downloadSpeedFormatted,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "↑ ${result.uploadSpeedFormatted}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${result.latency} ms",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.quality.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
        bytesPerSecond < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
        else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024))
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
