package com.raccoonsquad.core.stats

import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import com.raccoonsquad.core.log.LogManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Traffic statistics tracker for VPN connection
 * Uses VPN service stats instead of UID stats
 */
object TrafficStats {
    private const val TAG = "TrafficStats"
    private const val UPDATE_INTERVAL_MS = 2000L  // Update every 2 seconds instead of 1
    
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastUpdateTime = 0L
    
    private val totalRx = AtomicLong(0)
    private val totalTx = AtomicLong(0)
    
    private val currentRxSpeed = AtomicLong(0)
    private val currentTxSpeed = AtomicLong(0)
    
    private var isTracking = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Callback to get traffic from VPN service
    private var getRxBytes: (() -> Long)? = null
    private var getTxBytes: (() -> Long)? = null
    
    private val listeners = mutableListOf<(rxSpeed: Long, txSpeed: Long, totalRx: Long, totalTx: Long) -> Unit>()
    
    val downloadSpeed: Long get() = currentRxSpeed.get()
    val uploadSpeed: Long get() = currentTxSpeed.get()
    val totalDownloaded: Long get() = totalRx.get()
    val totalUploaded: Long get() = totalTx.get()
    
    val downloadSpeedFormatted: String get() = formatSpeed(currentRxSpeed.get())
    val uploadSpeedFormatted: String get() = formatSpeed(currentTxSpeed.get())
    val totalDownloadedFormatted: String get() = formatBytes(totalRx.get())
    val totalUploadedFormatted: String get() = formatBytes(totalTx.get())
    
    /**
     * Start tracking with custom byte counters (for VPN service)
     */
    fun startTracking(
        rxBytesProvider: () -> Long,
        txBytesProvider: () -> Long
    ) {
        if (isTracking) return
        
        getRxBytes = rxBytesProvider
        getTxBytes = txBytesProvider
        isTracking = true
        
        lastRxBytes = rxBytesProvider()
        lastTxBytes = txBytesProvider()
        lastUpdateTime = System.currentTimeMillis()
        totalRx.set(0)
        totalTx.set(0)
        currentRxSpeed.set(0)
        currentTxSpeed.set(0)
        
        LogManager.i(TAG, "Started tracking traffic")
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
    }
    
    /**
     * Legacy method - uses UID stats (may not work for VPN)
     */
    fun startTracking(appUid: Int) {
        if (isTracking) return
        isTracking = true
        
        lastRxBytes = TrafficStats.getUidRxBytes(appUid)
        lastTxBytes = TrafficStats.getUidTxBytes(appUid)
        lastUpdateTime = System.currentTimeMillis()
        totalRx.set(0)
        totalTx.set(0)
        currentRxSpeed.set(0)
        currentTxSpeed.set(0)
        
        LogManager.i(TAG, "Started tracking traffic for UID=$appUid (legacy mode)")
        
        // Use legacy providers
        getRxBytes = { TrafficStats.getUidRxBytes(appUid) }
        getTxBytes = { TrafficStats.getUidTxBytes(appUid) }
        
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
    }
    
    fun stopTracking() {
        isTracking = false
        handler.removeCallbacks(updateRunnable)
        getRxBytes = null
        getTxBytes = null
        LogManager.i(TAG, "Stopped tracking. Total: ↓${formatBytes(totalRx.get())} ↑${formatBytes(totalTx.get())}")
    }
    
    /**
     * Reset counters when switching nodes
     */
    fun reset() {
        totalRx.set(0)
        totalTx.set(0)
        currentRxSpeed.set(0)
        currentTxSpeed.set(0)
        lastRxBytes = getRxBytes?.invoke() ?: 0
        lastTxBytes = getTxBytes?.invoke() ?: 0
        lastUpdateTime = System.currentTimeMillis()
        LogManager.i(TAG, "Traffic stats reset")
    }
    
    fun addListener(listener: (rxSpeed: Long, txSpeed: Long, totalRx: Long, totalTx: Long) -> Unit) {
        listeners.add(listener)
    }
    
    fun removeListener(listener: (rxSpeed: Long, txSpeed: Long, totalRx: Long, totalTx: Long) -> Unit) {
        listeners.remove(listener)
    }
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isTracking) return
            
            try {
                val rxProvider = getRxBytes
                val txProvider = getTxBytes
                
                if (rxProvider != null && txProvider != null) {
                    val currentRx = rxProvider()
                    val currentTx = txProvider()
                    val currentTime = System.currentTimeMillis()
                    
                    val timeDiff = currentTime - lastUpdateTime
                    if (timeDiff > 0) {
                        val rxDiff = currentRx - lastRxBytes
                        val txDiff = currentTx - lastTxBytes
                        
                        // Handle counter reset (e.g., after device reboot)
                        val rxDiffSafe = if (rxDiff >= 0) rxDiff else currentRx
                        val txDiffSafe = if (txDiff >= 0) txDiff else currentTx
                        
                        val rxSpeed = (rxDiffSafe * 1000 / timeDiff).coerceAtLeast(0)
                        val txSpeed = (txDiffSafe * 1000 / timeDiff).coerceAtLeast(0)
                        
                        currentRxSpeed.set(rxSpeed)
                        currentTxSpeed.set(txSpeed)
                        
                        totalRx.addAndGet(rxDiffSafe.coerceAtLeast(0))
                        totalTx.addAndGet(txDiffSafe.coerceAtLeast(0))
                        
                        notifyListeners()
                    }
                    
                    lastRxBytes = currentRx
                    lastTxBytes = currentTx
                    lastUpdateTime = currentTime
                }
                
            } catch (e: Throwable) {
                LogManager.e(TAG, "Error updating traffic stats", e)
            }
            
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }
    
    private fun notifyListeners() {
        handler.post {
            listeners.forEach { listener ->
                try {
                    listener(currentRxSpeed.get(), currentTxSpeed.get(), totalRx.get(), totalTx.get())
                } catch (e: Throwable) {
                    LogManager.e(TAG, "Error in traffic listener", e)
                }
            }
        }
    }
    
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024))
        }
    }
}
