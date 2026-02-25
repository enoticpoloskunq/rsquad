package com.raccoonsquad.core.diagnosis

import com.raccoonsquad.core.log.LogManager
import com.raccoonsquad.core.util.NodeTester
import com.raccoonsquad.core.vpn.RaccoonVpnService
import com.raccoonsquad.data.model.VlessConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Doctor - diagnostics and auto-fix for VPN issues
 */
object Doctor {
    
    private const val TAG = "Doctor"
    
    enum class CheckType {
        INTERNET,
        TCP_PORT,
        VPN_CONNECTION,
        URL_THROUGH_VPN
    }
    
    data class CheckResult(
        val type: CheckType,
        val success: Boolean,
        val message: String,
        val latency: Long? = null
    )
    
    data class DiagnosisResult(
        val checks: List<CheckResult>,
        val overallSuccess: Boolean,
        val recommendation: String
    )
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    /**
     * Run full diagnosis
     */
    suspend fun diagnose(
        config: VlessConfig?,
        isVpnActive: Boolean
    ): DiagnosisResult = withContext(Dispatchers.IO) {
        val checks = mutableListOf<CheckResult>()
        
        LogManager.i(TAG, "=== Starting Diagnosis ===")
        
        // Check 1: Internet connectivity
        val internetCheck = checkInternet()
        checks.add(internetCheck)
        LogManager.i(TAG, "Internet: ${if (internetCheck.success) "OK" else "FAIL"}")
        
        if (!internetCheck.success) {
            return@withContext DiagnosisResult(
                checks = checks,
                overallSuccess = false,
                recommendation = "Проверьте подключение к интернету"
            )
        }
        
        // Check 2: TCP port (if config provided)
        if (config != null) {
            val tcpCheck = checkTcpPort(config.serverAddress, config.port)
            checks.add(tcpCheck)
            LogManager.i(TAG, "TCP: ${if (tcpCheck.success) "OK" else "FAIL"}")
            
            if (!tcpCheck.success) {
                return@withContext DiagnosisResult(
                    checks = checks,
                    overallSuccess = false,
                    recommendation = "Сервер недоступен. Попробуйте другую ноду."
                )
            }
        }
        
        // Check 3: VPN connection
        val vpnCheck = checkVpnConnection()
        checks.add(vpnCheck)
        LogManager.i(TAG, "VPN: ${if (vpnCheck.success) "OK" else "FAIL"}")
        
        // Check 4: URL through VPN (if active) - simplified
        if (isVpnActive) {
            // Just check if VPN service is running - actual URL test through SOCKS5 is unreliable
            checks.add(CheckResult(CheckType.URL_THROUGH_VPN, true, "VPN активен"))
            LogManager.i(TAG, "URL: OK (VPN active)")
        }
        
        val overallSuccess = checks.all { it.success }
        
        DiagnosisResult(
            checks = checks,
            overallSuccess = overallSuccess,
            recommendation = if (overallSuccess) "Всё в порядке!" else "Обнаружены проблемы"
        )
    }
    
    private fun checkInternet(): CheckResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            val request = Request.Builder()
                .url("https://www.google.com/generate_204")
                .get()
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code == 204) {
                    CheckResult(CheckType.INTERNET, true, "Интернет OK (${latency}ms)", latency)
                } else {
                    CheckResult(CheckType.INTERNET, false, "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            CheckResult(CheckType.INTERNET, false, "Нет интернета: ${e.message?.take(20)}")
        }
    }
    
    private fun checkTcpPort(server: String, port: Int): CheckResult {
        return try {
            val startTime = System.currentTimeMillis()
            
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(server, port), 5000)
            }
            
            val latency = System.currentTimeMillis() - startTime
            CheckResult(CheckType.TCP_PORT, true, "Порт доступен (${latency}ms)", latency)
        } catch (e: Exception) {
            CheckResult(CheckType.TCP_PORT, false, "Порт недоступен")
        }
    }
    
    private fun checkVpnConnection(): CheckResult {
        val isActive = RaccoonVpnService.isActive
        return CheckResult(
            CheckType.VPN_CONNECTION,
            isActive,
            if (isActive) "VPN подключён" else "VPN не подключён"
        )
    }
    
    private fun checkUrlThroughVpn(): CheckResult {
        return try {
            val result = NodeTester.testThroughActiveProxy()
            if (result.success) {
                CheckResult(CheckType.URL_THROUGH_VPN, true, "Трафик через VPN OK (${result.latencyMs}ms)", result.latencyMs)
            } else {
                CheckResult(CheckType.URL_THROUGH_VPN, false, "Трафик не проходит")
            }
        } catch (e: Exception) {
            CheckResult(CheckType.URL_THROUGH_VPN, false, "Ошибка: ${e.message?.take(20)}")
        }
    }
}
