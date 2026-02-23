package com.raccoonsquad.core.xray

import android.content.Context
import android.util.Log
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.SecurityMode
import com.raccoonsquad.data.model.FlowMode
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wrapper for Xray-core via libv2ray.aar
 * 
 * API based on: https://github.com/2dust/AndroidLibXrayLite
 */
object XrayWrapper {
    
    private const val TAG = "XrayWrapper"
    
    private var coreController: CoreController? = null
    private var isRunning = false
    private var currentConfig: String? = null
    
    /**
     * Initialize Xray wrapper with application context
     */
    fun init(context: Context) {
        val filesDir = context.filesDir.absolutePath
        val geoDir = File(filesDir, "xray")
        if (!geoDir.exists()) geoDir.mkdirs()
        
        // Initialize core environment (asset path, key)
        Libv2ray.initCoreEnv(geoDir.absolutePath, "")
        
        Log.i(TAG, "Xray wrapper initialized, version: ${Libv2ray.checkVersionX()}")
    }
    
    /**
     * Generate Xray config JSON from VlessConfig
     */
    fun generateConfig(config: VlessConfig): String {
        val json = JSONObject()
        
        // Log
        json.put("log", JSONObject().put("loglevel", "warning"))
        
        // Inbounds
        val inbounds = JSONArray()
        
        // SOCKS inbound
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
        
        // HTTP inbound
        inbounds.put(JSONObject().apply {
            put("tag", "http-in")
            put("port", 10809)
            put("listen", "127.0.0.1")
            put("protocol", "http")
        })
        
        json.put("inbounds", inbounds)
        
        // Outbounds
        val outbounds = JSONArray()
        
        // VLESS outbound
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
                
                // Socket settings
                put("sockopt", JSONObject().apply {
                    if (config.tcpFastOpen) put("tcpFastOpen", true)
                    if (config.tcpNoDelay) put("tcpNoDelay", true)
                    if (config.tcpKeepAliveInterval > 0) put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
                    
                    // Fragmentation
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
        
        // Routing
        json.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                })
            })
        })
        
        return json.toString(2)
    }
    
    /**
     * Start Xray with config
     */
    fun start(configJson: String): Boolean {
        if (isRunning) stop()
        
        try {
            currentConfig = configJson
            
            // Create callback handler
            val callback = object : CoreCallbackHandler() {
                override fun Startup(): Int {
                    Log.i(TAG, "Xray core started")
                    return 0
                }
                
                override fun Shutdown(): Int {
                    Log.i(TAG, "Xray core shutdown")
                    return 0
                }
                
                override fun OnEmitStatus(code: Int, message: String?): Int {
                    Log.d(TAG, "Xray status: $code - $message")
                    return 0
                }
            }
            
            // Create controller
            coreController = Libv2ray.newCoreController(callback)
            
            // Start with config (tunFd = 0 means use SOCKS proxy)
            val error = coreController?.startLoop(configJson, 0L)
            if (error != null) {
                Log.e(TAG, "Failed to start Xray: ${error.message}")
                return false
            }
            
            isRunning = true
            Log.i(TAG, "Xray started successfully")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Xray", e)
            return false
        }
    }
    
    /**
     * Stop Xray
     */
    fun stop() {
        if (!isRunning) return
        
        try {
            coreController?.stopLoop()
            coreController = null
            isRunning = false
            currentConfig = null
            Log.i(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Xray", e)
        }
    }
    
    /**
     * Check if Xray is running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Measure latency
     */
    fun measureDelay(url: String = "https://www.google.com/generate_204"): Long {
        if (!isRunning) return -1
        return try {
            coreController?.measureDelay(url) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    /**
     * Query traffic stats
     */
    fun queryStats(tag: String = "proxy", direction: String = "downlink"): Long {
        if (!isRunning) return 0
        return try {
            coreController?.queryStats(tag, direction) ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get version
     */
    fun getVersion(): String = Libv2ray.checkVersionX()
}
