package com.raccoonsquad.core.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.raccoonsquad.MainActivity
import com.raccoonsquad.R
import com.raccoonsquad.core.vpn.RaccoonVpnService
import com.raccoonsquad.core.stats.TrafficStats
import com.raccoonsquad.data.repository.NodeRepository
import com.raccoonsquad.core.log.LogManager

class RaccoonTileService : TileService() {

    companion object {
        private const val TAG = "TileService"
        private const val UPDATE_INTERVAL_MS = 2000L
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (RaccoonVpnService.isActive) {
                updateTile()
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        LogManager.i(TAG, "Tile listening, isActive=${RaccoonVpnService.isActive}")
        
        // Start periodic updates when connected
        if (RaccoonVpnService.isActive) {
            handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
        }
    }
    
    override fun onStopListening() {
        super.onStopListening()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onClick() {
        super.onClick()
        LogManager.i(TAG, "Tile clicked, isActive=${RaccoonVpnService.isActive}")

        if (RaccoonVpnService.isActive) {
            // Disconnect
            val intent = Intent(this, RaccoonVpnService::class.java).apply {
                action = RaccoonVpnService.ACTION_DISCONNECT
            }
            startService(intent)
            handler.removeCallbacks(updateRunnable)
        } else {
            // Connect to last node or best node
            connectToBestNode()
        }
        
        updateTile()
    }

    private fun connectToBestNode() {
        val sharedPrefs = getSharedPreferences("raccoon_prefs", MODE_PRIVATE)
        val lastId = sharedPrefs.getString("last_connected_id", null)
        
        LogManager.i(TAG, "Last ID: $lastId")
        
        val repository = NodeRepository.getInstance(this)
        val nodes = repository.getNodesSync()
        
        if (nodes.isEmpty()) {
            LogManager.i(TAG, "No nodes, opening app")
            openApp()
            return
        }
        
        // Try last connected node first
        if (lastId != null) {
            val lastNode = nodes.find { it.id == lastId }
            if (lastNode != null) {
                LogManager.i(TAG, "Found last node: ${lastNode.name}")
                startVpnWithNode(lastNode)
                return
            }
        }
        
        // Find best node (favorite with lowest latency, or just lowest latency)
        val bestNode = nodes
            .filter { it.latency != null && it.latency > 0 }
            .minByOrNull { it.latency ?: Long.MAX_VALUE }
            ?: nodes.firstOrNull { it.isFavorite }
            ?: nodes.firstOrNull()
        
        if (bestNode != null) {
            LogManager.i(TAG, "Selected best node: ${bestNode.name}")
            startVpnWithNode(bestNode)
        } else {
            LogManager.i(TAG, "No suitable node, opening app")
            openApp()
        }
    }

    private fun startVpnWithNode(node: com.raccoonsquad.data.model.VlessConfig) {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            LogManager.w(TAG, "VPN permission not granted, opening app")
            openApp()
            return
        }

        val sharedPrefs = getSharedPreferences("raccoon_prefs", MODE_PRIVATE)
        sharedPrefs.edit().putString("last_connected_id", node.id).apply()

        val intent = Intent(this, RaccoonVpnService::class.java).apply {
            action = RaccoonVpnService.ACTION_CONNECT
            putExtra("config", node)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        LogManager.i(TAG, "Connect command sent for: ${node.name}")
        
        // Start updates
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        try {
            pendingIntent.send()
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to open app", e)
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        
        if (RaccoonVpnService.isActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.app_name)
            
            // Show node name and speed
            val nodeName = RaccoonVpnService.currentConfig?.name ?: "Connected"
            val speed = if (TrafficStats.downloadSpeed > 0 || TrafficStats.uploadSpeed > 0) {
                "↓${TrafficStats.downloadSpeedFormatted} ↑${TrafficStats.uploadSpeedFormatted}"
            } else {
                nodeName
            }
            tile.subtitle = speed
            
            // Show connection quality in state description
            tile.contentDescription = "VPN connected to $nodeName"
            
            // Update with raccoon icon when active
            tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.app_name)
            tile.subtitle = "Disconnected"
            tile.contentDescription = "VPN disconnected"
            tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        }
        
        tile.updateTile()
    }
}
