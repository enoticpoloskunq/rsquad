package com.raccoonsquad.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.FlowMode
import com.raccoonsquad.data.model.SecurityMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorScreen(
    config: VlessConfig? = null,
    onBack: () -> Unit,
    onSave: (VlessConfig) -> Unit
) {
    // Form state - initialize from config if editing
    var name by remember { mutableStateOf(config?.name ?: "") }
    var server by remember { mutableStateOf(config?.serverAddress ?: "") }
    var port by remember { mutableStateOf(config?.port?.toString() ?: "443") }
    var sni by remember { mutableStateOf(config?.sni ?: "") }
    
    // Reality
    var publicKey by remember { mutableStateOf(config?.realityPublicKey ?: "") }
    var shortId by remember { mutableStateOf(config?.realityShortId ?: "") }
    var spiderX by remember { mutableStateOf(config?.realitySpiderX ?: "/") }
    
    // Fragmentation
    var fragmentEnabled by remember { mutableStateOf(config?.fragmentationEnabled ?: false) }
    var fragmentPackets by remember { mutableStateOf(config?.fragmentPackets ?: "1-3") }
    var fragmentLength by remember { mutableStateOf(config?.fragmentLength ?: "10-20") }
    var fragmentInterval by remember { mutableStateOf(config?.fragmentInterval ?: "10-20") }
    
    // Socket
    var tcpFastOpen by remember { mutableStateOf(config?.tcpFastOpen ?: false) }
    var tcpNoDelay by remember { mutableStateOf(config?.tcpNoDelay ?: true) }
    var mtu by remember { mutableStateOf(config?.mtu ?: "1350") }
    
    val isEditing = config != null
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "✏️ Редактирование" else "➕ Новая нода") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Build updated config
                        val updatedConfig = (config ?: VlessConfig(
                            uuid = java.util.UUID.randomUUID().toString(),
                            serverAddress = server.ifEmpty { "example.com" },
                            port = port.toIntOrNull() ?: 443
                        )).copy(
                            name = name,
                            serverAddress = server,
                            port = port.toIntOrNull() ?: 443,
                            sni = sni,
                            realityPublicKey = publicKey,
                            realityShortId = shortId,
                            realitySpiderX = spiderX,
                            fragmentationEnabled = fragmentEnabled,
                            fragmentPackets = fragmentPackets,
                            fragmentLength = fragmentLength,
                            fragmentInterval = fragmentInterval,
                            tcpFastOpen = tcpFastOpen,
                            tcpNoDelay = tcpNoDelay,
                            mtu = mtu
                        )
                        onSave(updatedConfig)
                    }) {
                        Icon(Icons.Default.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Section
            Text("Основное", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Сервер") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Порт") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(100.dp)
                )
            }
            
            OutlinedTextField(
                value = sni,
                onValueChange = { sni = it },
                label = { Text("SNI") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = mtu,
                onValueChange = { mtu = it },
                label = { Text("MTU") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Divider()
            
            // Reality Section
            Text("Reality", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = publicKey,
                onValueChange = { publicKey = it },
                label = { Text("Public Key") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = shortId,
                onValueChange = { shortId = it },
                label = { Text("Short ID") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = spiderX,
                onValueChange = { spiderX = it },
                label = { Text("SpiderX") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Divider()
            
            // Fragmentation Section
            Text("Фрагментация", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Включить фрагментацию")
                Switch(checked = fragmentEnabled, onCheckedChange = { fragmentEnabled = it })
            }
            
            if (fragmentEnabled) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fragmentPackets,
                        onValueChange = { fragmentPackets = it },
                        label = { Text("Packets") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("1-3") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = fragmentLength,
                        onValueChange = { fragmentLength = it },
                        label = { Text("Length") },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("10-20") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = fragmentInterval,
                    onValueChange = { fragmentInterval = it },
                    label = { Text("Interval") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("10-20") }
                )
            }
            
            Divider()
            
            // Socket Section
            Text("TCP опции", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TCP Fast Open")
                Switch(checked = tcpFastOpen, onCheckedChange = { tcpFastOpen = it })
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TCP NoDelay")
                Switch(checked = tcpNoDelay, onCheckedChange = { tcpNoDelay = it })
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    val updatedConfig = (config ?: VlessConfig(
                        uuid = java.util.UUID.randomUUID().toString(),
                        serverAddress = server.ifEmpty { "example.com" },
                        port = port.toIntOrNull() ?: 443
                    )).copy(
                        name = name,
                        serverAddress = server,
                        port = port.toIntOrNull() ?: 443,
                        sni = sni,
                        realityPublicKey = publicKey,
                        realityShortId = shortId,
                        realitySpiderX = spiderX,
                        fragmentationEnabled = fragmentEnabled,
                        fragmentPackets = fragmentPackets,
                        fragmentLength = fragmentLength,
                        fragmentInterval = fragmentInterval,
                        tcpFastOpen = tcpFastOpen,
                        tcpNoDelay = tcpNoDelay,
                        mtu = mtu
                    )
                    onSave(updatedConfig)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEditing) "💾 Сохранить изменения" else "➕ Создать ноду")
            }
        }
    }
}
