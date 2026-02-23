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
import com.raccoonsquad.MainActivity
import com.raccoonsquad.R
import com.raccoonsquad.core.xray.XrayWrapper
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.compat.RomCompat
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.nio.ByteBuffer

class RaccoonVpnService : VpnService() {
    
    companion object {
        const val ACTION_CONNECT = "com.raccoonsquad.CONNECT"
        const val ACTION_DISCONNECT = "com.raccoonsquad.DISCONNECT"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "raccoon_vpn_channel"
        
        private const val TAG = "VPN"
        
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
        LogManager.i(TAG, "=== VPN Service onCreate ===")
        LogManager.flush()
        
        try {
            createNotificationChannel()
            LogManager.d(TAG, "Notification channel created")
            LogManager.flush()
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to create notification channel", e)
            LogManager.flush()
        }
        
        try {
            LogManager.i(TAG, "Initializing XrayWrapper...")
            LogManager.flush()
            XrayWrapper.init(applicationContext)
            LogManager.i(TAG, "XrayWrapper initialized")
            LogManager.flush()
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to init XrayWrapper", e)
            LogManager.flush()
        }
        
        RomCompat.applyProcessOptimizations()
        LogManager.i(TAG, "VPN Service onCreate DONE")
        LogManager.flush()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogManager.i(TAG, "=== onStartCommand: ${intent?.action} ===")
        LogManager.flush()
        
        when (intent?.action) {
            ACTION_CONNECT -> {
                @Suppress("DEPRECATION")
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("config", VlessConfig::class.java)
                } else {
                    intent.getParcelableExtra("config")
                }
                
                if (config != null) {
                    LogManager.i(TAG, "Config: ${config.name} @ ${config.serverAddress}:${config.port}")
                    LogManager.flush()
                    
                    try {
                        LogManager.d(TAG, "Starting foreground...")
                        LogManager.flush()
                        
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
                        
                        LogManager.i(TAG, "Foreground started")
                        LogManager.flush()
                        
                    } catch (e: Exception) {
                        LogManager.e(TAG, "Failed to start foreground", e)
                        LogManager.flush()
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    
                    connect(config)
                } else {
                    LogManager.e(TAG, "No config in intent!")
                    LogManager.flush()
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                LogManager.i(TAG, "Disconnect requested")
                LogManager.flush()
                disconnect()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }
    
    private fun connect(config: VlessConfig) {
        LogManager.i(TAG, "=== connect() START ===")
        LogManager.flush()
        
        if (isActive) {
            LogManager.w(TAG, "Already active, disconnecting first")
            LogManager.flush()
            disconnect()
        }
        
        currentConfig = config
        
        try {
            // Generate Xray config
            LogManager.d(TAG, "Generating Xray config...")
            LogManager.flush()
            
            val xrayConfig = XrayWrapper.generateConfig(config)
            LogManager.d(TAG, "Config generated: ${xrayConfig.length} bytes")
            LogManager.flush()
            
            // Build VPN interface
            val mtu = config.mtu.toIntOrNull() ?: RomCompat.getRecommendedMtu()
            LogManager.i(TAG, "Building VPN interface (MTU=$mtu)...")
            LogManager.flush()
            
            val builder = Builder()
                .setSession("Raccoon VPN")
                .setMtu(mtu)
                .addAddress("10.66.66.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
            
            try {
                builder.addDisallowedApplication(packageName)
                LogManager.d(TAG, "App excluded from tunnel")
            } catch (e: Exception) {
                LogManager.w(TAG, "Could not exclude app: ${e.message}")
            }
            LogManager.flush()
            
            LogManager.d(TAG, "Establishing VPN interface...")
            LogManager.flush()
            
            vpnInterface = builder.establish()
            
            if (vpnInterface == null) {
                LogManager.e(TAG, "VPN interface is NULL - permission revoked?")
                LogManager.flush()
                disconnect()
                return
            }
            
            LogManager.i(TAG, "VPN interface established (fd=${vpnInterface!!.fd})")
            LogManager.flush()
            
            // Start Xray
            LogManager.i(TAG, "Starting Xray...")
            LogManager.flush()
            
            val started = XrayWrapper.start(xrayConfig)
            
            if (!started) {
                LogManager.e(TAG, "Xray failed to start")
                LogManager.flush()
                disconnect()
                return
            }
            
            isActive = true
            
            // Update notification
            try {
                val notification = createNotification(config.name)
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                LogManager.w(TAG, "Could not update notification: ${e.message}")
            }
            
            // Start packet thread
            startVpnThread()
            
            LogManager.i(TAG, "=== VPN CONNECTED ===")
            LogManager.i(TAG, "Node: ${config.name}")
            LogManager.i(TAG, "Server: ${config.serverAddress}:${config.port}")
            LogManager.flush()
            
        } catch (e: Exception) {
            LogManager.e(TAG, "connect() FAILED", e)
            LogManager.flush()
            disconnect()
        }
    }
    
    private fun startVpnThread() {
        LogManager.d(TAG, "Starting VPN packet thread")
        LogManager.flush()
        
        isVpnRunning = true
        vpnThread = Thread {
            RomCompat.applyProcessOptimizations()
            
            val fd = vpnInterface?.fileDescriptor
            if (fd == null) {
                LogManager.e(TAG, "No fd in packet thread")
                LogManager.flush()
                return@Thread
            }
            
            val input = FileInputStream(fd)
            val buffer = ByteBuffer.allocate(32767)
            
            LogManager.d(TAG, "Packet thread running")
            LogManager.flush()
            
            while (isVpnRunning && isActive) {
                try {
                    val len = input.read(buffer.array())
                    if (len > 0) buffer.clear()
                } catch (e: Exception) {
                    if (isVpnRunning) {
                        LogManager.e(TAG, "Packet thread error", e)
                        LogManager.flush()
                    }
                }
            }
            
            LogManager.d(TAG, "Packet thread stopped")
            LogManager.flush()
        }.apply { start() }
    }
    
    private fun disconnect() {
        LogManager.i(TAG, "=== disconnect() ===")
        LogManager.flush()
        
        isVpnRunning = false
        vpnThread?.interrupt()
        vpnThread = null
        
        isActive = false
        
        try {
            XrayWrapper.stop()
        } catch (e: Exception) {
            LogManager.e(TAG, "Error stopping Xray", e)
        }
        
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            LogManager.e(TAG, "Error closing interface", e)
        }
        vpnInterface = null
        
        currentConfig = null
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            LogManager.e(TAG, "Error stopForeground", e)
        }
        
        LogManager.flush()
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Raccoon VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN connection"
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
        
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦝 Raccoon Squad")
            .setContentText(nodeName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    override fun onDestroy() {
        LogManager.i(TAG, "VPN Service onDestroy")
        LogManager.flush()
        disconnect()
        scope.cancel()
        super.onDestroy()
    }
}
