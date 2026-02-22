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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class RaccoonVpnService : VpnService() {
    
    companion object {
        const val ACTION_CONNECT = "com.raccoonsquad.CONNECT"
        const val ACTION_DISCONNECT = "com.raccoonsquad.DISCONNECT"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "raccoon_vpn_channel"
        
        var isActive = false
            private set
        
        var currentConfig: VlessConfig? = null
            private set
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val vpnThread = VpnThread()
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        if (isActive) disconnect()
        
        currentConfig = config
        
        // Generate Xray config
        val xrayConfig = XrayWrapper.generateConfig(config)
        Log.d("RaccoonVpn", "Xray config:\n${xrayConfig.take(500)}")
        
        // Build VPN interface
        val mtu = config.mtu.toIntOrNull() ?: 1500
        val builder = Builder()
            .setSession("Raccoon Squad VPN")
            .setMtu(mtu)
            .addAddress("10.66.66.1", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
        
        // Exclude our app from VPN to avoid loops
        try {
            builder.addDisallowedApplication("com.raccoonsquad")
        } catch (e: Exception) {
            Log.e("RaccoonVpn", "Failed to exclude self", e)
        }
        
        try {
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                Log.e("RaccoonVpn", "VPN interface is null - revoked?")
                return
            }
            
            isActive = true
            
            // Start foreground notification
            startForeground(NOTIFICATION_ID, createNotification(config.name))
            
            // Start Xray (via wrapper)
            // Note: This requires libv2ray.aar to actually work
            XrayWrapper.start(xrayConfig)
            
            // Start VPN packet processing
            vpnThread.start()
            
            Log.d("RaccoonVpn", "VPN connected - MTU: $mtu")
            
        } catch (e: Exception) {
            Log.e("RaccoonVpn", "Failed to establish VPN", e)
            disconnect()
        }
    }
    
    private fun disconnect() {
        isActive = false
        
        // Stop Xray
        XrayWrapper.stop()
        
        // Stop VPN thread
        vpnThread.stopVpn()
        
        // Close interface
        vpnInterface?.close()
        vpnInterface = null
        
        currentConfig = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d("RaccoonVpn", "VPN disconnected")
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
    
    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
    
    /**
     * VPN packet processing thread
     * Reads from VPN interface and forwards to Xray
     */
    private inner class VpnThread {
        private var running = false
        private var thread: Thread? = null
        
        fun start() {
            running = true
            thread = Thread {
                val interfaceFd = vpnInterface?.fileDescriptor ?: return@Thread
                val input = FileInputStream(interfaceFd)
                val output = FileOutputStream(interfaceFd)
                
                val buffer = ByteBuffer.allocate(32767)
                
                Log.d("RaccoonVpn", "VPN thread started")
                
                while (running && isActive) {
                    try {
                        // Read packet from VPN interface
                        val length = input.read(buffer.array())
                        
                        if (length > 0) {
                            // TODO: Process packet through Xray
                            // This is where libv2ray would handle the packet
                            // For now, packets are processed internally by Xray
                            // via the SOCKS/HTTP proxy interface
                            
                            buffer.clear()
                        }
                        
                    } catch (e: Exception) {
                        if (running) {
                            Log.e("RaccoonVpn", "VPN thread error", e)
                        }
                    }
                }
                
                Log.d("RaccoonVpn", "VPN thread stopped")
            }.apply { start() }
        }
        
        fun stopVpn() {
            running = false
            thread?.interrupt()
            thread = null
        }
    }
}
