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
import com.raccoonsquad.data.model.VlessConfig
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream

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
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                // Config passed via intent extra
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
        
        // Build VPN interface
        val builder = Builder()
            .setSession("Raccoon Squad VPN")
            .addAddress("10.66.66.1", 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("8.8.4.4")
            .setMtu(if (config.mtu != "default") config.mtu.toIntOrNull() ?: 1500 else 1500)
        
        // Add exclusion for server
        try {
            builder.addDisallowedApplication("com.raccoonsquad")
        } catch (e: Exception) {
            Log.e("RaccoonVpn", "Failed to exclude self", e)
        }
        
        try {
            vpnInterface = builder.establish()
            isActive = true
            
            startForeground(NOTIFICATION_ID, createNotification(config.name))
            
            // TODO: Start Xray-core here
            // For now, just keep the VPN interface active
            startVpnLoop()
            
        } catch (e: Exception) {
            Log.e("RaccoonVpn", "Failed to establish VPN", e)
            disconnect()
        }
    }
    
    private fun startVpnLoop() {
        job = scope.launch {
            vpnInterface?.let { pfd ->
                val input = FileInputStream(pfd.fileDescriptor)
                val output = FileOutputStream(pfd.fileDescriptor)
                
                val buffer = ByteArray(32767)
                
                while (isActive && isActive) {
                    try {
                        // Read from VPN interface
                        val length = input.read(buffer)
                        if (length > 0) {
                            // TODO: Process packet through Xray-core
                            // For now, just a placeholder
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e("RaccoonVpn", "VPN loop error", e)
                        }
                    }
                }
            }
        }
    }
    
    private fun disconnect() {
        isActive = false
        currentConfig = null
        job?.cancel()
        job = null
        
        vpnInterface?.close()
        vpnInterface = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Raccoon Squad VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN connection status"
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
            .build()
    }
    
    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }
}
