package com.raccoonsquad.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.TrafficStats as AndroidTrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.raccoonsquad.MainActivity
import com.raccoonsquad.R
import com.raccoonsquad.core.xray.XrayWrapper
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.compat.RomCompat
import com.raccoonsquad.core.stats.TrafficStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class RaccoonVpnService : VpnService() {
    
    companion object {
        const val ACTION_CONNECT = "com.raccoonsquad.CONNECT"
        const val ACTION_DISCONNECT = "com.raccoonsquad.DISCONNECT"
        const val ACTION_RECONNECT = "com.raccoonsquad.RECONNECT"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "raccoon_vpn_channel"
        const val KILL_SWITCH_CHANNEL_ID = "raccoon_kill_switch_channel"
        
        private const val TAG = "VPN"
        private const val RECONNECT_DELAY_MS = 2000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        
        var isActive = false
            private set
        
        var currentConfig: VlessConfig? = null
            private set
        
        var isKillSwitchActive = false
            private set
        
        // Callback for UI updates
        var onConnectionLost: (() -> Unit)? = null
        var onReconnecting: (() -> Unit)? = null
        var onReconnected: (() -> Unit)? = null
    }
    
    private var vpnInterface: ParcelFileDescriptor? = null
    private var xrayInitialized = false
    private var reconnectAttempts = 0
    private var isUserDisconnect = false
    private var killSwitchInterface: ParcelFileDescriptor? = null
    private val reconnectHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val connectionMonitorRunnable = object : Runnable {
        override fun run() {
            if (isActive && !XrayWrapper.isRunning()) {
                LogManager.w(TAG, "Xray stopped unexpectedly!")
                handleUnexpectedDisconnect()
            }
            if (isActive) {
                reconnectHandler.postDelayed(this, 3000) // Check every 3 seconds
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        LogManager.i(TAG, "=== VPN Service onCreate ===")
        LogManager.flush()
        
        createNotificationChannel()
        createKillSwitchNotificationChannel()
        RomCompat.applyProcessOptimizations()
        
        LogManager.i(TAG, "VPN Service created")
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
                    LogManager.i(TAG, "Config: ${config.name}")
                    LogManager.flush()
                    
                    try {
                        val notification = createNotification("Starting...")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }
                        LogManager.i(TAG, "Foreground started")
                    } catch (e: Throwable) {
                        LogManager.e(TAG, "Failed foreground", e)
                        LogManager.flush()
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    
                    connect(config)
                } else {
                    LogManager.e(TAG, "No config!")
                    LogManager.flush()
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                isUserDisconnect = true
                disconnect()
                return START_NOT_STICKY
            }
            ACTION_RECONNECT -> {
                if (currentConfig != null) {
                    LogManager.i(TAG, "Manual reconnect triggered")
                    reconnectAttempts = 0
                    connect(currentConfig!!)
                }
            }
        }
        return START_STICKY
    }
    
    private fun getSettings(): Pair<Boolean, Boolean> {
        return try {
            runBlocking {
                val settingsManager = com.raccoonsquad.data.settings.SettingsManager(applicationContext)
                val killSwitch = settingsManager.killSwitch.first()
                val autoReconnect = settingsManager.autoReconnect.first()
                Pair(killSwitch, autoReconnect)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to read settings", e)
            Pair(false, true) // Defaults: Kill Switch off, Auto-reconnect on
        }
    }
    
    private fun connect(config: VlessConfig) {
        LogManager.i(TAG, "=== connect() ===")
        LogManager.flush()
        
        currentConfig = config
        
        // If already running, just switch Xray config (keep VPN interface)
        val isSwitching = isActive && vpnInterface != null
        
        if (isSwitching) {
            LogManager.i(TAG, "Switching to new config (keeping VPN interface)...")
            // Reset traffic stats when switching nodes
            TrafficStats.reset()
            try {
                XrayWrapper.stop()
                Thread.sleep(100) // Wait for Xray to stop
            } catch (e: Throwable) {
                LogManager.e(TAG, "Error stopping Xray during switch", e)
            }
        }
        
        try {
            // Init Xray if needed
            if (!xrayInitialized) {
                LogManager.i(TAG, "Initializing Xray...")
                LogManager.flush()
                
                XrayWrapper.init(applicationContext)
                xrayInitialized = true
                LogManager.i(TAG, "Xray initialized OK")
            }
            LogManager.flush()
            
            // Generate config
            LogManager.d(TAG, "Generating Xray config...")
            LogManager.flush()
            
            val xrayConfig = XrayWrapper.generateConfig(config)
            LogManager.d(TAG, "Config generated: ${xrayConfig.length} bytes")
            LogManager.flush()
            
            // Build VPN interface only if not switching
            if (!isSwitching) {
                val mtu = config.mtu.toIntOrNull() ?: RomCompat.getRecommendedMtu()
                LogManager.i(TAG, "Building VPN interface (MTU=$mtu)...")
                LogManager.flush()
                
                val builder = Builder()
                    .setSession("Raccoon VPN")
                    .setMtu(mtu)
                    .addAddress("10.66.66.1", 24)
                    .addRoute("0.0.0.0", 0)
                    // IPv6 support
                    .addAddress("fd00:1::1", 64)
                    .addRoute("::", 0)
                    // DNS - these will be used by system resolver
                    // Xray will handle actual DNS resolution through proxy
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("2001:4860:4860::8888")
                
                // Try to set underlying networks (may help with some carriers)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    try {
                        builder.setUnderlyingNetworks(null)
                    } catch (e: Throwable) {
                        LogManager.w(TAG, "Could not set underlying networks")
                    }
                }
                
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Throwable) {
                    LogManager.w(TAG, "Could not exclude self from VPN")
                }
                
                LogManager.d(TAG, "Establishing VPN interface...")
                LogManager.flush()
                
                vpnInterface = builder.establish()
                
                if (vpnInterface == null) {
                    LogManager.e(TAG, "VPN interface is NULL! VPN permission revoked?")
                    LogManager.flush()
                    disconnect()
                    return
                }
                
                LogManager.i(TAG, "VPN established fd=${vpnInterface!!.fd}")
            } else {
                LogManager.i(TAG, "Reusing existing VPN interface fd=${vpnInterface!!.fd}")
            }
            LogManager.flush()
            
            // Start Xray with VPN TUN fd
            LogManager.i(TAG, "Starting Xray with tunFd=${vpnInterface!!.fd}...")
            LogManager.flush()
            
            val started = try {
                XrayWrapper.start(xrayConfig, vpnInterface!!.fd)
            } catch (e: Throwable) {
                LogManager.e(TAG, "XRAY START CRASHED", e)
                LogManager.flush()
                false
            }
            
            if (!started) {
                LogManager.e(TAG, "Xray failed to start")
                LogManager.flush()
                disconnect()
                return
            }
            
            isActive = true
            isUserDisconnect = false
            isKillSwitchActive = false
            reconnectAttempts = 0
            
            // Start connection monitor
            reconnectHandler.post(connectionMonitorRunnable)
            
            // Start traffic tracking - use total system traffic
            TrafficStats.startTracking(
                rxBytesProvider = { 
                    AndroidTrafficStats.getTotalRxBytes() - AndroidTrafficStats.getMobileRxBytes()
                },
                txBytesProvider = { 
                    AndroidTrafficStats.getTotalTxBytes() - AndroidTrafficStats.getMobileTxBytes()
                }
            )
            
            // Add listener to update notification with stats
            TrafficStats.addListener { _, _, _, _ ->
                try {
                    updateNotificationWithStats(
                        TrafficStats.downloadSpeedFormatted,
                        TrafficStats.uploadSpeedFormatted,
                        TrafficStats.totalDownloadedFormatted,
                        TrafficStats.totalUploadedFormatted
                    )
                } catch (e: Throwable) {
                    // Ignore
                }
            }
            
            // Save last connected ID for tile
            try {
                val sharedPrefs = getSharedPreferences("raccoon_prefs", MODE_PRIVATE)
                sharedPrefs.edit().putString("last_connected_id", config.id).apply()
            } catch (e: Throwable) {
                LogManager.w(TAG, "Could not save last ID")
            }
            
            // Update notification
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, createNotification(config.name))
            } catch (e: Throwable) {
                LogManager.w(TAG, "Could not update notification")
            }
            
            LogManager.i(TAG, "=== VPN CONNECTED ===")
            LogManager.flush()
            
        } catch (e: Throwable) {
            LogManager.e(TAG, "connect() CRASHED", e)
            LogManager.flush()
            disconnect()
        }
    }
    
    private fun disconnect() {
        LogManager.i(TAG, "=== disconnect() ===")
        LogManager.flush()
        
        // Stop connection monitor
        reconnectHandler.removeCallbacks(connectionMonitorRunnable)
        
        isActive = false
        
        // Stop traffic tracking
        TrafficStats.stopTracking()
        
        try {
            XrayWrapper.stop()
        } catch (e: Throwable) {
            LogManager.e(TAG, "Error stopping Xray", e)
        }
        
        try {
            vpnInterface?.close()
        } catch (e: Throwable) {
            LogManager.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        
        currentConfig = null
        
        // Handle Kill Switch
        if (!isUserDisconnect) {
            val (killSwitchEnabled, _) = getSettings()
            if (killSwitchEnabled) {
                LogManager.w(TAG, "Kill Switch activated - blocking internet")
                activateKillSwitch()
            }
        } else {
            // User intentionally disconnected - disable kill switch if active
            deactivateKillSwitch()
        }
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Throwable) {
            LogManager.e(TAG, "Error stopForeground", e)
        }
        
        LogManager.flush()
        stopSelf()
    }
    
    private fun handleUnexpectedDisconnect() {
        LogManager.w(TAG, "Handling unexpected disconnect...")
        
        val (killSwitchEnabled, autoReconnectEnabled) = getSettings()
        
        if (autoReconnectEnabled && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            LogManager.i(TAG, "Auto-reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS")
            
            // Notify UI
            onReconnecting?.invoke()
            
            reconnectHandler.postDelayed({
                if (currentConfig != null && !isUserDisconnect) {
                    try {
                        connect(currentConfig!!)
                        onReconnected?.invoke()
                    } catch (e: Throwable) {
                        LogManager.e(TAG, "Reconnect failed", e)
                        handleUnexpectedDisconnect()
                    }
                }
            }, RECONNECT_DELAY_MS)
        } else if (killSwitchEnabled) {
            LogManager.w(TAG, "Max reconnect attempts reached or auto-reconnect disabled, activating Kill Switch")
            isActive = false
            activateKillSwitch()
            onConnectionLost?.invoke()
        } else {
            isActive = false
            onConnectionLost?.invoke()
        }
    }
    
    private fun activateKillSwitch() {
        if (isKillSwitchActive) return
        
        try {
            LogManager.i(TAG, "Activating Kill Switch...")
            
            // Create a "block all" VPN interface
            val builder = Builder()
                .setSession("Raccoon Kill Switch")
                .setMtu(1280)
                .addAddress("10.0.0.1", 32)
                // Don't add any routes - this blocks all traffic
            
            killSwitchInterface = builder.establish()
            isKillSwitchActive = true
            
            // Show notification
            showKillSwitchNotification()
            
            LogManager.i(TAG, "Kill Switch activated - internet blocked")
        } catch (e: Throwable) {
            LogManager.e(TAG, "Failed to activate Kill Switch", e)
        }
    }
    
    private fun deactivateKillSwitch() {
        if (!isKillSwitchActive) return
        
        try {
            LogManager.i(TAG, "Deactivating Kill Switch...")
            killSwitchInterface?.close()
            killSwitchInterface = null
            isKillSwitchActive = false
            
            // Remove notification
            val nm = getSystemService(NotificationManager::class.java)
            nm.cancel(2)
            
            LogManager.i(TAG, "Kill Switch deactivated")
        } catch (e: Throwable) {
            LogManager.e(TAG, "Failed to deactivate Kill Switch", e)
        }
    }
    
    private fun createKillSwitchNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                KILL_SWITCH_CHANNEL_ID, 
                "Kill Switch", 
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Kill Switch active - internet blocked"
            channel.setShowBadge(true)
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun showKillSwitchNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        // Action to deactivate kill switch
        val deactivateIntent = Intent(this, RaccoonVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val deactivatePendingIntent = PendingIntent.getService(
            this, 2, deactivateIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = androidx.core.app.NotificationCompat.Builder(this, KILL_SWITCH_CHANNEL_ID)
            .setContentTitle("🛡️ Kill Switch активен")
            .setContentText("Интернет заблокирован. Нажмите для отключения.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_STATUS)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отключить Kill Switch",
                deactivatePendingIntent
            )
            .build()
        
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(2, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Raccoon VPN", NotificationManager.IMPORTANCE_LOW)
            channel.description = "VPN connection status"
            channel.setShowBadge(false)
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(nodeName: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        // Disconnect action
        val disconnectIntent = Intent(this, RaccoonVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦝 $nodeName")
            .setContentText("VPN активен • Нажмите для открытия")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отключить",
                disconnectPendingIntent
            )
            .build()
    }
    
    fun updateNotificationWithStats(downloadSpeed: String, uploadSpeed: String, totalDownload: String, totalUpload: String) {
        try {
            val intent = Intent(this, RaccoonVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            val disconnectPendingIntent = PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            
            val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🦝 ${currentConfig?.name ?: "VPN"}")
                .setContentText("↓ $downloadSpeed  ↑ $uploadSpeed")
                .setSubText("Всего: ↓$totalDownload ↑$totalUpload")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .setSilent(true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Отключить",
                    disconnectPendingIntent
                )
                .build()
            
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Throwable) {
            LogManager.w(TAG, "Could not update notification with stats")
        }
    }
    
    override fun onDestroy() {
        LogManager.i(TAG, "VPN onDestroy")
        LogManager.flush()
        reconnectHandler.removeCallbacks(connectionMonitorRunnable)
        deactivateKillSwitch()
        disconnect()
        super.onDestroy()
    }
}
