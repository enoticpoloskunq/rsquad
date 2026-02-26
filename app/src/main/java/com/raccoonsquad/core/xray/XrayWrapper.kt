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
    
    // URL-decode spiderX (may be stored as %2F for /)
    private fun decodeSpiderX(spiderX: String): String {
        return try {
            java.net.URLDecoder.decode(spiderX, "UTF-8")
        } catch (e: Exception) {
            spiderX
        }
    }
    
    private var coreController: CoreController? = null
    private var isRunning = false
    private var currentConfig: String? = null
    private var appContext: Context? = null
    private var isInitialized = false
    
    /**
     * Initialize Xray wrapper with application context
     * Must be called before any Xray operations
     */
    fun init(context: Context) {
        if (isInitialized) {
            LogManager.d(TAG, "Already initialized, skipping")
            return
        }
        
        LogManager.i(TAG, "=== XrayWrapper.init() START ===")
        LogManager.flush()
        
        // Save context for later use
        appContext = context.applicationContext
        
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
            
            isInitialized = true
            
        } catch (e: Throwable) {
            LogManager.e(TAG, "=== XrayWrapper.init() FAILED ===", e)
            LogManager.flush()
            throw e
        }
    }
    
    /**
     * Ensure Xray is initialized before operations
     * Uses saved context from previous init() call
     */
    private fun ensureInitialized(): Boolean {
        if (isInitialized) return true
        
        val ctx = appContext
        if (ctx != null) {
            LogManager.w(TAG, "Xray not initialized, initializing with saved context...")
            try {
                init(ctx)
                return isInitialized
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to initialize Xray", e)
                return false
            }
        }
        
        LogManager.e(TAG, "Xray not initialized and no context saved! Call init(context) first.")
        return false
    }
    
    /**
     * Check if Xray is initialized
     */
    fun isInitialized(): Boolean = isInitialized
    
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
        
        // DNS settings - simple DNS through proxy
        // Avoid DoH complexity - just use plain DNS through proxy
        json.put("dns", JSONObject().apply {
            put("tag", "dns-out")
            put("servers", JSONArray().apply {
                // DNS through proxy - simple UDP DNS
                put("8.8.8.8")
                put("1.1.1.1")
            })
            put("queryStrategy", "UseIPv4")
        })
        
        // Inbounds - TUN (for VPN) + SOCKS5 and HTTP proxy
        val inbounds = JSONArray()
        
        // TUN inbound - uses tunFd from environment (set by libv2ray)
        // This is the key inbound for VPN traffic!
        inbounds.put(JSONObject().apply {
            put("tag", "tun")
            put("port", 0)  // Port 0 for TUN
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("name", "xray0")
                put("mtu", config.mtu.toIntOrNull() ?: 1500)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            })
        })
        
        // SOCKS5 proxy on localhost (for testing)
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
        
        // HTTP proxy on localhost (for testing)
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
                        if (config.realitySpiderX.isNotEmpty()) put("spiderX", decodeSpiderX(config.realitySpiderX))
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
        
        // Routing - Russian services DIRECT, others through proxy
        json.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // Private IPs -> direct (for local network access)
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
                
                // Russian IPs -> direct (banks, gov, etc)
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:ru"))
                    put("outboundTag", "direct")
                })
                
                // Russian domains -> direct
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply {
                        put("geosite:category-ru")
                        put("geosite:yandex")
                        put("geosite:vk")
                        put("geosite:mailru")
                        put("geosite:github")
                    })
                    put("outboundTag", "direct")
                })
                
                // Popular Russian services - direct (explicit list)
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().apply {
                        // Government
                        put("gosuslugi.ru")
                        put("www.gosuslugi.ru")
                        put("esia.gosuslugi.ru")
                        put("gu-st.ru")
                        put("nalog.gov.ru")
                        put("goszakupki.ru")
                        put("zakupki.gov.ru")
                        // Banks
                        put("sberbank.ru")
                        put("online.sberbank.ru")
                        put("tinkoff.ru")
                        put("tbank.ru")
                        put("vtb.ru")
                        put("alfabank.ru")
                        put("raiffeisen.ru")
                        put("gazprombank.ru")
                        put("rosbank.ru")
                        // Social
                        put("vk.com")
                        put("vkontakte.ru")
                        put("ok.ru")
                        put("odnoklassniki.ru")
                        put("telegram.org")
                        put("t.me")
                        // Services
                        put("yandex.ru")
                        put("ya.ru")
                        put("mail.ru")
                        put("rambler.ru")
                        put("kinopoisk.ru")
                        put("avito.ru")
                        put("hh.ru")
                        put("wildberries.ru")
                        put("ozon.ru")
                        put("aliexpress.ru")
                        put("dns-shop.ru")
                        put("citilink.ru")
                        put("mts.ru")
                        put("beeline.ru")
                        put("megafon.ru")
                        put("tele2.ru")
                        put("rostelecom.ru")
                        // Education
                        put("edu.ru")
                        put("russianpost.ru")
                        put("pochta.ru")
                    })
                    put("outboundTag", "direct")
                })
                
                // Block ads
                put(JSONObject().apply {
                    put("type", "field")
                    put("domain", JSONArray().put("geosite:category-ads-all"))
                    put("outboundTag", "block")
                })
                
                // DEFAULT: All other traffic -> proxy
                put(JSONObject().apply {
                    put("type", "field")
                    put("network", "tcp,udp")
                    put("outboundTag", "proxy")
                })
            })
        })
        
        // Log the generated config for debugging (full config)
        val configStr = json.toString(2)
        LogManager.d(TAG, "Generated Xray config:\n$configStr")
        
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

    /**
     * Measure delay for a config WITHOUT starting VPN
     * This is the REAL test - starts Xray temporarily and measures HTTP request time
     * Uses Libv2ray.measureOutboundDelay() internally
     * 
     * @param configJson Xray config JSON
     * @param url Test URL (default: https://www.google.com/generate_204)
     * @return Pair of (delay in milliseconds, error message or null)
     */
    fun measureDelayWithError(configJson: String, url: String = "https://www.google.com/generate_204"): Pair<Long, String?> {
        // Ensure Xray is initialized (sets xray.location.asset env var)
        if (!ensureInitialized()) {
            LogManager.e(TAG, "measureDelay() aborted: Xray not initialized")
            return Pair(-1L, "Xray not initialized")
        }
        
        return try {
            LogManager.d(TAG, "measureDelay() starting...")
            val startTime = System.currentTimeMillis()
            
            // Use libv2ray's built-in delay measurement
            // This starts a temporary Xray instance and measures the delay
            // Returns -1 on failure, throws on error
            val delay = Libv2ray.measureOutboundDelay(configJson, url)
            
            val elapsed = System.currentTimeMillis() - startTime
            LogManager.d(TAG, "measureDelay() result: ${delay}ms, total time: ${elapsed}ms")
            
            if (delay > 0) {
                Pair(delay, null)
            } else {
                // Delay is -1 or 0 = connection failed
                // Most likely causes: timeout, TLS handshake failure, or server unreachable
                val errorMsg = when {
                    elapsed > 25000 -> "TLS handshake timeout"  // Long wait = timeout
                    elapsed > 10000 -> "Connection timeout"     // Medium wait = connection issue
                    else -> "Connection refused"                  // Quick fail = server down
                }
                LogManager.w(TAG, "measureDelay() failed: $errorMsg (waited ${elapsed}ms)")
                Pair(-1L, errorMsg)
            }
            
        } catch (e: Throwable) {
            val errorMsg = e.message ?: "Unknown error"
            LogManager.e(TAG, "measureDelay() exception: $errorMsg")
            Pair(-1L, errorMsg)
        }
    }
    
    /**
     * Measure delay for a config WITHOUT starting VPN (legacy method)
     * This is the REAL test - starts Xray temporarily and measures HTTP request time
     * Uses Libv2ray.measureOutboundDelay() internally
     * 
     * @param configJson Xray config JSON
     * @param url Test URL (default: https://www.google.com/generate_204)
     * @return Delay in milliseconds, or -1 on error
     */
    fun measureDelay(configJson: String, url: String = "https://www.google.com/generate_204"): Long {
        val (delay, _) = measureDelayWithError(configJson, url)
        return delay
    }

    /**
     * Test a VlessConfig by measuring delay
     * Generates config JSON internally (uses simplified test config)
     */
    fun testConfig(config: VlessConfig, url: String = "https://www.google.com/generate_204"): Long {
        val configJson = generateTestConfig(config)
        return measureDelay(configJson, url)
    }
    
    /**
     * Test a VlessConfig by measuring delay, returns error message too
     * Generates config JSON internally (uses simplified test config)
     * @return Pair of (delay in ms, error message or null)
     */
    fun testConfigWithError(config: VlessConfig, url: String = "https://www.google.com/generate_204"): Pair<Long, String?> {
        val configJson = generateTestConfig(config)
        return measureDelayWithError(configJson, url)
    }
    
    /**
     * Generate simplified Xray config for node testing
     * NO geoip/geosite rules - just direct proxy connection
     * This avoids the geoip.dat/geosite.dat file requirement
     */
    fun generateTestConfig(config: VlessConfig): String {
        LogManager.d(TAG, "generateTestConfig() for ${config.name}")
        
        val json = JSONObject()
        
        // Minimal log settings
        json.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })
        
        // Simple DNS
        json.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put("8.8.8.8")
                put("1.1.1.1")
            })
            put("queryStrategy", "UseIPv4")
        })
        
        // Minimal inbounds - just SOCKS5 for testing
        val inbounds = JSONArray()
        inbounds.put(JSONObject().apply {
            put("tag", "socks-in")
            put("port", 10808)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("udp", false)
                put("auth", "noauth")
            })
        })
        json.put("inbounds", inbounds)
        
        // Outbounds
        val outbounds = JSONArray()
        
        // VLESS proxy outbound
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
                        if (config.realitySpiderX.isNotEmpty()) put("spiderX", decodeSpiderX(config.realitySpiderX))
                    })
                } else if (config.securityMode == SecurityMode.TLS) {
                    put("tlsSettings", JSONObject().apply {
                        put("allowInsecure", false)
                        if (config.sni.isNotEmpty()) put("serverName", config.sni)
                        put("fingerprint", config.fingerprint.ifEmpty { "chrome" })
                    })
                }
                
                // Socket options
                put("sockopt", JSONObject().apply {
                    if (config.tcpFastOpen) put("tcpFastOpen", true)
                    if (config.tcpNoDelay) put("tcpNoDelay", true)
                    if (config.tcpKeepAliveInterval > 0) put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
                })
            })
        })
        
        // Direct outbound for fallback
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        })
        
        json.put("outbounds", outbounds)
        
        // SIMPLIFIED ROUTING - no geoip/geosite!
        // Just route everything through proxy
        json.put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("rules", JSONArray().apply {
                // Everything goes through proxy
                put(JSONObject().apply {
                    put("type", "field")
                    put("network", "tcp")
                    put("outboundTag", "proxy")
                })
            })
        })
        
        val configStr = json.toString(2)
        LogManager.d(TAG, "Test config generated (${configStr.length} bytes)")
        
        return configStr
    }
}

/**
 * Core callback handler implementation for Xray core events
 */
private object CoreCallback : CoreCallbackHandler {
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
}
