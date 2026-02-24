package com.raccoonsquad.core.util

import android.util.Log
import com.raccoonsquad.core.log.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Node testing utilities - TCP ping and HTTP GET tests
 */
object NodeTester {
    
    private const val TAG = "NodeTester"
    
    enum class TestMethod {
        TCP,    // TCP connect + measure time
        HTTP    // HTTP GET request
    }
    
    data class TestResult(
        val success: Boolean,
        val latencyMs: Long = -1,
        val error: String? = null
    )
    
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    
    /**
     * Test node connectivity
     */
    suspend fun testNode(
        serverAddress: String,
        port: Int,
        method: TestMethod = TestMethod.TCP,
        testUrl: String = "https://www.google.com/generate_204"
    ): TestResult = withContext(Dispatchers.IO) {
        when (method) {
            TestMethod.TCP -> testTcpConnect(serverAddress, port)
            TestMethod.HTTP -> testHttpGet(testUrl)
        }
    }
    
    /**
     * TCP ping - measure time to establish TCP connection
     */
    private fun testTcpConnect(serverAddress: String, port: Int): TestResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(serverAddress, port), 5000)
            }
            
            val latency = System.currentTimeMillis() - startTime
            LogManager.d(TAG, "TCP test $serverAddress:$port = ${latency}ms")
            
            TestResult(success = true, latencyMs = latency)
        } catch (e: Exception) {
            LogManager.e(TAG, "TCP test failed: ${e.message}")
            TestResult(success = false, error = e.message)
        }
    }
    
    /**
     * HTTP GET test - measure time to complete HTTP request
     */
    private fun testHttpGet(testUrl: String): TestResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            val request = Request.Builder()
                .url(testUrl)
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                val success = response.isSuccessful || response.code == 204
                
                LogManager.d(TAG, "HTTP test $testUrl = ${latency}ms, code=${response.code}")
                
                TestResult(success = success, latencyMs = latency)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "HTTP test failed: ${e.message}")
            TestResult(success = false, error = e.message)
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
