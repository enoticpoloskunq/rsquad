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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditorScreen(
    nodeId: String? = null,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    // Form state
    var name by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var sni by remember { mutableStateOf("") }
    
    // Reality
    var publicKey by remember { mutableStateOf("") }
    var shortId by remember { mutableStateOf("") }
    
    // Fragmentation
    var fragmentEnabled by remember { mutableStateOf(false) }
    var fragmentPackets by remember { mutableStateOf("1-3") }
    var fragmentLength by remember { mutableStateOf("10-20") }
    
    // Socket
    var tcpFastOpen by remember { mutableStateOf(false) }
    var tcpNoDelay by remember { mutableStateOf(true) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (nodeId == null) "Add Node" else "Edit Node") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
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
            Text("Basic", style = MaterialTheme.typography.titleMedium)
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = uuid,
                onValueChange = { uuid = it },
                label = { Text("UUID") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") }
            )
            
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
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
            
            Divider()
            
            // Fragmentation Section
            Text("Fragmentation", style = MaterialTheme.typography.titleMedium)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Fragmentation")
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
            }
            
            Divider()
            
            // Socket Section
            Text("Socket Options", style = MaterialTheme.typography.titleMedium)
            
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
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Node")
            }
        }
    }
}
