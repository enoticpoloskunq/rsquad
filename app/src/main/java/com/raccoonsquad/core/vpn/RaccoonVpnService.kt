package com.raccoonsquad.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import com.raccoonsquad.MainActivity
import com.raccoonsquad.R
import com.raccoonsquad.core.xray.XrayWrapper
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.compat.RomCompat
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
        
        private const val TAG = "VpnService"
        
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
        LogManager.i(TAG, "VPN Service onCreate")
        LogManager.i(TAG, "ROM: ${RomCompat.romName}")
        
        createNotificationChannel()
        XrayWrapper.init(applicationContext)
        
        // Apply ROM-specific process optimizations
        RomCompat.applyProcessOptimizations()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogManager.i(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_CONNECT -> {
                @Suppress("DEPRECATION")
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("config", VlessConfig::class.java)
                } else {
                    intent.getParcelableExtra("config")
                }
                
                if (config != null) {
                    LogManager.i(TAG, "Starting VPN for: ${config.name}")
                    
                    // Start foreground FIRST for Android 8+
                    val notification = createNotification("Connecting...")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIFICATION_ID, 
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    LogManager.i(TAG, "Foreground service started")
                    
                    connect(config)
                } else {
                    LogManager.e(TAG, "No config provided")
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                LogManager.i(TAG, "Disconnect requested")
                disconnect()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }
    
    private fun connect(config: VlessConfig) {
        LogManager.i(TAG, "connect() called for: ${config.name}")
        
        if (isActive) {
            LogManager.w(TAG, "Already active, disconnecting first")
            disconnect()
        }
        
        currentConfig = config
        
        try {
            // Generate Xray config
            LogManager.d(TAG, "Generating Xray config...")
            val xrayConfig = XrayWrapper.generateConfig(config)
            LogManager.d(TAG, "Xray config generated (${xrayConfig.length} bytes)")
            
            // Build VPN interface
            val mtu = config.mtu.toIntOrNull() ?: RomCompat.getRecommendedMtu()
            LogManager.i(TAG, "Building VPN interface with MTU: $mtu")
            
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
                LogManager.d(TAG, "App excluded from VPN tunnel")
            } catch (e: Exception) {
                LogManager.w(TAG, "Failed to exclude self: ${e.message}")
            }
            
            LogManager.i(TAG, "Establishing VPN interface...")
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                LogManager.e(TAG, "VPN interface is null - permission revoked?")
                disconnect()
                return
            }
            
            LogManager.i(TAG, "VPN interface established, fd: ${vpnInterface!!.fd}")
            
            // Start Xray
            LogManager.i(TAG, "Starting Xray core...")
            if (!XrayWrapper.start(xrayConfig)) {
                LogManager.e(TAG, "Failed to start Xray core")
                disconnect()
                return
            }
            LogManager.i(TAG, "Xray core started successfully")
            
            isActive = true
            
            // Update notification with node name
            val notification = createNotification(config.name)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
            
            // Start VPN packet processing
            startVpnThread()
            
            LogManager.i(TAG, "=== VPN CONNECTED ===")
            LogManager.i(TAG, "Node: ${config.name}")
            LogManager.i(TAG, "Server: ${config.serverAddress}:${config.port}")
            LogManager.i(TAG, "MTU: $mtu")
            LogManager.i(TAG, "ROM: ${RomCompat.romName}")
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to establish VPN", e)
            disconnect()
        }
    }
    
    private fun startVpnThread() {
        LogManager.d(TAG, "Starting VPN packet thread")
        
        isVpnRunning = true
        vpnThread = Thread {
            // Set thread priority for this ROM
            RomCompat.applyProcessOptimizations()
            
            val fd = vpnInterface?.fileDescriptor
            if (fd == null) {
                LogManager.e(TAG, "No file descriptor in VPN thread")
                return@Thread
            }
            
            val input = FileInputStream(fd)
            val buffer = ByteBuffer.allocate(32767)
            
            LogManager.d(TAG, "VPN packet thread started")
            
            var packetsRead = 0L
            var lastLogTime = System.currentTimeMillis()
            
            while (isVpnRunning && isActive) {
                try {
                    val length = input.read(buffer.array())
                    if (length > 0) {
                        packetsRead++
                        buffer.clear()
                        
                        // Log stats every 30 seconds
                        val now = System.currentTimeMillis()
                        if (now - lastLogTime > 30000) {
                            LogManager.d(TAG, "Packets processed: $packetsRead")
                            lastLogTime = now
                        }
                    }
                } catch (e: Exception) {
                    if (isVpnRunning) {
                        LogManager.e(TAG, "VPN thread error", e)
                    }
                }
            }
            
            LogManager.d(TAG, "VPN packet thread stopped, total packets: $packetsRead")
        }.apply { start() }
    }
    
    private fun disconnect() {
        LogManager.i(TAG, "disconnect() called")
        
        isVpnRunning = false
        vpnThread?.interrupt()
        vpnThread = null
        
        isActive = false
        
        // Stop Xray
        try {
            LogManager.d(TAG, "Stopping Xray core...")
            XrayWrapper.stop()
            LogManager.d(TAG, "Xray core stopped")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error stopping Xray", e)
        }
        
        // Close interface
        try {
            vpnInterface?.close()
            LogManager.d(TAG, "VPN interface closed")
        } catch (e: Exception) {
            LogManager.e(TAG, "Error closing interface", e)
        }
        vpnInterface = null
        
        currentConfig = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        LogManager.i(TAG, "=== VPN DISCONNECTED ===")
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
            LogManager.d(TAG, "Notification channel created")
        }
    }
    
    private fun createNotification(nodeName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦝 Raccoon Squad")
            .setContentText("Connected to $nodeName")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    override fun onDestroy() {
        LogManager.i(TAG, "VPN Service onDestroy")
        disconnect()
        scope.cancel()
        super.onDestroy()
    }
}
