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
    private var xrayInitialized = false
    
    override fun onCreate() {
        super.onCreate()
        LogManager.i(TAG, "=== VPN Service onCreate ===")
        LogManager.flush()
        
        createNotificationChannel()
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
                disconnect()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }
    
    private fun connect(config: VlessConfig) {
        LogManager.i(TAG, "=== connect() ===")
        LogManager.flush()
        
        if (isActive) {
            LogManager.w(TAG, "Already active, disconnecting first")
            disconnect()
        }
        
        currentConfig = config
        
        try {
            // Init Xray LAZILY
            if (!xrayInitialized) {
                LogManager.i(TAG, "Initializing Xray...")
                LogManager.flush()
                
                try {
                    XrayWrapper.init(applicationContext)
                    xrayInitialized = true
                    LogManager.i(TAG, "Xray initialized OK")
                } catch (e: Throwable) {
                    LogManager.e(TAG, "XRAY INIT CRASHED", e)
                    LogManager.flush()
                    disconnect()
                    return
                }
            }
            LogManager.flush()
            
            // Generate config
            LogManager.d(TAG, "Generating Xray config...")
            LogManager.flush()
            
            val xrayConfig = XrayWrapper.generateConfig(config)
            LogManager.d(TAG, "Config generated: ${xrayConfig.length} bytes")
            LogManager.d(TAG, "Config preview: ${xrayConfig.take(200)}...")
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
            
            // Update notification
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, createNotification(config.name))
            } catch (e: Throwable) {
                LogManager.w(TAG, "Could not update notification")
            }
            
            LogManager.i(TAG, "=== VPN CONNECTED (Xray proxy mode on :10808) ===")
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
        
        isActive = false
        
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
        
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Throwable) {
            LogManager.e(TAG, "Error stopForeground", e)
        }
        
        LogManager.flush()
        stopSelf()
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
        LogManager.i(TAG, "VPN onDestroy")
        LogManager.flush()
        disconnect()
        super.onDestroy()
    }
}
