package com.raccoonsquad.core.speedtest

import com.raccoonsquad.core.log.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Speed Test module for measuring download/upload speeds
 */
data class SpeedTestResult(
    val downloadSpeed: Long,      // bytes per second
    val uploadSpeed: Long,        // bytes per second
    val latency: Long,            // milliseconds
    val jitter: Long,             // milliseconds
    val packetLoss: Float,        // percentage 0.0 - 100.0
    val downloadBytes: Long,      // total downloaded bytes
    val uploadBytes: Long,        // total uploaded bytes
    val testDuration: Long,       // milliseconds
    val serverName: String,
    val serverCountry: String
) {
    val downloadSpeedFormatted: String
        get() = when {
            downloadSpeed < 1024 -> "$downloadSpeed B/s"
            downloadSpeed < 1024 * 1024 -> String.format("%.1f KB/s", downloadSpeed / 1024.0)
            downloadSpeed < 1024 * 1024 * 1024 -> String.format("%.1f MB/s", downloadSpeed / (1024.0 * 1024))
            else -> String.format("%.2f GB/s", downloadSpeed / (1024.0 * 1024 * 1024))
        }
    
    val uploadSpeedFormatted: String
        get() = when {
            uploadSpeed < 1024 -> "$uploadSpeed B/s"
            uploadSpeed < 1024 * 1024 -> String.format("%.1f KB/s", uploadSpeed / 1024.0)
            uploadSpeed < 1024 * 1024 * 1024 -> String.format("%.1f MB/s", uploadSpeed / (1024.0 * 1024))
            else -> String.format("%.2f GB/s", uploadSpeed / (1024.0 * 1024 * 1024))
        }
    
    val quality: ConnectionQuality
        get() = when {
            latency < 50 && downloadSpeed > 50_000_000 -> ConnectionQuality.EXCELLENT
            latency < 100 && downloadSpeed > 20_000_000 -> ConnectionQuality.GOOD
            latency < 200 && downloadSpeed > 5_000_000 -> ConnectionQuality.FAIR
            latency < 500 && downloadSpeed > 1_000_000 -> ConnectionQuality.POOR
            else -> ConnectionQuality.VERY_POOR
        }
    
    val qualityEmoji: String
        get() = when (quality) {
            ConnectionQuality.EXCELLENT -> "🚀"
            ConnectionQuality.GOOD -> "✨"
            ConnectionQuality.FAIR -> "👍"
            ConnectionQuality.POOR -> "🐢"
            ConnectionQuality.VERY_POOR -> "💀"
        }
}

enum class ConnectionQuality {
    EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
}

data class SpeedTestProgress(
    val phase: TestPhase,
    val progress: Float,      // 0.0 - 1.0
    val currentSpeed: Long,   // bytes per second
    val bytesTransferred: Long
)

enum class TestPhase {
    IDLE,
    LATENCY_TEST,
    DOWNLOAD_TEST,
    UPLOAD_TEST,
    COMPLETED,
    ERROR
}

object SpeedTest {
    private const val TAG = "SpeedTest"
    
    private val isRunning = AtomicBoolean(false)
    private var progressCallback: ((SpeedTestProgress) -> Unit)? = null
    
    // Test configuration
    private const val DOWNLOAD_TEST_SIZE = 5_000_000L  // 5 MB
    private const val UPLOAD_TEST_SIZE = 2_000_000L    // 2 MB
    private const val LATENCY_TEST_COUNT = 5
    private const val CONNECTION_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000
    
    fun isRunning(): Boolean = isRunning.get()
    
    fun setProgressCallback(callback: (SpeedTestProgress) -> Unit) {
        progressCallback = callback
    }
    
    fun clearProgressCallback() {
        progressCallback = null
    }
    
    fun stopTest() {
        isRunning.set(false)
    }
    
