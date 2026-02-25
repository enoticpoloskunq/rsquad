package com.raccoonsquad.core.util

import android.net.TrafficStats as AndroidTrafficStats
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.xray.XrayWrapper
import com.raccoonsquad.data.model.VlessConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Node testing utilities - TCP ping, URL check through VPN, full connectivity test
 */
object NodeTester {
    
    private const val TAG = "NodeTester"
    
    enum class TestMethod {
        TCP,        // TCP connect + measure time (fast, but only checks if port is open)
        URL,        // Full VPN connection + HTTP request (slow, but real test)
        FULL        // URL + IP check (slowest, most comprehensive)
    }
    
    data class TestResult(
        val success: Boolean,
        val latencyMs: Long = -1,
        val error: String? = null,
        val ipAddress: String? = null,      // Detected exit IP for FULL test
        val country: String? = null,        // Detected country
        val isWorking: Boolean = false      // True if actually works for VPN
    )
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    
    // Test URLs
    private const val TEST_URL_204 = "https://www.google.com/generate_204"
    private const val TEST_URL_IP = "https://api.ipify.org?format=text"
    private const val TEST_URL_IPINFO = "http://ip-api.com/json/"
    
    /**
     * Test node with specified method
     */
    suspend fun testNode(
        serverAddress: String,
        port: Int,
        method: TestMethod = TestMethod.TCP,
        config: VlessConfig? = null,
        vpnService: VpnService? = null
    ): TestResult = withContext(Dispatchers.IO) {
        when (method) {
            TestMethod.TCP -> testTcpConnect(serverAddress, port)
            TestMethod.URL -> {
                if (config != null && vpnService != null) {
                    testUrlThroughVpn(config, vpnService)
                } else {
                    TestResult(success = false, error = "Need config and VpnService for URL test")
                }
            }
            TestMethod.FULL -> {
                if (config != null && vpnService != null) {
                    testFullConnectivity(config, vpnService)
                } else {
                    TestResult(success = false, error = "Need config and VpnService for FULL test")
                }
            }
        }
    }
    
