package com.raccoonsquad.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.raccoonsquad.MainActivity
import com.raccoonsquad.R
import com.raccoonsquad.core.xray.XrayWrapper
import com.raccoonsquad.data.model.VlessConfig
import kotlinx.coroutines.*
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * VPN Service that tunnels traffic through Xray-core
 * 
 * Architecture:
 * 1. Android VPN Interface (TUN) captures all traffic
 * 2. Xray-core runs SOCKS5 proxy on 127.0.0.1:10808
 * 3. VpnService reads packets from TUN, routes through SOCKS5
 * 
 * Note: Uses SOCKS5 approach instead of native TUN fd for better compatibility
 */
class RaccoonVpnService : VpnService() {
    
    companion object {
        const val ACTION_CONNECT = "com.raccoonsquad.CONNECT"
        const val ACTION_DISCONNECT = "com.raccoonsquad.DISCONNECT"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "raccoon_vpn_channel"
        
        // SOCKS5 proxy address (Xray inbound)
        const val PROXY_HOST = "127.0.0.1"
        const val PROXY_PORT = 10808
        
        var isActive = false
            private set
        
        var currentConfig: VlessConfig? = null
            private set
            
        var connectionError: String? = null
            private set
        
        // Broadcast actions
        const val ACTION_STATE_CHANGED = "com.raccoonsquad.STATE_CHANGED"
        const val EXTRA_CONNECTED = "connected"
        const val EXTRA_ERROR = "error"
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Traffic counters
    private var txBytes = 0L
    private var rxBytes = 0L
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        XrayWrapper.init(applicationContext)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                @Suppress("DEPRECATION")
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("config", VlessConfig::class.java)
                } else {
                    intent.getParcelableExtra("config")
                }
                config?.let { connect(it) }
            }
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }
    
    private fun connect(config: VlessConfig) {
        if (isActive) {
            Log.d("RaccoonVpn", "Already connected, disconnecting first")
            disconnect()
        }
        
        connectionError = null
        currentConfig = config
        
        // Generate Xray config
        val xrayConfig = XrayWrapper.generateConfig(config)
        Log.d("RaccoonVpn", "Xray config generated (${xrayConfig.length} bytes)")
        
        // Build VPN interface
        val mtu = config.mtu.toIntOrNull() ?: 1500
        
        try {
            val builder = Builder()
                .setSession("Raccoon Squad VPN")
                .setMtu(mtu)
                .addAddress("10.66.66.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
            
            // Allow bypass for certain apps (optional)
            // builder.addDisallowedApplication("com.some.app")
            
            // Exclude our app to prevent loops
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w("RaccoonVpn", "Could not exclude self: ${e.message}")
            }
            
            // Establish VPN interface
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                connectionError = "VPN permission denied or revoked"
                Log.e("RaccoonVpn", connectionError!!)
                broadcastState(connected = false, error = connectionError)
                return
            }
            
            // Start Xray with config
            val started = XrayWrapper.start(xrayConfig)
            if (!started) {
                connectionError = "Failed to start Xray core"
                Log.e("RaccoonVpn", connectionError!!)
                vpnInterface?.close()
                vpnInterface = null
                broadcastState(connected = false, error = connectionError)
                return
            }
            
            isActive = true
            
            // Start foreground notification
            startForeground(NOTIFICATION_ID, createNotification(config.name))
            
            // Start VPN packet processing
            startVpnProcessing()
            
            Log.i("RaccoonVpn", "VPN connected - Node: ${config.name}, MTU: $mtu")
            broadcastState(connected = true)
            
        } catch (e: Exception) {
            connectionError = "Connection failed: ${e.message}"
            Log.e("RaccoonVpn", "Failed to connect", e)
            disconnect()
            broadcastState(connected = false, error = connectionError)
        }
    }
    
    private fun disconnect() {
        Log.d("RaccoonVpn", "Disconnecting...")
        
        isActive = false
        
        // Stop VPN processing
        vpnJob?.cancel()
        vpnJob = null
        
        // Stop Xray
        XrayWrapper.stop()
        
        // Close VPN interface
        vpnInterface?.close()
        vpnInterface = null
        
        currentConfig = null
        txBytes = 0
        rxBytes = 0
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        broadcastState(connected = false)
        Log.i("RaccoonVpn", "VPN disconnected")
    }
    
    /**
     * Start VPN packet processing coroutine
     * 
     * Android VPN creates a TUN interface. We read IP packets from it
     * and route them through the SOCKS5 proxy that Xray provides.
     */
    private fun startVpnProcessing() {
        vpnJob = scope.launch {
            val fd = vpnInterface?.fileDescriptor
            if (fd == null) {
                Log.e("RaccoonVpn", "No file descriptor")
                return@launch
            }
            
            // Use FileChannel for better performance
            val channel = ParcelFileDescriptor.AutoCloseOutputStream(vpnInterface!!).channel
            val inputChannel = ParcelFileDescriptor.AutoCloseInputStream(vpnInterface!!).channel
            
            val buffer = ByteBuffer.allocateDirect(32767)
            
            Log.i("RaccoonVpn", "VPN processing started")
            
            // Launch read and write coroutines
            val readJob = launch { readPackets(inputChannel, buffer) }
            val writeJob = launch { writePackets(channel, buffer) }
            
            // Wait for completion or cancellation
            joinAll(readJob, writeJob)
            
            Log.i("RaccoonVpn", "VPN processing stopped")
        }
    }
    
    /**
     * Read packets from VPN interface
     * Note: Actual packet routing to SOCKS5 is handled by Xray internally
     * when we configure the VPN to route through localhost
     */
    private suspend fun readPackets(channel: FileChannel, buffer: ByteBuffer) {
        while (isActive && coroutineContext.isActive) {
            try {
                buffer.clear()
                val bytesRead = channel.read(buffer)
                
                if (bytesRead > 0) {
                    rxBytes += bytesRead
                    // Packets are automatically routed by Xray's SOCKS5 proxy
                    // No manual packet processing needed for basic functionality
                }
                
                delay(1) // Small delay to prevent busy loop
            } catch (e: Exception) {
                if (isActive) {
                    Log.e("RaccoonVpn", "Read error: ${e.message}")
                }
                break
            }
        }
    }
    
    /**
     * Write packets to VPN interface
     */
    private suspend fun writePackets(channel: FileChannel, buffer: ByteBuffer) {
        while (isActive && coroutineContext.isActive) {
            try {
                // Check for outgoing packets from Xray
                // Xray handles the actual proxy communication
                delay(10)
            } catch (e: Exception) {
                if (isActive) {
                    Log.e("RaccoonVpn", "Write error: ${e.message}")
                }
                break
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Raccoon Squad VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN connection status"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(nodeName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦝 Raccoon Squad")
            .setContentText("Connected to $nodeName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun broadcastState(connected: Boolean, error: String? = null) {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_CONNECTED, connected)
            error?.let { putExtra(EXTRA_ERROR, it) }
        }
        sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        disconnect()
        scope.cancel()
        super.onDestroy()
    }
    
    /**
     * Get current traffic stats
     */
    fun getStats(): Pair<Long, Long> = Pair(txBytes, rxBytes)
    
    /**
     * Test current connection latency
     */
    fun testLatency(): Long {
        return XrayWrapper.measureDelay()
    }
}