    suspend fun runTest(
        proxyHost: String = "127.0.0.1",
        proxyPort: Int = 10809
    ): Result<SpeedTestResult> = withContext(Dispatchers.IO) {
        if (!isRunning.compareAndSet(false, true)) {
            return@withContext Result.failure(IllegalStateException("Speed test already running"))
        }
        
        val startTime = System.currentTimeMillis()
        
        try {
            LogManager.i(TAG, "Starting speed test through proxy $proxyHost:$proxyPort")
            
            // Phase 1: Latency test
            updateProgress(TestPhase.LATENCY_TEST, 0f, 0, 0)
            val latencyResult = runLatencyTest(proxyHost, proxyPort)
            val (latency, jitter, packetLoss) = latencyResult
            
            LogManager.i(TAG, "Latency: ${latency}ms, Jitter: ${jitter}ms, Packet Loss: ${packetLoss}%")
            
            // Phase 2: Download test
            updateProgress(TestPhase.DOWNLOAD_TEST, 0f, 0, 0)
            val downloadResult = runDownloadTest(proxyHost, proxyPort)
            val (downloadSpeed, downloadBytes) = downloadResult
            
            LogManager.i(TAG, "Download: ${formatSpeed(downloadSpeed)}")
            
            // Phase 3: Upload test
            updateProgress(TestPhase.UPLOAD_TEST, 0f, 0, 0)
            val uploadResult = runUploadTest(proxyHost, proxyPort)
            val (uploadSpeed, uploadBytes) = uploadResult
            
            LogManager.i(TAG, "Upload: ${formatSpeed(uploadSpeed)}")
            
            val testDuration = System.currentTimeMillis() - startTime
            
            val result = SpeedTestResult(
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                latency = latency,
                jitter = jitter,
                packetLoss = packetLoss,
                downloadBytes = downloadBytes,
                uploadBytes = uploadBytes,
                testDuration = testDuration,
                serverName = "VPN Server",
                serverCountry = "VPN"
            )
            
            updateProgress(TestPhase.COMPLETED, 1f, 0, 0)
            LogManager.i(TAG, "Speed test completed: ↓${result.downloadSpeedFormatted} ↑${result.uploadSpeedFormatted}")
            
            Result.success(result)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Speed test failed", e)
            updateProgress(TestPhase.ERROR, 0f, 0, 0)
            Result.failure(e)
        } finally {
            isRunning.set(false)
        }
    }
    
    private suspend fun runLatencyTest(
        proxyHost: String,
        proxyPort: Int
    ): Triple<Long, Long, Float> = withContext(Dispatchers.IO) {
        val latencies = mutableListOf<Long>()
        var failedPings = 0
        
        for (i in 0 until LATENCY_TEST_COUNT) {
            if (!isRunning.get()) break
            
            try {
                val startTime = System.currentTimeMillis()
                
                val proxy = java.net.Proxy(
                    java.net.Proxy.Type.HTTP,
                    java.net.InetSocketAddress(proxyHost, proxyPort)
                )
                
                val url = URL("http://www.google.com/generate_204")
                val connection = url.openConnection(proxy) as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "GET"
                
                val responseCode = connection.responseCode
                val latency = System.currentTimeMillis() - startTime
                connection.disconnect()
                
                if (responseCode == 204 || responseCode == 200) {
                    latencies.add(latency)
                } else {
                    failedPings++
                }
                
                updateProgress(TestPhase.LATENCY_TEST, (i + 1) / LATENCY_TEST_COUNT.toFloat(), 0, 0)
                
            } catch (e: Exception) {
                LogManager.w(TAG, "Latency test ping $i failed: ${e.message}")
                failedPings++
            }
        }
        
        val avgLatency = if (latencies.isNotEmpty()) latencies.average().toLong() else 999L
        val jitter = if (latencies.size > 1) {
            latencies.zipWithNext().map { (a, b) -> kotlin.math.abs(a - b) }.average().toLong()
        } else 0L
        val packetLoss = (failedPings.toFloat() / LATENCY_TEST_COUNT) * 100
        
        Triple(avgLatency, jitter, packetLoss)
    }
    
