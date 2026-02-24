package com.raccoonsquad.core.xray

import android.content.Context
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.SecurityMode
import com.raccoonsquad.data.model.FlowMode
import com.raccoonsquad.core.log.LogManager
import go.Seq
import libv2ray.Libv2ray
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wrapper for Xray-core via libv2ray.aar
 * API: Libv2ray.newCoreController() -> CoreController.startLoop()/stopLoop()
 */
object XrayWrapper {
    
    private const val TAG = "Xray"
    
    private var coreController: CoreController? = null
    private var isRunning = false
    private var currentConfig: String? = null
    
    /**
     * Initialize Xray wrapper with application context
     */
    fun init(context: Context) {
        LogManager.i(TAG, "=== XrayWrapper.init() START ===")
        LogManager.flush()
        
        try {
            // IMPORTANT: Set context for Go mobile bindings
            Seq.setContext(context.applicationContext)
            LogManager.d(TAG, "Seq.setContext done")
            
            val filesDir = context.filesDir.absolutePath
            LogManager.d(TAG, "filesDir: $filesDir")
            
            // Create xray directory for assets
            val xrayDir = File(filesDir, "xray")
            if (!xrayDir.exists()) {
                val created = xrayDir.mkdirs()
                LogManager.d(TAG, "xrayDir created: $created")
            }
            
            // Copy geoip.dat and geosite.dat from assets to files dir
            copyAssetsIfNeeded(context, xrayDir)
            
            LogManager.i(TAG, "Calling Libv2ray.initCoreEnv...")
            LogManager.flush()
            
            // API: initCoreEnv(assetsPath, deviceId)
            // deviceId is used for XUDP base key - can be empty or unique
            Libv2ray.initCoreEnv(xrayDir.absolutePath, "")
            
            LogManager.i(TAG, "Libv2ray.initCoreEnv DONE")
            
            val version = Libv2ray.checkVersionX()
            LogManager.i(TAG, "Xray version: $version")
            LogManager.i(TAG, "=== XrayWrapper.init() SUCCESS ===")
            LogManager.flush()
            
        } catch (e: Throwable) {
            LogManager.e(TAG, "=== XrayWrapper.init() FAILED ===", e)
            LogManager.flush()
            throw e
        }
    }
    
    /**
     * Copy geoip.dat and geosite.dat from assets to files directory
     */
    private fun copyAssetsIfNeeded(context: Context, destDir: File) {
        try {
            val assets = listOf("geoip.dat", "geosite.dat")
            
            for (asset in assets) {
                val destFile = File(destDir, asset)
                
                // Only copy if not exists
                if (!destFile.exists()) {
                    LogManager.d(TAG, "Copying $asset from assets...")
                    
                    context.assets.open(asset).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    LogManager.d(TAG, "$asset copied (${destFile.length()} bytes)")
                } else {
                    LogManager.d(TAG, "$asset already exists (${destFile.length()} bytes)")
                }
            }
        } catch (e: Throwable) {
            LogManager.e(TAG, "Failed to copy assets", e)
        }
    }
    
