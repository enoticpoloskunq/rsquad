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
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var vpnThread: Thread? = null
    private var isVpnRunning = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        XrayWrapper.init(applicationContext)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: Start foreground IMMEDIATELY to prevent crash on Android 8+
        startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
        
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
                disconnect()
                return
            }
            
            // Start Xray
            if (!XrayWrapper.start(xrayConfig)) {
                Log.e("RaccoonVpn", "Failed to start Xray")
                disconnect()
                return
            }
            
            isActive = true
            
            // Update notification with node name
            val notification = createNotification(config.name)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
            
            // Start VPN packet processing
            startVpnThread()
            
            Log.d("RaccoonVpn", "VPN connected - MTU: $mtu")
            
        } catch (e: Exception) {
            Log.e("RaccoonVpn", "Failed to establish VPN", e)
            disconnect()
        }
    }
    
    private fun startVpnThread() {
        isVpnRunning = true
        vpnThread = Thread {
            val fd = vpnInterface?.fileDescriptor ?: return@Thread
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            val buffer = ByteBuffer.allocate(32767)
            
            Log.d("RaccoonVpn", "VPN thread started")
            
            while (isVpnRunning && isActive) {
                try {
                    val length = input.read(buffer.array())
                    if (length > 0) {
                        // Packets are handled by Xray SOCKS proxy
                        buffer.clear()
                    }
                } catch (e: Exception) {
                    if (isVpnRunning) {
                        Log.e("RaccoonVpn", "VPN thread error", e)
                    }
                }
            }
            
            Log.d("RaccoonVpn", "VPN thread stopped")
        }.apply { start() }
    }
    
    private fun disconnect() {
        isVpnRunning = false
        vpnThread?.interrupt()
        vpnThread = null
        
        isActive = false
        
        // Stop Xray
        XrayWrapper.stop()
        
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
        scope.cancel()
        super.onDestroy()
    }
}
