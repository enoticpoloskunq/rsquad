package com.raccoonsquad.core.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.raccoonsquad.MainActivity
import com.raccoonsquad.R
import com.raccoonsquad.core.vpn.RaccoonVpnService
import com.raccoonsquad.data.repository.NodeRepository
import com.raccoonsquad.core.log.LogManager

class RaccoonTileService : TileService() {

    companion object {
        private const val TAG = "TileService"
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
        LogManager.i(TAG, "Tile listening, isActive=${RaccoonVpnService.isActive}")
    }

    override fun onClick() {
        super.onClick()
        LogManager.i(TAG, "Tile clicked, isActive=${RaccoonVpnService.isActive}")

        if (RaccoonVpnService.isActive) {
            val intent = Intent(this, RaccoonVpnService::class.java).apply {
                action = RaccoonVpnService.ACTION_DISCONNECT
            }
            startService(intent)
        } else {
            connectToLastNode()
        }
        
        updateTile()
    }

    private fun connectToLastNode() {
        val sharedPrefs = getSharedPreferences("raccoon_prefs", MODE_PRIVATE)
        val lastId = sharedPrefs.getString("last_connected_id", null)
        
        LogManager.i(TAG, "Last ID: $lastId")
        
        if (lastId != null) {
            val repository = NodeRepository.getInstance(this)
            val nodes = repository.getNodesSync()
            val node = nodes.find { it.id == lastId }
            
            if (node != null) {
                LogManager.i(TAG, "Found node: ${node.name}")
                startVpnWithNode(node)
                return
            }
        }
        
        LogManager.i(TAG, "No last node, opening app")
        openApp()
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
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        // Use PendingIntent for Android 11+
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
            tile.subtitle = RaccoonVpnService.currentConfig?.name ?: "Connected"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.app_name)
            tile.subtitle = "Disconnected"
        }
        
        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
        tile.updateTile()
    }
}
