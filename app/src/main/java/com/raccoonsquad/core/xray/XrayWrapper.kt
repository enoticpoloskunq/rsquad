package com.raccoonsquad.core.xray

import android.content.Context
import android.util.Log
import com.raccoonsquad.data.model.VlessConfig
import com.raccoonsquad.data.model.SecurityMode
import com.raccoonsquad.data.model.FlowMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wrapper for Xray-core via libv2ray.aar
 * 
 * Note: Requires libv2ray.aar from:
 * https://github.com/2dust/AndroidLibXrayLite/releases
 * 
 * Place libv2ray.aar in app/libs/ folder
 */
object XrayWrapper {
    
    private const val TAG = "XrayWrapper"
    
    // libv2ray functions (from AAR)
    // These will be called via JNI through the AAR
    
    private var isRunning = false
    private var currentConfig: String? = null
    
    /**
     * Generate Xray config JSON from VlessConfig
     */
    fun generateConfig(config: VlessConfig): String {
        val json = JSONObject()
        
        // Log
        val log = JSONObject().apply {
            put("loglevel", "warning")
        }
        json.put("log", log)
        
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
        val vlessOut = JSONObject().apply {
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
                        put("fingerprint", "chrome")
                        if (config.sni.isNotEmpty()) {
                            put("serverName", config.sni)
                        }
                        if (config.realityPublicKey.isNotEmpty()) {
                            put("publicKey", config.realityPublicKey)
                        }
                        if (config.realityShortId.isNotEmpty()) {
                            put("shortId", config.realityShortId)
                        }
                        if (config.realitySpiderX.isNotEmpty()) {
                            put("spiderX", config.realitySpiderX)
                        }
                    })
                } else if (config.securityMode == SecurityMode.TLS) {
                    put("tlsSettings", JSONObject().apply {
                        put("allowInsecure", false)
                        if (config.sni.isNotEmpty()) {
                            put("serverName", config.sni)
                        }
                        put("fingerprint", "chrome")
                    })
                }
                
                // Socket settings with fragmentation
                put("sockopt", JSONObject().apply {
                    if (config.tcpFastOpen) {
                        put("tcpFastOpen", true)
                    }
                    if (config.tcpNoDelay) {
                        put("tcpNoDelay", true)
                    }
                    if (config.tcpKeepAlive) {
                        put("tcpKeepAliveInterval", config.tcpKeepAliveInterval)
                    }
                    
                    // Dialer settings for fragmentation
                    if (config.fragmentationEnabled) {
                        put("dialer", JSONObject().apply {
                            put("domainStrategy", "AsIs")
                            // Fragment packets
                            // Note: This requires custom Xray build with fragment support
                        })
                    }
                })
            })
        }
        
        outbounds.put(vlessOut)
        
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
        val routing = JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                // Block private IPs from proxy
                put(JSONObject().apply {
                    put("type", "field")
                    put("ip", JSONArray().put("geoip:private"))
                    put("outboundTag", "direct")
                })
                // Default to proxy
                put(JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "proxy")
                })
            })
        }
        json.put("routing", routing)
        
        return json.toString(2)
    }
    
    /**
     * Start Xray with config
     * Called from VPN service
     */
    fun start(configJson: String): Boolean {
        if (isRunning) {
            stop()
        }
        
        try {
            currentConfig = configJson
            
            // TODO: Call libv2ray start function
            // This requires the AAR to be included
            // Example: Libv2ray.start(configJson)
            
            Log.d(TAG, "Xray config generated: ${configJson.take(200)}...")
            
            isRunning = true
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
            // TODO: Call libv2ray stop function
            // Example: Libv2ray.stop()
            
            isRunning = false
            currentConfig = null
            Log.d(TAG, "Xray stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop Xray", e)
        }
    }
    
    /**
     * Check if Xray is running
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Get test delay (for latency check)
     */
    fun testDelay(configJson: String): Long {
        // TODO: Implement via libv2ray
        return -1
    }
}