    /**
     * Generate Xray config JSON from VlessConfig
     */
    fun generateConfig(config: VlessConfig): String {
        LogManager.d(TAG, "generateConfig() for ${config.name}")
        
        val json = JSONObject()
        
        // Log settings - use debug to see what's happening
        json.put("log", JSONObject().apply {
            put("loglevel", "debug")
        })
        
        // DNS settings - use simple DNS, not through proxy (to avoid circular dependency)
        json.put("dns", JSONObject().apply {
            put("tag", "dns-out")
            put("servers", JSONArray().apply {
                // Simple DNS servers - direct, not through proxy
                // This avoids circular dependency (need DNS to connect to proxy)
                put("8.8.8.8")
                put("1.1.1.1")
                // Fallback local
                put("localhost")
            })
            put("queryStrategy", "UseIPv4")
        })
        
        // Inbounds - SOCKS5 and HTTP proxy
        val inbounds = JSONArray()
        
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("udp", true)
                put("auth", "noauth")
            })
        })
        
        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("port", 10809)
            put("listen", "127.0.0.1")
            put("protocol", "http")
        })
        
        json.put("inbounds", inbounds)
        
        // Outbounds
        val outbounds = JSONArray()
        
        outbounds.put(JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", config.serverAddress)
                    put("port", config.port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", config.uuid)
                        put("encryption", "none")
                        if (config.flow != FlowMode.NONE) {
                            put("flow", when(config.flow) {
                                FlowMode.XTLS_RPRX_VISION -> "xtls-rprx-vision"
                                FlowMode.XTLS_RPRX_VISION_UDP443 -> "xtls-rprx-vision-udp443"
                                else -> ""
                            })
                        }
                    }))
                }))
            })
            put("streamSettings", JSONObject().apply {
                put("network", "tcp")
                put("security", when(config.securityMode) {
                    SecurityMode.REALITY -> "reality"
                    SecurityMode.TLS -> "tls"
                    SecurityMode.NONE -> "none"
                })
                
                if (config.securityMode == SecurityMode.REALITY) {
                    put("realitySettings", JSONObject().apply {
                        put("show", false)
                        put("fingerprint", config.fingerprint.ifEmpty { "chrome" })
                        if (config.sni.isNotEmpty()) put("serverName", config.sni)
                        if (config.realityPublicKey.isNotEmpty()) put("publicKey", config.realityPublicKey)
                        if (config.realityShortId.isNotEmpty()) put("shortId", config.realityShortId)
                        if (config.realitySpiderX.isNotEmpty()) put("spiderX", config.realitySpiderX)
                    })
                } else if (config.securityMode == SecurityMode.TLS) {
                    put("tlsSettings", JSONObject().apply {
                        put("allowInsecure", false)
                        if (config.sni.isNotEmpty()) put("serverName", config.sni)
                        put("fingerprint", config.fingerprint.ifEmpty { "chrome" })
                    })
                }
                
                // Socket options (fragment, etc)
                put("sockopt", JSONObject().apply {
                    if (config.tcpFastOpen) put("tcpFastOpen", true)
                    if (config.tcpNoDelay) put("tcpNoDelay", true)
                    if (config.tcpKeepAliveInterval > 0) put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
                    
                    // FRAGMENTATION - the key feature!
                    if (config.fragmentationEnabled) {
                        put("dialer", JSONObject().apply {
                            put("domainStrategy", "AsIs")
                            put("fragment", JSONObject().apply {
                                put("packets", config.fragmentPackets)
                                put("length", config.fragmentLength)
                                put("interval", config.fragmentInterval)
                            })
                        })
                    }
                })
            })
        })
        
        // DNS outbound - for DNS queries through proxy
        outbounds.put(JSONObject().apply {
            put("tag", "dns-out")
            put("protocol", "dns")
        })
        
        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        })
        
        // Block outbound
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        })
        
        json.put("outbounds", outbounds)
        
        // Routing - simple and correct
        json.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // Block ads (optional)
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().put("geosite:category-ads-all"))
                    put("outboundTag", "block")
                })
                // Private IPs -> direct (for local network access)
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
            })
        })
        
        // Log the generated config for debugging
        val configStr = json.toString(2)
        LogManager.d(TAG, "Generated Xray config:\n${configStr.take(500)}...")
        
        return configStr
    }
    
    /**
     * Start Xray with given config
     * @param configJson Xray config JSON
     * @param tunFd TUN file descriptor (0 for proxy-only mode)
     */
    fun start(configJson: String, tunFd: Int = 0): Boolean {
        LogManager.i(TAG, "=== XrayWrapper.start() ===")
        LogManager.d(TAG, "Config length: ${configJson.length}, tunFd: $tunFd")
        LogManager.flush()
        
        if (isRunning) {
            LogManager.w(TAG, "Already running, stopping first")
            stop()
        }
        
        try {
            currentConfig = configJson
            
            LogManager.d(TAG, "Creating CoreController...")
            LogManager.flush()
            
            coreController = Libv2ray.newCoreController(CoreCallback)
            
            if (coreController == null) {
                LogManager.e(TAG, "CoreController is NULL!")
                LogManager.flush()
                return false
            }
            
            LogManager.d(TAG, "CoreController created OK")
            LogManager.i(TAG, "Starting Xray loop...")
            LogManager.flush()
            
            // startLoop(configJson, tunFd) - tunFd = 0 for proxy mode
            coreController?.startLoop(configJson, tunFd)
            
            isRunning = true
            LogManager.i(TAG, "=== XrayWrapper.start() SUCCESS ===")
            LogManager.flush()
            return true
            
        } catch (e: Throwable) {
            LogManager.e(TAG, "=== XrayWrapper.start() FAILED ===", e)
            LogManager.flush()
            return false
        }
    }
    
    fun stop() {
        if (!isRunning && coreController == null) return
        
        LogManager.i(TAG, "XrayWrapper.stop()")
        
        try {
            coreController?.stopLoop()
        } catch (e: Throwable) {
            LogManager.e(TAG, "Error stopping", e)
        }
        
        coreController = null
        isRunning = false
        currentConfig = null
        
        LogManager.i(TAG, "Xray stopped")
        LogManager.flush()
    }
    
    fun isRunning(): Boolean = isRunning
}

/**
 * Core callback handler implementation for Xray core events
 */
private object CoreCallback : CoreCallbackHandler() {
    private const val TAG = "XrayCallback"
    
    override fun startup(): Long {
        LogManager.i(TAG, "Xray core STARTED")
        LogManager.flush()
        return 0
    }
    
    override fun shutdown(): Long {
        LogManager.i(TAG, "Xray core SHUTDOWN")
        LogManager.flush()
        return 0
    }
    
    override fun onEmitStatus(code: Long, message: String?): Long {
        // Log all Xray core messages
        when {
            message.isNullOrEmpty() -> LogManager.d(TAG, "Xray status: $code")
            message.contains("error", ignoreCase = true) -> LogManager.e(TAG, "Xray: $message")
            message.contains("warn", ignoreCase = true) -> LogManager.w(TAG, "Xray: $message")
            else -> LogManager.d(TAG, "Xray: $message")
        }
        LogManager.flush()
        return 0
    }
    
    override fun onLog(level: Long, log: String?): Long {
        log?.let {
            when (level.toInt()) {
                0 -> LogManager.d(TAG, it)  // debug
                1 -> LogManager.i(TAG, it)  // info
                2 -> LogManager.w(TAG, it)  // warning
                3 -> LogManager.e(TAG, it)  // error
                else -> LogManager.d(TAG, it)
            }
        }
        return 0
    }
}