    /**
     * TCP ping - measure time to establish TCP connection
     * Fast but only checks if port is reachable
     */
    private fun testTcpConnect(serverAddress: String, port: Int): TestResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(serverAddress, port), 5000)
            }
            
            val latency = System.currentTimeMillis() - startTime
            LogManager.d(TAG, "TCP test $serverAddress:$port = ${latency}ms ✓")
            
            TestResult(success = true, latencyMs = latency, isWorking = false)
        } catch (e: Exception) {
            LogManager.w(TAG, "TCP test $serverAddress:$port FAILED: ${e.message}")
            TestResult(success = false, error = e.message)
        }
    }
    
    /**
     * Test through active VPN connection (via HTTP proxy)
     * Uses the existing VPN tunnel - no need to start new connection
     */
    fun testThroughActiveProxy(): TestResult {
        return try {
            val startTime = System.currentTimeMillis()

            // Use HTTP proxy provided by Xray (127.0.0.1:10809) - more reliable than SOCKS5
            val proxy = java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                InetSocketAddress("127.0.0.1", 10809)
            )

            val client = OkHttpClient.Builder()
                .proxy(proxy)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("http://www.google.com/generate_204")  // HTTP works better through proxy
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code == 204) {
                    LogManager.d(TAG, "URL test through active VPN: ✓ ${latency}ms")
                    TestResult(success = true, latencyMs = latency, isWorking = true)
                } else {
                    TestResult(success = false, error = "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "URL test through active VPN failed: ${e.message}")
            TestResult(success = false, error = e.message)
        }
    }
    
    /**
     * Test node URL without active VPN - creates temporary connection
     * REAL test using libv2ray MeasureOutboundDelay - starts Xray temporarily
     */
    suspend fun testNodeUrl(config: VlessConfig): TestResult = withContext(Dispatchers.IO) {
        // Validate config first
        if (config.serverAddress.isBlank()) {
            LogManager.w(TAG, "URL test ${config.name}: invalid server address")
            return@withContext TestResult(success = false, error = "Invalid server address")
        }
        if (config.port <= 0 || config.port > 65535) {
            LogManager.w(TAG, "URL test ${config.name}: invalid port ${config.port}")
            return@withContext TestResult(success = false, error = "Invalid port: ${config.port}")
        }
        
        return@withContext try {
            val startTime = System.currentTimeMillis()
            
            // REAL TEST: Use XrayWrapper.testConfig() which calls libv2ray MeasureOutboundDelay
            // This actually starts Xray and makes an HTTP request through the tunnel
            val delay = XrayWrapper.testConfig(config)
            
            val elapsed = System.currentTimeMillis() - startTime
            
            if (delay > 0) {
                LogManager.d(TAG, "URL test ${config.name}: ✓ ${delay}ms (total: ${elapsed}ms)")
                TestResult(success = true, latencyMs = delay, isWorking = true)
            } else {
                LogManager.w(TAG, "URL test ${config.name} FAILED: Xray returned -1")
                TestResult(success = false, error = "Connection failed")
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: e.javaClass.simpleName
            LogManager.w(TAG, "URL test ${config.name} FAILED: $errorMsg")
            TestResult(success = false, error = errorMsg)
        }
    }
    
    /**
     * Test real VPN connectivity - connect through VPN and make HTTP request
     */
    private fun testUrlThroughVpn(config: VlessConfig, vpnService: VpnService): TestResult {
        var vpnInterface: ParcelFileDescriptor? = null
        val isTesting = AtomicBoolean(true)
        
        return try {
            LogManager.i(TAG, "=== URL Test for ${config.name} ===")
            
            // Initialize Xray if needed
            try {
                XrayWrapper.init(vpnService.applicationContext)
            } catch (e: Exception) {
                LogManager.w(TAG, "Xray already initialized")
            }
            
            // Build VPN interface
            val mtu = config.mtu.toIntOrNull() ?: 1500
            val builder = vpnService.Builder()
                .setSession("RaccoonTest")
                .setMtu(mtu)
                .addAddress("10.66.66.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
            
            try {
                builder.addDisallowedApplication(vpnService.packageName)
            } catch (e: Exception) {
                LogManager.w(TAG, "Could not exclude self from VPN")
            }
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                return TestResult(success = false, error = "VPN interface is null")
            }
            
            LogManager.d(TAG, "VPN interface established fd=${vpnInterface!!.fd}")
            
            // Generate Xray config
            val xrayConfig = XrayWrapper.generateConfig(config)
            
            // Start Xray
            val startTime = System.currentTimeMillis()
            val started = XrayWrapper.start(xrayConfig, vpnInterface!!.fd)
            
            if (!started) {
                LogManager.e(TAG, "Xray failed to start")
                return TestResult(success = false, error = "Xray failed to start")
            }
            
            LogManager.d(TAG, "Xray started, waiting for connection...")
            
            // Wait for connection to establish
            Thread.sleep(1500)
            
            // Make HTTP request through VPN
            val testResult = testHttpConnection()
            val latency = System.currentTimeMillis() - startTime
            
            LogManager.i(TAG, "URL Test result: ${if (testResult.success) "✓ ${latency}ms" else "✗ ${testResult.error}"}")
            
            testResult.copy(latencyMs = latency)
            
        } catch (e: Exception) {
            LogManager.e(TAG, "URL Test crashed: ${e.message}")
            TestResult(success = false, error = e.message)
        } finally {
            // Cleanup
            try {
                XrayWrapper.stop()
            } catch (e: Exception) {
                LogManager.w(TAG, "Error stopping Xray: ${e.message}")
            }
            
            try {
                vpnInterface?.close()
            } catch (e: Exception) {
                LogManager.w(TAG, "Error closing VPN: ${e.message}")
            }
            
            // Small delay before next test
            Thread.sleep(500)
        }
    }
    
    /**
     * Full connectivity test - URL + IP check + geolocation
     */
    private fun testFullConnectivity(config: VlessConfig, vpnService: VpnService): TestResult {
        var vpnInterface: ParcelFileDescriptor? = null
        
        return try {
            LogManager.i(TAG, "=== FULL Test for ${config.name} ===")
            
            // Initialize Xray if needed
            try {
                XrayWrapper.init(vpnService.applicationContext)
            } catch (e: Exception) {
                LogManager.w(TAG, "Xray already initialized")
            }
            
            // Build VPN interface
            val mtu = config.mtu.toIntOrNull() ?: 1500
            val builder = vpnService.Builder()
                .setSession("RaccoonTest")
                .setMtu(mtu)
                .addAddress("10.66.66.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .addDnsServer("1.1.1.1")
            
            try {
                builder.addDisallowedApplication(vpnService.packageName)
            } catch (e: Exception) {
                LogManager.w(TAG, "Could not exclude self from VPN")
            }
            
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                return TestResult(success = false, error = "VPN interface is null")
            }
            
            LogManager.d(TAG, "VPN interface established fd=${vpnInterface!!.fd}")
            
            // Generate and start Xray
            val xrayConfig = XrayWrapper.generateConfig(config)
            val startTime = System.currentTimeMillis()
            val started = XrayWrapper.start(xrayConfig, vpnInterface!!.fd)
            
            if (!started) {
                LogManager.e(TAG, "Xray failed to start")
                return TestResult(success = false, error = "Xray failed to start")
            }
            
            LogManager.d(TAG, "Xray started, waiting for connection...")
            Thread.sleep(2000)
            
            // Test 1: Basic connectivity (generate_204)
            val connectivityTest = testHttpConnection()
            if (!connectivityTest.success) {
                return connectivityTest.copy(latencyMs = System.currentTimeMillis() - startTime)
            }
            
            // Test 2: Get exit IP
            val exitIp = getExitIp()
            LogManager.i(TAG, "Exit IP: $exitIp")
            
            // Test 3: Get geolocation
            val geoInfo = getGeoInfo(exitIp)
            LogManager.i(TAG, "Geo: $geoInfo")
            
            val latency = System.currentTimeMillis() - startTime
            LogManager.i(TAG, "FULL Test ✓ ${latency}ms - IP: $exitIp, Country: ${geoInfo.second}")
            
            TestResult(
                success = true,
                latencyMs = latency,
                ipAddress = exitIp,
                country = geoInfo.second,
                isWorking = true
            )
            
        } catch (e: Exception) {
            LogManager.e(TAG, "FULL Test crashed: ${e.message}")
            TestResult(success = false, error = e.message)
        } finally {
            try { XrayWrapper.stop() } catch (e: Exception) { }
            try { vpnInterface?.close() } catch (e: Exception) { }
            Thread.sleep(500)
        }
    }
    
    /**
     * Test HTTP connectivity through VPN
     */
    private fun testHttpConnection(): TestResult {
        return try {
            val request = Request.Builder()
                .url(TEST_URL_204)
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 204) {
                    TestResult(success = true, isWorking = true)
                } else {
                    TestResult(success = false, error = "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "HTTP test failed: ${e.message}")
            TestResult(success = false, error = e.message)
        }
    }
    
    /**
     * Get exit IP address
     */
    private fun getExitIp(): String? {
        return try {
            val request = Request.Builder()
                .url(TEST_URL_IP)
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.trim()
                } else null
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to get exit IP: ${e.message}")
            null
        }
    }
    
    /**
     * Get geolocation info
     */
    private fun getGeoInfo(ip: String?): Pair<String?, String?> {
        return try {
            val url = if (ip != null) "$TEST_URL_IPINFO$ip" else TEST_URL_IPINFO
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string() ?: return Pair(null, null)
                    val obj = org.json.JSONObject(json)
                    Pair(
                        obj.optString("query"),
                        obj.optString("country")
                    )
                } else Pair(null, null)
            }
        } catch (e: Exception) {
            LogManager.w(TAG, "Failed to get geo info: ${e.message}")
            Pair(null, null)
        }
    }
    
    /**
     * Fetch subscription from URL
     */
    suspend fun fetchSubscription(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "Fetching subscription from: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty response"))
                
                LogManager.i(TAG, "Fetched ${body.length} bytes")
                Result.success(body)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to fetch subscription: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Test multiple nodes and return failed indices
     */
    suspend fun testNodesAndGetFailed(
        nodes: List<Triple<String, Int, TestMethod>>,
        timeout: Long = 5000
    ): List<Int> = withContext(Dispatchers.IO) {
        nodes.mapIndexedNotNull { index, (address, port, method) ->
            val result = testNode(address, port, method)
            if (!result.success) {
                LogManager.w(TAG, "Node $index ($address:$port) failed: ${result.error}")
                index
            } else {
                null
            }
        }
    }
}
