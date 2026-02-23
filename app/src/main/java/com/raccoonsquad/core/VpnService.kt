package com.raccoonsquad.core

import android.content.Intent
import android.net.VpnService as BaseVpnService
import android.os.ParcelFileDescriptor
import com.raccoonsquad.data.model.VlessConfig
import kotlinx.coroutines.*
import java.io.File

class VpnService : BaseVpnService() {
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Xray core process
    private var xrayProcess: Process? = null
    
    var isRunning = false
        private set
    
    var currentConfig: VlessConfig? = null
        private set
    
    fun connect(config: VlessConfig) {
        if (isRunning) return
        
        currentConfig = config
        isRunning = true
        
        // Build VPN interface
        vpnInterface = Builder()
            .setSession("RaccoonSquad")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .setMtu(if (config.mtu == "default") 1500 else config.mtu.toIntOrNull() ?: 1500)
            .establish()
        
        // Start Xray core
        startXray(config)
    }
    
    fun disconnect() {
        isRunning = false
        xrayProcess?.destroy()
        xrayProcess = null
        vpnInterface?.close()
        vpnInterface = null
        currentConfig = null
        stopSelf()
    }
    
    private fun startXray(config: VlessConfig) {
        scope.launch {
            try {
                // Generate Xray config JSON
                val xrayConfig = generateXrayConfig(config)
                
                // Write config to file
                val configFile = File(filesDir, "xray_config.json")
                configFile.writeText(xrayConfig)
                
                // Start Xray process
                // TODO: Copy xray binary to filesDir first
                val xrayBinary = File(filesDir, "xray")
                
                xrayProcess = ProcessBuilder()
                    .command(xrayBinary.absolutePath, "run", "-c", configFile.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                
                // Read logs
                xrayProcess?.inputStream?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        // Broadcast log line
                        broadcastLog(line ?: "")
                    }
                }
            } catch (e: Exception) {
                broadcastLog("Error: ${e.message}")
            }
        }
    }
    
    private fun generateXrayConfig(config: VlessConfig): String {
        // Build Xray JSON config
        return """
        {
            "log": {"loglevel": "debug"},
            "inbounds": [{
                "tag": "tun",
                "port": 10808,
                "protocol": "socks",
                "settings": {"auth": "noauth", "udp": true}
            }],
            "outbounds": [{
                "tag": "proxy",
                "protocol": "vless",
                "settings": {
                    "vnext": [{
                        "address": "${config.serverAddress}",
                        "port": ${config.port},
                        "users": [{
                            "id": "${config.uuid}",
                            "encryption": "none",
                            "flow": "${if (config.flow.name.contains("VISION")) "xtls-rprx-vision" else ""}"
                        }]
                    }]
                },
                "streamSettings": {
                    "network": "tcp",
                    "security": "${config.securityMode.name.lowercase()}",
                    "${config.securityMode.name.lowercase()}Settings": {
                        "serverName": "${config.sni}",
                        ${if (config.securityMode.name == "REALITY") """
                        "publicKey": "${config.realityPublicKey}",
                        "shortId": "${config.realityShortId}",
                        "fingerprint": "chrome"
                        """ else ""}
                    }
                }
                ${if (config.fragmentationEnabled) """,
                "sockopt": {
                    "dialerProxy": "fragment"
                }
                """ else ""}
            }]
            ${if (config.fragmentationEnabled) """,
            ,{
                "tag": "fragment",
                "protocol": "freedom",
                "settings": {
                    "fragment": {
                        "packets": "${config.fragmentationPackets}",
                        "length": "${config.fragmentationLength}"
                    }
                }
            }
            """ else ""}
            ],
            "routing": {
                "domainStrategy": "IPIfNonMatch",
                "rules": [
                    {"type": "field", "ip": ["geoip:private"], "outboundTag": "direct"}
                ]
            }
        }
        """.trimIndent()
    }
    
    private fun broadcastLog(message: String) {
        val intent = Intent(ACTION_LOG).apply {
            putExtra(EXTRA_LOG_MESSAGE, message)
        }
        sendBroadcast(intent)
    }
    
    companion object {
        const val ACTION_LOG = "com.raccoonsquad.LOG"
        const val EXTRA_LOG_MESSAGE = "log_message"
    }
}