    private suspend fun runDownloadTest(
        proxyHost: String,
        proxyPort: Int
    ): Pair<Long, Long> = withContext(Dispatchers.IO) {
        try {
            val proxy = java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress(proxyHost, proxyPort)
            )
            
            // Use a reliable test file
            val testUrl = "http://speedtest.tele2.net/1MB.zip"
            val url = URL(testUrl)
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            
            val startTime = System.currentTimeMillis()
            val inputStream = connection.inputStream
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytes = 0L
            var lastUpdateTime = startTime
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1 && totalBytes < DOWNLOAD_TEST_SIZE && isRunning.get()) {
                totalBytes += bytesRead
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 100) {
                    val elapsed = currentTime - startTime
                    if (elapsed > 0) {
                        val speed = (totalBytes * 1000 / elapsed)
                        updateProgress(TestPhase.DOWNLOAD_TEST, totalBytes.toFloat() / DOWNLOAD_TEST_SIZE, speed, totalBytes)
                    }
                    lastUpdateTime = currentTime
                }
            }
            
            inputStream.close()
            connection.disconnect()
            
            val elapsed = System.currentTimeMillis() - startTime
            val speed = if (elapsed > 0 && totalBytes > 0) (totalBytes * 1000 / elapsed) else 0L
            
            LogManager.i(TAG, "Download test: ${formatSpeed(speed)}, bytes: $totalBytes")
            Pair(speed, totalBytes)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Download test failed", e)
            Pair(0L, 0L)
        }
    }
    
    private suspend fun runUploadTest(
        proxyHost: String,
        proxyPort: Int
    ): Pair<Long, Long> = withContext(Dispatchers.IO) {
        try {
            val proxy = java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                java.net.InetSocketAddress(proxyHost, proxyPort)
            )
            
            // Use httpbin for upload test
            val url = URL("http://httpbin.org/post")
            val connection = url.openConnection(proxy) as HttpURLConnection
            connection.doOutput = true
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECTION_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            
            val startTime = System.currentTimeMillis()
            val outputStream = connection.outputStream
            
            // Generate data
            val chunkSize = 65536
            val buffer = ByteArray(chunkSize) { 0x41 }
            var uploadedBytes = 0L
            var lastUpdateTime = startTime
            
            while (uploadedBytes < UPLOAD_TEST_SIZE && isRunning.get()) {
                val toWrite = minOf(chunkSize.toLong(), UPLOAD_TEST_SIZE - uploadedBytes).toInt()
                outputStream.write(buffer, 0, toWrite)
                outputStream.flush()
                uploadedBytes += toWrite
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 100) {
                    val elapsed = currentTime - startTime
                    if (elapsed > 0) {
                        val speed = (uploadedBytes * 1000 / elapsed)
                        updateProgress(TestPhase.UPLOAD_TEST, uploadedBytes.toFloat() / UPLOAD_TEST_SIZE, speed, uploadedBytes)
                    }
                    lastUpdateTime = currentTime
                }
            }
            
            outputStream.close()
            
            // Read response
            try {
                connection.inputStream.close()
            } catch (e: Exception) { }
            
            connection.disconnect()
            
            val elapsed = System.currentTimeMillis() - startTime
            val speed = if (elapsed > 0) (uploadedBytes * 1000 / elapsed) else 0L
            
            LogManager.i(TAG, "Upload test: ${formatSpeed(speed)}")
            Pair(speed, uploadedBytes)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "Upload test failed", e)
            Pair(0L, 0L)
        }
    }
    
    private fun updateProgress(phase: TestPhase, progress: Float, currentSpeed: Long, bytesTransferred: Long) {
        progressCallback?.invoke(SpeedTestProgress(phase, progress, currentSpeed, bytesTransferred))
    }
    
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024))
        }
    }
}
