package com.raccoonsquad.core.stats

import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import com.raccoonsquad.core.log.LogManager
import java.util.concurrent.atomic.AtomicLong

/**
 * Traffic statistics tracker for VPN connection
 */
object TrafficStats {
    private const val TAG = "TrafficStats"
    private const val UPDATE_INTERVAL_MS = 1000L
    
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastUpdateTime = 0L
    
    private val totalRx = AtomicLong(0)
    private val totalTx = AtomicLong(0)
    
    private val currentRxSpeed = AtomicLong(0)
    private val currentTxSpeed = AtomicLong(0)
    
    private var uid: Int = -1
    private var isTracking = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val listeners = mutableListOf<(rxSpeed: Long, txSpeed: Long, totalRx: Long, totalTx: Long) -> Unit>()
    
    val downloadSpeed: Long get() = currentRxSpeed.get()
    val uploadSpeed: Long get() = currentTxSpeed.get()
    val totalDownloaded: Long get() = totalRx.get()
    val totalUploaded: Long get() = totalTx.get()
    
    val downloadSpeedFormatted: String get() = formatSpeed(currentRxSpeed.get())
    val uploadSpeedFormatted: String get() = formatSpeed(currentTxSpeed.get())
    val totalDownloadedFormatted: String get() = formatBytes(totalRx.get())
    val totalUploadedFormatted: String get() = formatBytes(totalTx.get())
    
    fun startTracking(appUid: Int) {
        if (isTracking) return
        
        uid = appUid
        isTracking = true
        
        lastRxBytes = TrafficStats.getUidRxBytes(uid)
        lastTxBytes = TrafficStats.getUidTxBytes(uid)
        lastUpdateTime = System.currentTimeMillis()
        totalRx.set(0)
        totalTx.set(0)
        currentRxSpeed.set(0)
        currentTxSpeed.set(0)
        
        LogManager.i(TAG, "Started tracking traffic for UID=$uid")
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL_MS)
    }
    
    fun stopTracking() {
        isTracking = false
        handler.removeCallbacks(updateRunnable)
        LogManager.i(TAG, "Stopped tracking. Total: ↓${formatBytes(totalRx.get())} ↑${formatBytes(totalTx.get())}")
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
                val currentRx = TrafficStats.getUidRxBytes(uid)
                val currentTx = TrafficStats.getUidTxBytes(uid)
                val currentTime = System.currentTimeMillis()
                
                val timeDiff = currentTime - lastUpdateTime
                if (timeDiff > 0) {
                    val rxDiff = currentRx - lastRxBytes
                    val txDiff = currentTx - lastTxBytes
                    
                    val rxSpeed = (rxDiff * 1000 / timeDiff).coerceAtLeast(0)
                    val txSpeed = (txDiff * 1000 / timeDiff).coerceAtLeast(0)
                    
                    currentRxSpeed.set(rxSpeed)
                    currentTxSpeed.set(txSpeed)
                    
                    totalRx.addAndGet(rxDiff.coerceAtLeast(0))
                    totalTx.addAndGet(txDiff.coerceAtLeast(0))
                    
                    notifyListeners()
                }
                
                lastRxBytes = currentRx
                lastTxBytes = currentTx
                lastUpdateTime = currentTime
                
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
